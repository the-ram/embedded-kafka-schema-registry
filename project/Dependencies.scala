import sbt._

object Dependencies {

  object CustomResolvers {
    lazy val Confluent = "confluent" at "https://packages.confluent.io/maven/"
    // Since v5.5.0
    lazy val Jitpack = "jitpack" at "https://jitpack.io"

    lazy val resolvers: Seq[Resolver] = Seq(Confluent, Jitpack)
  }

  object Versions {
    val Scala             = "2.13.1"
    val Scala212          = "2.12.11"
    val EmbeddedKafka     = "2.5.0"
    val ConfluentPlatform = "5.5.0"
    val Slf4j             = "1.7.30"
    val ScalaTest         = "3.1.2"
  }

  object Common {
    // Exclude any transitive Kafka dependency to prevent runtime errors.
    // They tend to evict Apache's since their version is greater
    lazy val confluentDeps: Seq[ModuleID] = Seq(
      "io.confluent" % "kafka-avro-serializer" % Versions.ConfluentPlatform,
      "io.confluent" % "kafka-schema-registry" % Versions.ConfluentPlatform
    ).map(_ excludeAll ExclusionRule().withOrganization("org.apache.kafka"))

    lazy val testDeps: Seq[ModuleID] = Seq(
      "org.slf4j"      % "slf4j-log4j12" % Versions.Slf4j,
      "org.scalatest" %% "scalatest"     % Versions.ScalaTest
    ).map(_ % Test)
  }

  object EmbeddedKafkaSchemaRegistry {
    lazy val prodDeps: Seq[ModuleID] = Seq(
      "io.github.embeddedkafka" %% "embedded-kafka" % Versions.EmbeddedKafka
    )

  }

  object KafkaStreams {
    lazy val prodDeps: Seq[ModuleID] = Seq(
      "io.github.embeddedkafka" %% "embedded-kafka-streams" % Versions.EmbeddedKafka
    )
  }

}
