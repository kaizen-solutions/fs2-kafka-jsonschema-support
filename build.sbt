import ReleaseTransformations._

organization := "io.kaizensolutions"

name := "fs2-kafka-jsonschema-support"

scalaVersion := "2.13.6"

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding",
  "UTF-8",
  "-feature",
  "-language:implicitConversions",
  "-unchecked",
  "-language:higherKinds",
  "-Xlint",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Ywarn-value-discard",
  "-Ywarn-unused",
  "-Xfatal-warnings",
  "-Vimplicits",
  "-Vtype-diffs",
  "-Xsource:3"
)

resolvers ++= Seq(
  "confluent" at "https://packages.confluent.io/maven/",
  "jitpack" at "https://jitpack.io"
)

libraryDependencies ++= {
  val circe     = "io.circe"
  val fd4s      = "com.github.fd4s"
  val fs2KafkaV = "2.1.0"
  Seq(
    fd4s                  %% "fs2-kafka"                    % fs2KafkaV,
    fd4s                  %% "fs2-kafka-vulcan"             % fs2KafkaV,
    "com.github.andyglow" %% "scala-jsonschema"             % "0.7.2",
    circe                 %% "circe-jackson212"             % "0.14.0",
    circe                 %% "circe-generic"                % "0.14.1",
    "org.typelevel"       %% "munit-cats-effect-3"          % "1.0.0"  % Test,
    "com.dimafeng"        %% "testcontainers-scala-munit"   % "0.39.5" % Test,
    "ch.qos.logback"       % "logback-classic"              % "1.2.3"  % Test,
    "io.confluent"         % "kafka-json-schema-serializer" % "6.2.0"
  )
}

releaseIgnoreUntrackedFiles := true

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  setNextVersion,
  commitNextVersion,
  pushChanges
)
