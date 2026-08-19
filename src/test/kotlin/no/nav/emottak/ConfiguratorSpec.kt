package no.nav.emottak

import com.sksamuel.hoplite.ConfigLoader
import com.sksamuel.hoplite.addResourceSource
import io.kotest.core.spec.style.StringSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import no.nav.emottak.configuration.Config
import no.nav.emottak.configuration.ForwardingSystem
import no.nav.emottak.util.toServiceRules
import kotlin.time.Duration

class ConfiguratorSpec : StringSpec({

    val prodConfig = ConfigLoader.builder()
        .addResourceSource("/kafka_common.conf")
        .addResourceSource("/application.conf")
        .addResourceSource("/filter-prod.conf")
        .withExplicitSealedTypes()
        .build()
        .loadConfigOrThrow<Config>()

    "config() loads without throwing an exception" {
        config()
    }

    "config() returns the same instance on every call (memoized)" {
        config() shouldBeSameInstanceAs config()
    }

    "mail inbox defaults are loaded correctly" {
        val mail = config().mail
        mail.inboxLimit shouldBe 0
        mail.inboxBatchReadLimit shouldBe 10
        mail.inboxExpunge.shouldBeFalse()
    }

    "kafka topics are loaded with the correct names" {
        val topics = config().kafkaTopics
        topics.payloadInTopic shouldBe "team-emottak.smtp.in.ebxml.payload"
        topics.signalInTopic shouldBe "team-emottak.smtp.in.ebxml.signal"
        topics.payloadOutTopic shouldBe "team-emottak.smtp.out.ebxml.payload"
        topics.signalOutTopic shouldBe "team-emottak.smtp.out.ebxml.signal"
    }

    "kafka groupId is smtp-transport" {
        config().kafka.groupId shouldBe "smtp-transport"
    }

    "kafka SSL settings are loaded from kafka_common.conf" {
        val kafka = config().kafka
        kafka.securityProtocol.value shouldBe "SSL"
        kafka.keystoreType.value shouldBe "PKCS12"
        kafka.truststoreType.value shouldBe "JKS"
    }

    "smtp settings have correct defaults" {
        val smtp = config().smtp
        smtp.username.value shouldBe "test@test.test"
        smtp.smtpPort.value shouldBe 3025
        smtp.pop3Port.value shouldBe 3110
        smtp.smtpHost.value shouldBe "localhost"
        smtp.pop3Host.value shouldBe "localhost"
        smtp.storeProtocol.value shouldBe "pop3"
        smtp.pop3FactoryPort.value shouldBe 3110
        smtp.pop3FactoryFallback shouldBe false
        smtp.smtpFromAddress shouldBe "noreply@nav.no"
    }

    "database connection pool settings are loaded with correct defaults" {
        val db = config().database
        db.minimumIdleConnections.value shouldBe 1
        db.maxConnectionPoolSize.value shouldBe 4
        db.connectionTimeout.value shouldBe 1000
        db.idleConnectionTimeout.value shouldBe 10001
        db.maxLifetimeConnections.value shouldBe 30001
        db.migrationsPath.value shouldBe "filesystem:/app/migrations"
    }

    "cleanupPayloadsJob defaults are loaded correctly" {
        val cleanupPayloadsJob = config().cleanupPayloadsJob
        cleanupPayloadsJob.enabled shouldBe false
        cleanupPayloadsJob.fixedInterval shouldBe Duration.parse("24h")
        cleanupPayloadsJob.startAtTime.value shouldBe java.time.LocalTime.MIDNIGHT
        cleanupPayloadsJob.keepPayloadsDays.value shouldBe 90
        cleanupPayloadsJob.batchSize.value shouldBe 10000
    }

    "azureAuth defaults are loaded correctly" {
        val auth = config().azureAuth
        auth.port.value shouldBe 3344
        auth.azureAppClientId.value shouldBe "test-aud"
        auth.azureGrantType.value shouldBe "client_credentials"
    }

    "server port defaults to 8080" {
        config().server.port.value shouldBe 8080
    }

    "ebmsAsync defaults are loaded" {
        val ebmsAsync = config().ebmsAsync
        ebmsAsync.baseUrl shouldBe "ebms-async"
        ebmsAsync.apiUrl shouldBe "api/payloads/"
    }

    "dev filter is loaded by default and contains expected services" {
        val services = config().services.map { it.name }
        services shouldContain "Inntektsforesporsel"
        services shouldContain "Trekkopplysning"
    }

    "dev filter routes expected services to EBMS" {
        val toEbms = config().services
            .filter { it.forwardTo == ForwardingSystem.EBMS }
            .map { it.name }
        toEbms.size shouldBe 6
        toEbms shouldContain "Inntektsforesporsel"
        toEbms shouldContain "Trekkopplysning"
        toEbms shouldContain "Sykmelding"
        toEbms shouldContain "Legemelding"
        toEbms shouldContain "HarBorgerFrikortMengde"
        toEbms shouldContain "PasientlisteForesporsel"
    }

    "dev filter routes expected services to BOTH" {
        val toBoth = config().services
            .filter { it.forwardTo == ForwardingSystem.BOTH }
            .map { it.name }
        toBoth.size shouldBeGreaterThan 3
        toBoth shouldContain "urn:oasis:names:tc:ebxml-msg:service"
    }

    "dev filter routes expected services to EMOTTAK" {
        val toEmottak = config().services
            .filter { it.forwardTo == ForwardingSystem.EMOTTAK }
            .map { it.name }
        toEmottak shouldContain "BehandlerKrav"
        toEmottak shouldContain "OppgjorsKontroll"
    }

    "dev filter service names are unique" {
        val services = config().services.map { it.name }
        services.size shouldBe services.toSet().size
    }

    "dev filter accepts all CPA ids for every service" {
        val rules = config().services.toServiceRules()
        rules.keys shouldBe config().services.map { it.name }.toSet()
        rules.values.forAll { it.allCpaIds.shouldBeTrue() }
    }

    "prod filter can be loaded directly and has expected values" {
        val services = prodConfig.services
        services.map { it.name } shouldContain "Inntektsforesporsel"
        services.map { it.name } shouldContain "Trekkopplysning"
        services.single { it.name == "urn:oasis:names:tc:ebxml-msg:service" }
            .forwardTo shouldBe ForwardingSystem.BOTH
    }

    "prod filter CPA lists are resolved from file and differ from dev" {
        val prodRules = prodConfig.services.toServiceRules()
        prodRules.size shouldBe 4
        prodRules["Inntektsforesporsel"]!!.allCpaIds.shouldBeTrue()
        prodRules["Inntektsforesporsel"]!!.cpaIds.shouldBeEmpty()
        prodRules["Trekkopplysning"]!!.allCpaIds.shouldBeTrue()
        prodRules["Trekkopplysning"]!!.cpaIds.shouldBeEmpty()
        prodRules["Sykmelding"]!!.allCpaIds.shouldBeFalse()
        prodRules["Sykmelding"]!!.cpaIds shouldContain "nav:112931"
    }

    "prod filter routes expected services to EBMS" {
        val toEbms = prodConfig.services
            .filter { it.forwardTo == ForwardingSystem.EBMS }
            .map { it.name }
        toEbms.size shouldBe 3
        toEbms shouldContain "Inntektsforesporsel"
        toEbms shouldContain "Trekkopplysning"
        toEbms shouldContain "Sykmelding"
    }

    "prod filter routes expected services to BOTH" {
        val toBoth = prodConfig.services
            .filter { it.forwardTo == ForwardingSystem.BOTH }
            .map { it.name }
        toBoth.size shouldBe 1
        toBoth shouldContain "urn:oasis:names:tc:ebxml-msg:service"
    }
})
