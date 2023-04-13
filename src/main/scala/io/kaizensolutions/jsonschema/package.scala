package io.kaizensolutions

import cats.effect.Sync
import fs2.kafka.vulcan.SchemaRegistryClientSettings
import io.confluent.kafka.schemaregistry.SchemaProvider
import io.confluent.kafka.schemaregistry.avro.AvroSchemaProvider
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import io.confluent.kafka.schemaregistry.json.{JsonSchema, JsonSchemaProvider}

import scala.jdk.CollectionConverters.*
import scala.reflect.ClassTag

package object jsonschema {
  implicit class ClientSchemaRegistrySyntax[F[_]: Sync](client: SchemaRegistryClientSettings[F]) {
    def withJsonSchemaSupport: SchemaRegistryClientSettings[F] =
      client.withCreateSchemaRegistryClient((baseUrl, maxCacheSize, properties) =>
        Sync[F].delay(
          new CachedSchemaRegistryClient(
            List(baseUrl).asJava,
            maxCacheSize,
            // Avro is present by default and we add JSON Schema support
            List[SchemaProvider](new AvroSchemaProvider(), new JsonSchemaProvider()).asJava,
            properties.asJava
          )
        )
      )
  }

  def toJsonSchema[F[_]: Sync, T](schema: json.Schema[T], schemaId: Option[String] = None)(implicit
    tag: ClassTag[T]
  ): F[JsonSchema] = {
    import com.github.andyglow.jsonschema.*

    Sync[F].delay {
      val instance = new JsonSchema(
        schema.draft07(
          schemaId.getOrElse(tag.runtimeClass.getSimpleName.toLowerCase + "schema.json")
        )
      )
      instance.validate()
      instance
    }
  }
}
