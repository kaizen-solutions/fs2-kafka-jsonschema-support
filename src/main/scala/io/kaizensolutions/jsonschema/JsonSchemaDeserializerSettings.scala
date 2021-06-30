package io.kaizensolutions.jsonschema

object JsonSchemaDeserializerSettings {
  val default: JsonSchemaDeserializerSettings = JsonSchemaDeserializerSettings()
}

/**
 * Settings that describe how to interact with Confluent's Schema Registry when deserializing data
 *
 * @param validatePayloadAgainstServerSchema will validate the payload against the schema on the server
 * @param validatePayloadAgainstClientSchema will validate the payload against the schema derived from the datatype you specify
 * @param validateClientSchemaAgainstServer  will validate the schema you specify against the server's schema
 * @param failOnUnknownKeys                  will specify failure when unknown JSON keys are encountered
 * @param jsonSchemaId                       is used to override the schema ID of the data that is being consumed
 */
final case class JsonSchemaDeserializerSettings(
  validatePayloadAgainstServerSchema: Boolean = false,
  validatePayloadAgainstClientSchema: Boolean = false,
  validateClientSchemaAgainstServer: Boolean = false,
  failOnUnknownKeys: Boolean = false,
  jsonSchemaId: Option[String] = None
) { self =>
  def withPayloadValidationAgainstServerSchema(b: Boolean): JsonSchemaDeserializerSettings =
    self.copy(validatePayloadAgainstServerSchema = b)

  def withPayloadValidationAgainstClientSchema(b: Boolean): JsonSchemaDeserializerSettings =
    self.copy(validatePayloadAgainstClientSchema = b)

  def withAggressiveSchemaValidation(b: Boolean): JsonSchemaDeserializerSettings =
    self.copy(validateClientSchemaAgainstServer = b)

  def withFailOnUnknownKeys(b: Boolean): JsonSchemaDeserializerSettings =
    self.copy(failOnUnknownKeys = b)

  def withAggressiveValidation(b: Boolean): JsonSchemaDeserializerSettings =
    self.copy(
      validatePayloadAgainstServerSchema = b,
      validatePayloadAgainstClientSchema = b,
      validateClientSchemaAgainstServer = b,
      failOnUnknownKeys = b
    )

  def withJsonSchemaId(id: String): JsonSchemaDeserializerSettings =
    self.copy(jsonSchemaId = Some(id))
}
