package io.kaizensolutions

import cats.effect.Sync
import fs2.kafka.vulcan.SchemaRegistryClientSettings
import io.confluent.kafka.schemaregistry.SchemaProvider
import io.confluent.kafka.schemaregistry.avro.AvroSchemaProvider
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import io.confluent.kafka.schemaregistry.json.JsonSchemaProvider
import scala.jdk.CollectionConverters._

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
}
