## FS2 Kafka JsonSchema support ##

![CI Build Status](https://github.com/kaizen-solutions/fs2-kafka-jsonschema-support/actions/workflows/scala.yml/badge.svg)

Provides FS2 Kafka `Serializer`s and `Deserializer`s that provide integration with Confluent Schema Registry for JSON messages with JSON Schemas. 
This library also provides an enrichment to fs2 kafka's vulcan `SchemaRegistryClientSettings` which is needed to enable additional JSON validation support 
inside the Schema Registry client. 

This functionality is backed by the following libraries:
- [scala-jsonschema](https://github.com/andyglow/scala-jsonschema) which is used to derive JSON Schemas for almost any Scala data-type
- [circe-jackson](https://github.com/circe/circe-jackson) which is used to derive JSON Encoders and Decoders for any Scala data-type and is further used to interop with Confluent's + Jackson's Schema validation mechanismss
- [fs2-kafka & fs2-kafka-vulcan](https://github.com/fd4s/fs2-kafka) which provides the serializers and deserializers interfaces that we implement along with the Schema Registry client that we enrich
- [confluent-schema-registry](https://github.com/confluentinc/schema-registry) is used as a basis for implementation and small portions are used for JSON Schema validation

### Notes ###
- Please note that this is only an initial design to prove the functionality and I'm very happy to integrate this back into FS2 Kafka so please submit an issue and we can take it from there
- This library provides additional validation checks for the Deserialization side on top of what Confluent provides in their Java JSON Schema Deserializer
