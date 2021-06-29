package io.kaizensolutions.jsonschema

object JsonSchemaSerializerSettings {
  val default: JsonSchemaSerializerSettings = JsonSchemaSerializerSettings()
}
final case class JsonSchemaSerializerSettings(
  automaticRegistration: Boolean = true,
  useLatestVersion: Boolean = false,
  validatePayload: Boolean = false,
  latestCompatStrict: Boolean = true,
  jsonSchemaId: Option[String] = None
)                                   { self =>
  def withSchemaId(id: String): JsonSchemaSerializerSettings =
    self.copy(jsonSchemaId = Some(id))

  def withUseLatestVersion(b: Boolean): JsonSchemaSerializerSettings =
    self.copy(useLatestVersion = b)

  def withAutomaticRegistration(b: Boolean): JsonSchemaSerializerSettings =
    self.copy(automaticRegistration = b)

  def withStrictLatestCompatibility(b: Boolean): JsonSchemaSerializerSettings =
    self.copy(latestCompatStrict = b)
}
