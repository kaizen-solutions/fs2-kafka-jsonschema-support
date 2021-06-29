package io.kaizensolutions.jsonschema

object JsonSchemaDeserializerSettings {
  val default: JsonSchemaDeserializerSettings = JsonSchemaDeserializerSettings()
}
final case class JsonSchemaDeserializerSettings(
  validatePayloadAgainstServerSchema: Boolean = false,
  validatePayloadAgainstClientSchema: Boolean = false,
  validateClientSchemaAgainstServer: Boolean = false,
  failOnUnknownKeys: Boolean = false,
  jsonSchemaId: Option[String] = None
)                                     { self =>
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
