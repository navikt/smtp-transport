package no.nav.emottak

import io.github.nomisRev.kafka.Admin
import io.github.nomisRev.kafka.AdminSettings
import io.github.nomisRev.kafka.receiver.AutoOffsetReset
import io.github.nomisRev.kafka.receiver.ReceiverSettings
import io.kotest.core.extensions.install
import io.kotest.core.spec.style.StringSpec
import io.kotest.extensions.testcontainers.ContainerExtension
import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG
import org.apache.kafka.clients.admin.AdminClientConfig.CLIENT_ID_CONFIG
import org.apache.kafka.clients.admin.AdminClientConfig.CONNECTIONS_MAX_IDLE_MS_CONFIG
import org.apache.kafka.clients.admin.AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.utility.DockerImageName
import java.util.Properties
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

abstract class KafkaSpec(body: KafkaSpec.() -> Unit = {}) : StringSpec() {
    init {
        body()
    }

    private val transactionTimeoutInterval = 1.seconds
    private val consumerPollingTimeout = 1.seconds

    private val kafkaImage: DockerImageName =
        DockerImageName.parse("confluentinc/cp-kafka:7.4.0")

    internal val container: KafkaContainer =
        install(
            ContainerExtension(
                KafkaContainer(kafkaImage)
                    .withExposedPorts(9092, 9093)
                    .withNetworkAliases("broker")
                    .withEnv("KAFKA_HOST_NAME", "broker")
                    .withEnv("KAFKA_CONFLUENT_LICENSE_TOPIC_REPLICATION_FACTOR", "1")
                    .withEnv("KAFKA_CONFLUENT_BALANCER_TOPIC_REPLICATION_FACTOR", "1")
                    .withEnv(
                        "KAFKA_TRANSACTION_ABORT_TIMED_OUT_TRANSACTION_CLEANUP_INTERVAL_MS",
                        transactionTimeoutInterval.inWholeMilliseconds.toString()
                    )
                    .withEnv("KAFKA_AUTHORIZER_CLASS_NAME", "kafka.security.authorizer.AclAuthorizer")
                    .withEnv("KAFKA_ALLOW_EVERYONE_IF_NO_ACL_FOUND", "true")
                    .withReuse(true)
                    .also { container -> container.start() }
            )
        )

    private fun adminProperties(): Properties = Properties().apply {
        put(BOOTSTRAP_SERVERS_CONFIG, container.bootstrapServers)
        put(CLIENT_ID_CONFIG, "test-kafka-admin-client-${UUID.randomUUID()}")
        put(REQUEST_TIMEOUT_MS_CONFIG, "10000")
        put(CONNECTIONS_MAX_IDLE_MS_CONFIG, "10000")
    }

    private fun adminSettings(): AdminSettings = AdminSettings(container.bootstrapServers, adminProperties())

    private inline fun <A> admin(body: Admin.() -> A): A = Admin(adminSettings()).use(body)

    fun receiverSettings(): ReceiverSettings<String, ByteArray> =
        ReceiverSettings(
            bootstrapServers = container.bootstrapServers,
            StringDeserializer(),
            ByteArrayDeserializer(),
            groupId = "test-group-id",
            autoOffsetReset = AutoOffsetReset.Earliest,
            pollTimeout = consumerPollingTimeout
        )
}
