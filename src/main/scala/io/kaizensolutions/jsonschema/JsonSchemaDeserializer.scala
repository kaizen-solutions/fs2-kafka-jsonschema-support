package io.kaizensolutions.jsonschema

import cats.effect.{Ref, Sync}
import cats.syntax.all._
import com.github.andyglow.jsonschema._
import com.fasterxml.jackson.databind.{DeserializationFeature, JsonNode, ObjectMapper}
import fs2.kafka.Deserializer
import io.circe.Decoder
import io.circe.jackson.jacksonToCirce
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.schemaregistry.json.JsonSchema
import io.confluent.kafka.schemaregistry.json.jackson.Jackson
import org.apache.kafka.common.errors.SerializationException

import java.io.{ByteArrayInputStream, IOException}
import java.nio.ByteBuffer
import scala.reflect.ClassTag

// See AbstractKafkaJsonSchemaDeserializer
object JsonSchemaDeserializer   {
  def apply[F[_]: Sync, A: Decoder](
    settings: JsonSchemaDeserializerSettings,
    client: SchemaRegistryClient
  )(implicit jsonSchema: json.Schema[A], tag: ClassTag[A]): F[Deserializer[F, A]] = {
    val fObjectMapper: F[ObjectMapper]   =
      Sync[F].delay {
        val instance = Jackson.newObjectMapper()
        if (settings.failOnUnknownKeys)
          instance.configure(
            DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
            settings.failOnUnknownKeys
          )
        instance
      }
    val fClientJsonSchema: F[JsonSchema] =
      Sync[F].delay {
        val schema =
          new JsonSchema(
            jsonSchema.draft07(
              settings.jsonSchemaId
                .getOrElse(tag.runtimeClass.getSimpleName.toLowerCase + "schema.json")
            )
          )
        schema.validate()
        schema
      }

    (fObjectMapper, fClientJsonSchema, Ref.of[F, Set[Int]](Set.empty[Int])).mapN {
      case (objectMapper, clientSchema, cache) =>
        new JsonSchemaDeserializer[F, A](settings, clientSchema, objectMapper, cache, client).jsonSchemaDeserializer
    }
  }
}
private[jsonschema] class JsonSchemaDeserializer[F[_]: Sync, A] private (
  settings: JsonSchemaDeserializerSettings,
  clientSchema: JsonSchema,
  objectMapper: ObjectMapper,
  compatSubjectIdCache: Ref[F, Set[Int]],
  client: SchemaRegistryClient
)(implicit decoder: Decoder[A]) {
  private val MagicByte: Byte = 0x0
  private val IdSize: Int     = 4

  def jsonSchemaDeserializer: Deserializer[F, A] =
    Deserializer.instance { (_, _, bytes) =>
      Sync[F].delay {
        val buffer             = getByteBuffer(bytes)
        val id                 = buffer.getInt()
        val serverSchema       = client.getSchemaById(id).asInstanceOf[JsonSchema]
        val bufferLength       = buffer.limit() - 1 - IdSize
        val start              = buffer.position() + buffer.arrayOffset()
        val jsonNode: JsonNode =
          objectMapper.readTree(new ByteArrayInputStream(buffer.array, start, bufferLength))

        if (settings.validatePayloadAgainstServerSchema) {
          serverSchema.validate(jsonNode)
        }

        if (settings.validatePayloadAgainstClientSchema) {
          clientSchema.validate(jsonNode)
        }

        (id, serverSchema, jsonNode)
      }.flatMap { case (serverId, serverSchema, jsonNode) =>
        val check =
          if (settings.validateClientSchemaAgainstServer)
            checkSchemaCompatibility(serverId, serverSchema)
          else Sync[F].unit

        check.as(jacksonToCirce(jsonNode))
      }
        .map(decoder.decodeJson)
        .rethrow
    }

  private def getByteBuffer(payload: Array[Byte]): ByteBuffer = {
    val buffer = ByteBuffer.wrap(payload)
    if (buffer.get() != MagicByte)
      throw new SerializationException("Unknown magic byte when deserializing from Kafka")
    buffer
  }

  private def checkSchemaCompatibility(serverSubjectId: Int, serverSchema: JsonSchema): F[Unit] = {
    val checkSchemaUpdateCache =
      Sync[F].delay {
        val incompatibilities = clientSchema.isBackwardCompatible(serverSchema)
        if (!incompatibilities.isEmpty)
          throw new IOException(
            s"Incompatible consumer schema with server schema: ${incompatibilities.toArray.mkString(", ")}"
          )
        else ()
      } *> compatSubjectIdCache.update(_ + serverSubjectId)

    for {
      existing <- compatSubjectIdCache.get
      _        <- if (existing.contains(serverSubjectId)) Sync[F].unit else checkSchemaUpdateCache
    } yield ()
  }
}
