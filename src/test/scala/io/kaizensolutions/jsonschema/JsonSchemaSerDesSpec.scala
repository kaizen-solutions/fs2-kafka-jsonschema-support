package io.kaizensolutions.jsonschema

import cats.effect._
import cats.syntax.all._
import com.dimafeng.testcontainers.DockerComposeContainer.ComposeFile
import com.dimafeng.testcontainers.munit.TestContainersForAll
import com.dimafeng.testcontainers.{DockerComposeContainer, ExposedService}
import fs2.Stream
import fs2.kafka._
import fs2.kafka.vulcan.SchemaRegistryClientSettings
import io.circe.generic.semiauto._
import io.circe.{Codec, Decoder, Encoder}
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException
import json.schema.description
import json.{Json, Schema}
import munit.CatsEffectSuite

import java.io.{File, IOException}
import scala.annotation.nowarn
import scala.concurrent.duration.DurationInt
import scala.reflect.ClassTag

class JsonSchemaSerDesSpec extends CatsEffectSuite with TestContainersForAll {
  test(
    "JsonSchemaSerialization will automatically register the JSON Schema and allow you to send JSON data to Kafka"
  ) {
    val examplePersons = List.fill(100)(PersonV1("Bob", 40, List(Book("Bob the builder", 1337))))
    val serSettings    = JsonSchemaSerializerSettings.default.withAutomaticRegistration(true)
    producerTest[IO, PersonV1](
      schemaRegistry[IO],
      serSettings,
      "example-topic-persons",
      examplePersons,
      result => assertIO(result, examplePersons)
    )
  }

  test("Enabling use latest (and disabling auto-registration) without configuring the client will fail") {
    val examplePersons = List.fill(100)(PersonV1("Bob", 40, List(Book("Bob the builder", 1337))))
    val serSettings    = JsonSchemaSerializerSettings.default.withAutomaticRegistration(false).withUseLatestVersion(true)
    producerTest[IO, PersonV1](
      noJsonSupportSchemaRegistry[IO],
      serSettings,
      "example-topic-persons",
      examplePersons,
      result =>
        interceptMessageIO[RuntimeException](
          "Please enable JSON support in SchemaRegistryClientSettings by using withJsonSchemaSupport"
        )(result)
    )
  }

  test("Attempting to publish an incompatible change with auto-registration will fail") {
    val settings =
      JsonSchemaSerializerSettings.default
        .withAutomaticRegistration(true)
        .withSchemaId(PersonV1.getClass.getSimpleName.toLowerCase + ".schema.json")

    val topic = "example-topic-persons"

    val examplePersons =
      List.fill(100)(PersonV2Bad("Bob", 40, List(Book("Bob the builder - incompatible rename edition", 1337))))

    producerTest[IO, PersonV2Bad](
      schemaRegistry[IO],
      settings,
      topic,
      examplePersons,
      result =>
        interceptMessageIO[RestClientException](
          s"""Schema being registered is incompatible with an earlier schema for subject "$topic-value"; error code: 409"""
        )(result)
    )
  }

  test(
    "Attempting to publish an incompatible change without auto-registration (using latest server schema) will fail"
  ) {
    val settings =
      JsonSchemaSerializerSettings.default
        .withAutomaticRegistration(false)
        .withUseLatestVersion(true)
        .withSchemaId(PersonV1.getClass.getSimpleName.toLowerCase + ".schema.json")

    val topic = "example-topic-persons"

    val examplePersons =
      List.fill(100)(PersonV2Bad("Bob", 40, List(Book("Bob the builder - incompatible rename edition", 1337))))

    producerTest[IO, PersonV2Bad](
      schemaRegistry[IO],
      settings,
      topic,
      examplePersons,
      result =>
        interceptMessageIO[IOException](
          """Incompatible schema: Found incompatible change: Difference{jsonPath='#/properties/books', type=REQUIRED_PROPERTY_ADDED_TO_UNOPEN_CONTENT_MODEL}, Found incompatible change: Difference{jsonPath='#/properties/booksRead', type=PROPERTY_REMOVED_FROM_CLOSED_CONTENT_MODEL}"""
        )(result)
    )
  }

  test(
    "Attempting to publish an incompatible change without auto-registration and not using the latest schema will fail"
  ) {
    val settings =
      JsonSchemaSerializerSettings.default
        .withAutomaticRegistration(false)
        .withUseLatestVersion(false)
        .withSchemaId(PersonV1.getClass.getSimpleName.toLowerCase + ".schema.json")

    val topic = "example-topic-persons"

    val examplePersons =
      List.fill(100)(PersonV2Bad("Bob", 40, List(Book("Bob the builder - incompatible rename edition", 1337))))

    producerTest[IO, PersonV2Bad](
      schemaRegistry[IO],
      settings,
      topic,
      examplePersons,
      result =>
        interceptMessageIO[RestClientException](
          """Schema not found; error code: 40403"""
        )(result)
    )
  }

  test("Publishing a compatible change with auto-registration is allowed") {
    val settings =
      JsonSchemaSerializerSettings.default
        .withAutomaticRegistration(true)
        .withSchemaId(PersonV1.getClass.getSimpleName.toLowerCase + ".schema.json")

    val topic = "example-topic-persons"

    val examplePersons =
      List.fill(100)(
        PersonV2Good(
          "Bob",
          40,
          List(Book("Bob the builder - incompatible rename edition", 1337)),
          List("coding"),
          Some("more information")
        )
      )

    producerTest[IO, PersonV2Good](
      schemaRegistry[IO],
      settings,
      topic,
      examplePersons,
      result => assertIO(result, examplePersons)
    )
  }

  test(
    "Reading data back from the topic with the latest schema is allowed provided you compensate for missing fields in your Decoder"
  ) {
    val settings = JsonSchemaDeserializerSettings.default
      .withJsonSchemaId(PersonV1.getClass.getSimpleName.toLowerCase + ".schema.json")
      .withAggressiveValidation(true)

    val result: IO[(Boolean, Boolean)] =
      consumeFromKafka[IO, PersonV2Good](
        schemaRegistry[IO],
        settings,
        "example-consumer",
        "example-topic-persons",
        200
      ).compile.toList
        .map(list =>
          (
            list.take(100).forall(each => each.hobbies.isEmpty && each.optionalField.isEmpty),
            list.drop(100).forall(each => each.hobbies.nonEmpty && each.optionalField.nonEmpty)
          )
        )

    assertIO(result, (true, true))
  }

  test("Reading data back from the topic with an older schema is allowed") {
    val settings = JsonSchemaDeserializerSettings.default
      .withJsonSchemaId(PersonV1.getClass.getSimpleName.toLowerCase + ".schema.json")
      .withPayloadValidationAgainstServerSchema(true)

    val result: IO[Long] =
      consumeFromKafka[IO, PersonV1](
        schemaRegistry[IO],
        settings,
        "example-consumer-older",
        "example-topic-persons",
        200
      ).compile.foldChunks(0L)((acc, next) => acc + next.size)

    assertIO(result, 200L)
  }

  def producerTest[F[_]: Async, A: Encoder: json.Schema: ClassTag](
    fClient: F[SchemaRegistryClient],
    settings: JsonSchemaSerializerSettings,
    topic: String,
    input: List[A],
    assertion: F[List[A]] => F[Any]
  ): F[Any] = {
    val produceElements: F[List[A]] =
      Stream
        .eval[F, SchemaRegistryClient](fClient)
        .evalMap(JsonSchemaSerializer[F, A](settings, _))
        .evalMap(_.forValue)
        .flatMap(implicit serializer => kafkaProducer[F, Option[String], A])
        .flatMap { kafkaProducer =>
          Stream
            .emits[F, A](input)
            .chunks
            .evalMap { chunkA =>
              kafkaProducer.produce(
                ProducerRecords(
                  chunkA.map(ProducerRecord[Option[String], A](topic, None, _)),
                  chunkA
                )
              )
            }
            .groupWithin(1000, 1.second)
            .evalMap(_.sequence)
            .map(_.flatMap(_.passthrough))
            .flatMap(Stream.chunk)
        }
        .compile
        .toList

    assertion(produceElements)
  }

  def consumeFromKafka[F[_]: Async, A: Decoder: json.Schema: ClassTag](
    fClient: F[SchemaRegistryClient],
    settings: JsonSchemaDeserializerSettings,
    groupId: String,
    topic: String,
    numberOfElements: Long
  ): Stream[F, A] =
    Stream
      .eval(fClient)
      .evalMap(client => JsonSchemaDeserializer[F, A](settings, client))
      .flatMap(implicit des => kafkaConsumer[F, Option[String], A](groupId))
      .evalTap(_.subscribeTo(topic))
      .flatMap(_.stream)
      .map(_.record.value)
      .take(numberOfElements)

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
      .withAutoOffsetReset(AutoOffsetReset.Earliest)
    KafkaConsumer.stream(settings)
  }

  def schemaRegistry[F[_]: Sync]: F[SchemaRegistryClient] =
    SchemaRegistryClientSettings("http://localhost:8081").withJsonSchemaSupport.createSchemaRegistryClient

  def noJsonSupportSchemaRegistry[F[_]: Sync]: F[SchemaRegistryClient] =
    SchemaRegistryClientSettings("http://localhost:8081").createSchemaRegistryClient
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
  @nowarn implicit val personV2BadJsonSchema: Schema[PersonV2Bad] = Json.schema[PersonV2Bad]
  @nowarn implicit val personV2BadCodec: Codec[PersonV2Bad]       = deriveCodec[PersonV2Bad]
}
final case class PersonV2Bad(
  @description("name of the person") name: String,
  @description("age of the person") age: Int,
  @description("A list of books that the person has read") booksRead: List[Book]
)

object PersonV2Good {
  @nowarn implicit val personV2GoodJsonSchema: Schema[PersonV2Good] = Json.schema[PersonV2Good]
  @nowarn implicit val personV2GoodCodec: Codec[PersonV2Good] = {
    val encoder: Encoder[PersonV2Good] = deriveEncoder[PersonV2Good]

    val decoder: Decoder[PersonV2Good] = cursor =>
      for {
        name     <- cursor.downField("name").as[String]
        age      <- cursor.downField("age").as[Int]
        books    <- cursor.downField("books").as[List[Book]]
        hobbies  <- cursor.downField("hobbies").as[Option[List[String]]] // account for missing hobbies
        optField <- cursor.downField("optionalField").as[Option[String]]
      } yield PersonV2Good(name, age, books, hobbies.getOrElse(Nil), optField)

    Codec.from(decoder, encoder)
  }
}
final case class PersonV2Good(
  @description("name of the person") name: String,
  @description("age of the person") age: Int,
  @description("A list of books that the person has read") books: List[Book],
  @description("A list of hobbies") hobbies: List[String] = Nil,
  @description("An optional field to add extra information") optionalField: Option[String]
)