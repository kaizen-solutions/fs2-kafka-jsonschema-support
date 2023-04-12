import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._

inThisBuild {
  val scala212 = "2.12.16"
  val scala213 = "2.13.10"

  Seq(
    scalaVersion       := scala213,
    crossScalaVersions := Seq(scala212, scala213),
    releaseTagName     := s"${version.value}"
  )
}

ThisBuild / scalacOptions ++= {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, 12 | 13)) =>
      Seq(
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
        "-Xsource:3"
      )

    case Some((3, _)) =>
      Seq.empty

    case Some(_) | None =>
      Seq.empty
  }
}

def releaseSettings: Seq[Def.Setting[_]] =
  Seq(
    releaseIgnoreUntrackedFiles := true,
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
  )

resolvers ++= Seq(
  "confluent".at("https://packages.confluent.io/maven/"),
  "jitpack".at("https://jitpack.io")
)

lazy val root =
  project
    .in(file("."))
    .settings(releaseSettings: _*)
    .settings(
      organization := "io.kaizensolutions",
      name         := "fs2-kafka-jsonschema-support",
      libraryDependencies ++= {
        val circe     = "io.circe"
        val fd4s      = "com.github.fd4s"
        val fs2KafkaV = "3.0.0"

        Seq(
          fd4s                     %% "fs2-kafka"                    % fs2KafkaV,
          fd4s                     %% "fs2-kafka-vulcan"             % fs2KafkaV,
          "com.github.andyglow"    %% "scala-jsonschema"             % "0.7.9",
          circe                    %% "circe-jackson212"             % "0.14.0",
          circe                    %% "circe-generic"                % "0.14.5",
          "org.scala-lang.modules" %% "scala-collection-compat"      % "2.8.1",
          "org.typelevel"          %% "munit-cats-effect-3"          % "1.0.7"   % Test,
          "com.dimafeng"           %% "testcontainers-scala-munit"   % "0.40.11" % Test,
          "ch.qos.logback"          % "logback-classic"              % "1.4.4"   % Test,
          "io.confluent"            % "kafka-json-schema-serializer" % "7.3.3"
        )
      }
    )
