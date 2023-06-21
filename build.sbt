inThisBuild {
  val scala212 = "2.12.18"
  val scala213 = "2.13.11"

  Seq(
    scalaVersion               := scala213,
    crossScalaVersions         := Seq(scala212, scala213),
    versionScheme              := Some("early-semver"),
    githubWorkflowJavaVersions := List(JavaSpec.temurin("11")),
    githubWorkflowTargetTags ++= Seq("v*"),
    githubWorkflowPublishTargetBranches := Seq(
      RefPredicate.StartsWith(Ref.Tag("v")),
      RefPredicate.Equals(Ref.Branch("main"))
    ),
    githubWorkflowPublish := Seq(
      WorkflowStep.Sbt(
        commands = List("ci-release"),
        name = Some("Publish project"),
        env = Map(
          "PGP_PASSPHRASE"    -> "${{ secrets.PGP_PASSPHRASE }}",
          "PGP_SECRET"        -> "${{ secrets.PGP_SECRET }}",
          "SONATYPE_PASSWORD" -> "${{ secrets.SONATYPE_PASSWORD }}",
          "SONATYPE_USERNAME" -> "${{ secrets.SONATYPE_USERNAME }}"
        )
      )
    ),
    developers := List(
      Developer("calvinlfer", "Calvin Fernandes", "cal@kaizen-solutions.io", url("https://www.kaizen-solutions.io")),
      Developer("anakos", "Alex Nakos", "anakos@users.noreply.github.com", url("https://github.com"))
    ),
    licenses               := List("MIT" -> url("https://opensource.org/licenses/MIT")),
    organization           := "io.kaizen-solutions",
    organizationName       := "kaizen-solutions",
    homepage               := Some(url("https://www.kaizen-solutions.io")),
    sonatypeCredentialHost := "s01.oss.sonatype.org",
    sonatypeProfileName    := "io.kaizen-solutions",
    sonatypeRepository     := "https://s01.oss.sonatype.org/service/local",
    sonatypeCredentialHost := "s01.oss.sonatype.org"
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

resolvers ++= Seq("confluent".at("https://packages.confluent.io/maven/"))

lazy val root =
  project
    .in(file("."))
    .settings(
      name := "fs2-kafka-jsonschema-support",
      libraryDependencies ++= {
        val circe     = "io.circe"
        val fd4s      = "com.github.fd4s"
        val fs2KafkaV = "3.0.1"

        Seq(
          fd4s                     %% "fs2-kafka"                    % fs2KafkaV,
          fd4s                     %% "fs2-kafka-vulcan"             % fs2KafkaV,
          "com.github.andyglow"    %% "scala-jsonschema"             % "0.7.9",
          circe                    %% "circe-jackson212"             % "0.14.0",
          circe                    %% "circe-generic"                % "0.14.5",
          "org.scala-lang.modules" %% "scala-collection-compat"      % "2.11.0",
          "org.typelevel"          %% "munit-cats-effect"            % "2.0.0-M3" % Test,
          "com.dimafeng"           %% "testcontainers-scala-munit"   % "0.40.17"  % Test,
          "ch.qos.logback"          % "logback-classic"              % "1.4.7"    % Test,
          "io.confluent"            % "kafka-json-schema-serializer" % "7.4.0"
        )
      }
    )
