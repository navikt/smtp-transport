package no.nav.emottak

import com.sksamuel.hoplite.ConfigLoader
import com.sksamuel.hoplite.ExperimentalHoplite
import com.sksamuel.hoplite.addResourceSource
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import no.nav.emottak.configuration.Config

class ConfiguratorSpec : StringSpec({

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

    "dev filter is loaded by default and contains expected typesToEbms" {
        config().ebmsFilter.typesToEbms shouldContain "Inntektsforesporsel"
    }

    "dev filter typesToBoth includes both ebXML service and Trekkopplysning" {
        val typesToBoth = config().ebmsFilter.typesToBoth
        typesToBoth shouldContain "urn:oasis:names:tc:ebxml-msg:service"
        typesToBoth shouldContain "Trekkopplysning"
    }

    "dev filter senderAddresses are populated" {
        val senderAddresses = config().ebmsFilter.senderAddresses
        senderAddresses.shouldNotBeEmpty()
        senderAddresses shouldContain "nyebmsbcc@test-es.nav.no"
    }

    "prod filter can be loaded directly and has expected values" {
        @OptIn(ExperimentalHoplite::class)
        val prodConfig = ConfigLoader.builder()
            .addResourceSource("/kafka_common.conf")
            .addResourceSource("/application.conf")
            .addResourceSource("/filter-prod.conf")
            .withExplicitSealedTypes()
            .build()
            .loadConfigOrThrow<Config>()

        val filter = prodConfig.ebmsFilter
        filter.typesToEbms shouldContain "Inntektsforesporsel"
        filter.typesToBoth shouldContain "urn:oasis:names:tc:ebxml-msg:service"
        filter.senderAddresses.shouldNotBeEmpty()
        filter.senderAddresses shouldContain "aidn.eidfjord@edi.nhn.no"
    }

    "prod filter senderAddresses differ from dev filter" {
        @OptIn(ExperimentalHoplite::class)
        val prodConfig = ConfigLoader.builder()
            .addResourceSource("/kafka_common.conf")
            .addResourceSource("/application.conf")
            .addResourceSource("/filter-prod.conf")
            .withExplicitSealedTypes()
            .build()
            .loadConfigOrThrow<Config>()

        val devSenders = config().ebmsFilter.senderAddresses
        val prodSenders = prodConfig.ebmsFilter.senderAddresses
        devSenders shouldContain "nyebmsbcc@test-es.nav.no"
        prodSenders shouldContain "aidn.eidfjord@edi.nhn.no"
        (devSenders intersect prodSenders).isEmpty() shouldBe true
    }
})
