package net.manub.embeddedkafka.schemaregistry

import java.nio.file.{Files, Path}

import net.manub.embeddedkafka.ops.{EmbeddedKafkaOps, RunningEmbeddedKafkaOps}
import net.manub.embeddedkafka.schemaregistry.ops.{
  RunningSchemaRegistryOps,
  SchemaRegistryOps
}
import net.manub.embeddedkafka.{EmbeddedKafkaSupport, EmbeddedServer, EmbeddedZ}

trait EmbeddedKafka
    extends EmbeddedKafkaSupport[EmbeddedKafkaConfig]
    with EmbeddedKafkaOps[EmbeddedKafkaConfig, EmbeddedKWithSR]
    with SchemaRegistryOps {
  override private[embeddedkafka] def baseConsumerConfig(
      implicit config: EmbeddedKafkaConfig
  ): Map[String, Object] =
    defaultConsumerConfig ++ config.customConsumerProperties

  override private[embeddedkafka] def baseProducerConfig(
      implicit config: EmbeddedKafkaConfig
  ): Map[String, Object] =
    defaultProducerConf ++ config.customProducerProperties

  override private[embeddedkafka] def withRunningServers[T](
      config: EmbeddedKafkaConfig,
      actualZkPort: Int,
      kafkaLogsDir: Path
  )(body: EmbeddedKafkaConfig => T): T = {
    val broker =
      startKafka(
        config.kafkaPort,
        actualZkPort,
        config.customBrokerProperties,
        kafkaLogsDir
      )
    val actualKafkaPort = EmbeddedKafka.kafkaPort(broker)
    val restApp = startSchemaRegistry(
      config.schemaRegistryPort,
      actualKafkaPort,
      config.customSchemaRegistryProperties
    )

    val configWithUsedPorts = EmbeddedKafkaConfig(
      actualKafkaPort,
      actualZkPort,
      EmbeddedKafka.schemaRegistryPort(restApp),
      config.customBrokerProperties,
      config.customProducerProperties,
      config.customConsumerProperties,
      config.customSchemaRegistryProperties
    )

    try {
      body(configWithUsedPorts)
    } finally {
      restApp.stop()
      broker.shutdown()
      broker.awaitShutdown()
    }
  }
}

object EmbeddedKafka
    extends EmbeddedKafka
    with RunningEmbeddedKafkaOps[EmbeddedKafkaConfig, EmbeddedKWithSR]
    with RunningSchemaRegistryOps {
  override def start()(
      implicit config: EmbeddedKafkaConfig
  ): EmbeddedKWithSR = {
    val zkLogsDir    = Files.createTempDirectory("zookeeper-logs")
    val kafkaLogsDir = Files.createTempDirectory("kafka-logs")

    val factory =
      EmbeddedZ(startZooKeeper(config.zooKeeperPort, zkLogsDir), zkLogsDir)

    val configWithUsedZooKeeperPort = EmbeddedKafkaConfigImpl(
      config.kafkaPort,
      zookeeperPort(factory),
      config.schemaRegistryPort,
      config.customBrokerProperties,
      config.customProducerProperties,
      config.customConsumerProperties,
      config.customSchemaRegistryProperties
    )

    val kafkaBroker = startKafka(configWithUsedZooKeeperPort, kafkaLogsDir)

    val actualKafkaPort = EmbeddedKafka.kafkaPort(kafkaBroker)
    val restApp = EmbeddedSR(
      startSchemaRegistry(
        configWithUsedZooKeeperPort.schemaRegistryPort,
        actualKafkaPort,
        configWithUsedZooKeeperPort.customSchemaRegistryProperties
      )
    )

    val configWithUsedPorts = configWithUsedZooKeeperPort.copy(
      kafkaPort = actualKafkaPort,
      schemaRegistryPort = EmbeddedKafka.schemaRegistryPort(restApp.app)
    )

    val server = EmbeddedKWithSR(
      factory = Option(factory),
      broker = kafkaBroker,
      app = restApp,
      logsDirs = kafkaLogsDir,
      configWithUsedPorts
    )

    runningServers.add(server)
    server
  }

  override def isRunning: Boolean =
    runningServers.list
      .toFilteredSeq[EmbeddedKWithSR](s =>
        // Need to consider both independently-started Schema Registry and
        // all-in-one Kafka with SR
        isEmbeddedKWithSR(s) || isEmbeddedSR(s)
      )
      .nonEmpty

  private[embeddedkafka] def isEmbeddedKWithSR(
      server: EmbeddedServer
  ): Boolean =
    server.isInstanceOf[EmbeddedKWithSR]
}
