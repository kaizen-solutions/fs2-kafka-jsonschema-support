package io.kaizensolutions.jsonschema

import cats.effect._
import com.dimafeng.testcontainers.DockerComposeContainer.ComposeFile
import com.dimafeng.testcontainers.munit.TestContainersForAll
import com.dimafeng.testcontainers.{DockerComposeContainer, ExposedService}
import fs2.Stream
import fs2.kafka._
import fs2.kafka.vulcan.SchemaRegistryClientSettings
import io.circe.Codec
import io.circe.generic.semiauto._
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException
import json.schema.description
import json.{Json, Schema}
import munit.CatsEffectSuite

import java.io.{File, IOException}
import scala.annotation.nowarn
import scala.reflect.ClassTag

class JsonSchemaSerializationSpec extends CatsEffectSuite with TestContainersForAll {
  test(
    "JsonSchemaSerialization will automatically register the JSON Schema and allow you to send JSON data to Kafka"
  ) {
    val examplePerson = PersonV1("Bob", 40, List(Book("Bob the builder", 1337)))
    val serSettings   = JsonSchemaSerializerSettings.default.withAutomaticRegistration(true)

    val produceMessage =
      Stream
        .eval(schemaRegistry[IO])
        .evalMap(c => JsonSchemaSerializer[IO, PersonV1](serSettings, c))
        .evalMap(_.forValue)
        .flatMap(implicit s => kafkaProducer[IO, String, PersonV1])
        .evalMap(
          _.produce(
            ProducerRecords
              .one(ProducerRecord("example-topic", "a", examplePerson), examplePerson)
          ).flatten
        )
        .map(_.passthrough)
        .compile
        .lastOrError

    assertIO(produceMessage, examplePerson)
  }

  test("Attempting to publish an incompatible change with auto-registration will fail") {
    incompatibleChange[RestClientException](
      JsonSchemaSerializerSettings.default
        .withAutomaticRegistration(true)
        .withSchemaId(PersonV1.getClass.getSimpleName.toLowerCase + ".schema.json"),
      """Schema being registered is incompatible with an earlier schema for subject "example-topic-value"; error code: 409"""
    )
  }

  test(
    "Attempting to publish an incompatible change without auto-registration (using latest server schema) will fail"
  ) {
    incompatibleChange[IOException](
      JsonSchemaSerializerSettings.default
        .withAutomaticRegistration(false)
        .withUseLatestVersion(true)
        .withSchemaId(PersonV1.getClass.getSimpleName.toLowerCase + ".schema.json"),
      """Incompatible schema: Found incompatible change: Difference{jsonPath='#/properties/books', type=REQUIRED_PROPERTY_ADDED_TO_UNOPEN_CONTENT_MODEL}, Found incompatible change: Difference{jsonPath='#/properties/booksRead', type=PROPERTY_REMOVED_FROM_CLOSED_CONTENT_MODEL}"""
    )
  }

  test(
    "Attempting to publish an incompatible change without auto-registration and not using the latest schema will fail"
  ) {
    incompatibleChange[RestClientException](
      JsonSchemaSerializerSettings.default
        .withAutomaticRegistration(false)
        .withUseLatestVersion(false)
        .withSchemaId(PersonV1.getClass.getSimpleName.toLowerCase + ".schema.json"),
      """Schema not found; error code: 40403"""
    )
  }

  def incompatibleChange[FailureType <: Throwable: ClassTag](
    serSettings: JsonSchemaSerializerSettings,
    expectedMessage: String
  ): IO[FailureType] = {
    val incompatiblePerson = PersonV2Bad("Bob", 40, List(Book("Bob the builder sneaky edition", 1337)))

    val produceMessage =
      Stream
        .eval(schemaRegistry[IO])
        .evalMap(c => JsonSchemaSerializer[IO, PersonV2Bad](serSettings, c))
        .evalMap(_.forValue)
        .flatMap(implicit s => kafkaProducer[IO, String, PersonV2Bad])
        .evalMap(
          _.produce(
            ProducerRecords
              .one(ProducerRecord("example-topic", "a", incompatiblePerson), incompatiblePerson)
          ).flatten
        )
        .map(_.passthrough)
        .compile
        .lastOrError

    interceptMessageIO[FailureType](expectedMessage)(produceMessage)
  }

  override type Containers = DockerComposeContainer

  override def startContainers(): Containers =
    DockerComposeContainer
      .Def(
        composeFiles = ComposeFile(Left(new File("./docker-compose.yaml"))),
        exposedServices = List(
          ExposedService(name = "kafka-schema-registry", 8081),
          ExposedService(name = "kafka1", 9092)
        )
      )
      .start()

  def kafkaProducer[F[_]: Async, K, V](implicit
    keySerializer: Serializer[F, K],
    valueSerializer: Serializer[F, V]
  ): Stream[F, KafkaProducer[F, K, V]] = {
    val settings: ProducerSettings[F, K, V] =
      ProducerSettings[F, K, V].withBootstrapServers("localhost:9092")
    KafkaProducer.stream(settings)
  }

  def kafkaConsumer[F[_]: Async, K, V](groupId: String)(implicit
    keyDeserializer: Deserializer[F, K],
    valueDeserializer: Deserializer[F, V]
  ): Stream[F, KafkaConsumer[F, K, V]] = {
    val settings = ConsumerSettings[F, K, V]
      .withBootstrapServers("localhost:9092")
      .withGroupId(groupId)
    KafkaConsumer.stream(settings)
  }

  def schemaRegistry[F[_]: Sync]: F[SchemaRegistryClient] =
    SchemaRegistryClientSettings("http://localhost:8081").withJsonSchemaSupport.createSchemaRegistryClient
}

object Book {
  @nowarn implicit val bookJsonSchema: Schema[Book] = Json.schema[Book]
  @nowarn implicit val bookCodec: Codec[Book]       = deriveCodec[Book]
}
final case class Book(
  @description("name of the book") name: String,
  @description("international standard book number") isbn: Int
)

object PersonV1 {
  @nowarn implicit val personJsonSchema: Schema[PersonV1] = Json.schema[PersonV1]
  @nowarn implicit val personCodec: Codec[PersonV1]       = deriveCodec[PersonV1]
}
final case class PersonV1(
  @description("name of the person") name: String,
  @description("age of the person") age: Int,
  @description("A list of books that the person has read") books: List[Book]
)

// V2 is backwards incompatible with V1 because the key has changed
object PersonV2Bad {
  @nowarn implicit val personJsonSchema: Schema[PersonV2Bad] = Json.schema[PersonV2Bad]
  @nowarn implicit val personCodec: Codec[PersonV2Bad]       = deriveCodec[PersonV2Bad]
}
final case class PersonV2Bad(
  @description("name of the person") name: String,
  @description("age of the person") age: Int,
  @description("A list of books that the person has read") booksRead: List[Book]
)
