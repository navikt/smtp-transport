package no.nav.emottak

import com.sksamuel.hoplite.ConfigLoader
import com.sksamuel.hoplite.addResourceSource
import io.kotest.core.spec.style.StringSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.ktor.util.toLowerCasePreservingASCIIRules
import no.nav.emottak.configuration.Config
import no.nav.emottak.util.SelectionType
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
            .filter { it.mode == "split" }
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
            .filter { it.mode == "BOTH" }
            .map { it.name }
        toBoth.size shouldBeGreaterThan 3
        toBoth shouldContain "urn:oasis:names:tc:ebxml-msg:service"
    }

    "dev filter routes expected services to EMOTTAK" {
        val toEmottak = config().services
            .filter { it.mode == "none" }
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
        rules.values.forAll { it.selectionType.shouldBe(SelectionType.ALL) }
    }

    "prod filter can be loaded directly and has expected values" {
        val services = prodConfig.services
        services.map { it.name } shouldContain "Inntektsforesporsel"
        services.map { it.name } shouldContain "Trekkopplysning"
        services.map { it.name } shouldContain "Sykmelding"
        services.map { it.name } shouldContain "Legemelding"
        services.single { it.name == "urn:oasis:names:tc:ebxml-msg:service" }
            .mode.toLowerCasePreservingASCIIRules() shouldBe "both"
    }

    "prod filter CPA lists are resolved from file and differ from dev" {
        val prodRules = prodConfig.services.toServiceRules()
        prodRules.size shouldBe 6
        prodRules["urn:oasis:names:tc:ebxml-msg:service"]!!.selectionType.shouldBe(SelectionType.ALL)
        prodRules["urn:oasis:names:tc:ebxml-msg:service"]!!.whitelist.shouldBeEmpty()
        prodRules["urn:oasis:names:tc:ebxml-msg:service"]!!.blacklist.shouldBeEmpty()
        prodRules["Inntektsforesporsel"]!!.selectionType.shouldBe(SelectionType.ALL)
        prodRules["Inntektsforesporsel"]!!.whitelist.shouldBeEmpty()
        prodRules["Inntektsforesporsel"]!!.blacklist.shouldBeEmpty()
        prodRules["Trekkopplysning"]!!.selectionType.shouldBe(SelectionType.ALL)
        prodRules["Trekkopplysning"]!!.whitelist.shouldBeEmpty()
        prodRules["Trekkopplysning"]!!.blacklist.shouldBeEmpty()
        prodRules["PasientlisteForesporsel"]!!.selectionType.shouldBe(SelectionType.ALL)
        prodRules["PasientlisteForesporsel"]!!.whitelist.shouldBeEmpty()
        prodRules["PasientlisteForesporsel"]!!.blacklist.shouldBeEmpty()
        prodRules["Sykmelding"]!!.selectionType.shouldBe(SelectionType.NONE)
        prodRules["Sykmelding"]!!.whitelist shouldContain "nav:112931"
        prodRules["Sykmelding"]!!.blacklist.shouldBeEmpty()
        prodRules["Legemelding"]!!.selectionType.shouldBe(SelectionType.NONE)
        prodRules["Legemelding"]!!.whitelist shouldContain "nav:112935"
        prodRules["Legemelding"]!!.blacklist.shouldBeEmpty()
    }

    "prod filter routes expected services to EBMS" {
        val toEbms = prodConfig.services
            .filter { it.mode == "split" }
            .map { it.name }
        toEbms.size shouldBe 5
        toEbms shouldContain "Inntektsforesporsel"
        toEbms shouldContain "Trekkopplysning"
        toEbms shouldContain "PasientlisteForesporsel"
        toEbms shouldContain "Sykmelding"
        toEbms shouldContain "Legemelding"
    }

    "prod filter routes expected services to BOTH" {
        val toBoth = prodConfig.services
            .filter { it.mode.toLowerCasePreservingASCIIRules() == "both" }
            .map { it.name }
        toBoth.size shouldBe 1
        toBoth shouldContain "urn:oasis:names:tc:ebxml-msg:service"
    }
})
