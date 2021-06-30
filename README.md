## FS2 Kafka JsonSchema support ##

![CI Build Status](https://github.com/kaizen-solutions/fs2-kafka-jsonschema-support/actions/workflows/scala.yml/badge.svg)
[![](https://jitpack.io/v/kaizen-solutions/fs2-kafka-jsonschema-support.svg)](https://jitpack.io/#kaizen-solutions/fs2-kafka-jsonschema-support)


Provides FS2 Kafka `Serializer`s and `Deserializer`s that provide integration with Confluent Schema Registry for JSON messages with JSON Schemas. 
This library also provides an enrichment to fs2 kafka's vulcan `SchemaRegistryClientSettings` which is needed to enable additional JSON validation support 
inside the Schema Registry client. 

This functionality is backed by the following libraries:
- [scala-jsonschema](https://github.com/andyglow/scala-jsonschema) which is used to derive JSON Schemas for almost any Scala data-type
- [circe-jackson](https://github.com/circe/circe-jackson) which is used to derive JSON Encoders and Decoders for any Scala data-type and is further used to interop with Confluent's + Jackson's Schema validation mechanismss
- [fs2-kafka & fs2-kafka-vulcan](https://github.com/fd4s/fs2-kafka) which provides the serializers and deserializers interfaces that we implement along with the Schema Registry client that we enrich
- [confluent-schema-registry](https://github.com/confluentinc/schema-registry) is used as a basis for implementation and small portions are used for JSON Schema validation

### Usage ###

Add the following to your `build.sbt`
```sbt
resolvers += "jitpack" at "https://jitpack.io"

libraryDependencies += "com.github.kaizen-solutions" %% "fs2-kafka-jsonschema-support" % "<version>"
```

1. Define your data-types
```scala
object Book {}
final case class Book(
  name: String,
  isbn: Int
)

object Person {}
final case class PersonV1(
  name: String,
  age: Int,
  books: List[Book]
)
```

2. Derive JSON Schemas for your case classes and add extra JSON Schema information using `scala-jsonschema`
```scala
import json.schema.description
import json.{Json, Schema}

object Book {
  implicit val bookJsonSchema: Schema[Book] = Json.schema[Book]
}
final case class Book(
  @description("name of the book") name: String,
  @description("international standard book number") isbn: Int
)

object Person {
  implicit val personJsonSchema: Schema[Person] = Json.schema[Person]
}
final case class Person(
  @description("name of the person") name: String,
  @description("age of the person") age: Int,
  @description("A list of books that the person has read") books: List[Book]
)
```

3. Use `circe` to derive Encoders & Decoders (or Codecs) for your data-types:
```scala
import io.circe.generic.semiauto._
import io.circe.Codec
import json.schema.description
import json.{Json, Schema}

object Book {
  implicit val bookJsonSchema: Schema[Book] = Json.schema[Book]
  implicit val bookCodec: Codec[Book]       = deriveCodec[Book]
}
final case class Book(
  @description("name of the book") name: String,
  @description("international standard book number") isbn: Int
)

object Person {
  implicit val personJsonSchema: Schema[Person] = Json.schema[Person]
  implicit val personCodec: Codec[Person]       = deriveCodec[Person]
}
final case class Person(
  @description("name of the person") name: String,
  @description("age of the person") age: Int,
  @description("A list of books that the person has read") books: List[Book]
)
```

4. Instantiate and configure the Schema Registry
```scala
import cats.effect._
import io.kaizensolutions.jsonschema._

def schemaRegistry[F[_]: Sync]: F[SchemaRegistryClient] =
  SchemaRegistryClientSettings("http://localhost:8081")
    .withJsonSchemaSupport
    .createSchemaRegistryClient
```

5. Configure your FS2 Kafka Producers and Consumers to pull Serializers (or do this process manually)
```scala
import cats.effect._
import fs2.Stream
import fs2.kafka._

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
```
**Note:** In some cases you will need to adjust the Decoder to account for missing data

6. Produce data to Kafka with automatic Confluent Schema Registry support:
```scala
import cats.effect._
import fs2._
import fs2.kafka._
import json._
import io.circe._
import io.kaizensolutions.jsonschema._
import scala.reflect.ClassTag

def jsonSchemaProducer[F[_]: Async, A: Encoder: json.Schema: ClassTag](
  settings: JsonSchemaSerializerSettings
): Stream[F, KafkaProducer[F, String, A]] =  
  Stream
    .eval[F, SchemaRegistryClient](schemaRegistry[F])
    .evalMap(schemaRegistryClient => JsonSchemaSerializer[F, A](settings, schemaRegistryClient))
    .evalMap(_.forValue)
    .flatMap(implicit serializer => kafkaProducer[F, String, A])


def jsonSchemaConsumer[F[_]: Async, A: Decoder: json.Schema: ClassTag](
  settings: JsonSchemaDeserializerSettings, 
  groupId: String
): Stream[F, KafkaConsumer[F, String, A]] =
  Stream
    .eval(schemaRegistry[F])
    .evalMap(client => JsonSchemaDeserializer[F, A](settings, client))
    .flatMap(implicit des => kafkaConsumer[F, String, A](groupId))
```

### Settings ###
There are a number of settings that control a number of behaviors when it comes to serialization and deserialization of data.
Please check `JsonSchemaDeserializerSettings` and `JsonSchemaSerializerSettings` for more information. The `default` settings
work great unless you need fine-grained control

### Notes ###
- Please note that this is only an initial design to prove the functionality, and I'm very happy to integrate this back into FS2 Kafka (and other Kafka libraries) so please submit an issue and we can take it from there
- This library provides additional validation checks for the Deserialization side on top of what Confluent provides in their Java JSON Schema Deserializer
