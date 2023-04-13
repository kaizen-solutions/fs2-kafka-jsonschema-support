package io.kaizensolutions.jsonschema

object JsonSchemaSerializerSettings {
  val default: JsonSchemaSerializerSettings = JsonSchemaSerializerSettings()
}

/**
 * Settings that describe how to interact with Confluent's Schema Registry when
 * serializing data
 *
 * @param automaticRegistration
 *   dictates whether we try to automatically register the schema we have with
 *   the server
 * @param useLatestVersion
 *   dictates whether to use the latest schema on the server instead of
 *   registering a new one
 * @param validatePayload
 *   dictates whether to validate the JSON payload against the schema
 * @param latestCompatStrict
 *   dictates whether to use strict compatibility
 * @param jsonSchemaId
 *   is used to override the schema ID of the data that is being produced
 */
final case class JsonSchemaSerializerSettings(
  automaticRegistration: Boolean = true,
  useLatestVersion: Boolean = false,
  validatePayload: Boolean = false,
  latestCompatStrict: Boolean = true,
  jsonSchemaId: Option[String] = None
) { self =>
  def withSchemaId(id: String): JsonSchemaSerializerSettings =
    self.copy(jsonSchemaId = Some(id))

  def withUseLatestVersion(b: Boolean): JsonSchemaSerializerSettings =
    self.copy(useLatestVersion = b)

  def withAutomaticRegistration(b: Boolean): JsonSchemaSerializerSettings =
    self.copy(automaticRegistration = b)

  def withStrictLatestCompatibility(b: Boolean): JsonSchemaSerializerSettings =
    self.copy(latestCompatStrict = b)
}
