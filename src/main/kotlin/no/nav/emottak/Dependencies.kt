package no.nav.emottak

import arrow.fx.coroutines.ExitCase
import arrow.fx.coroutines.ResourceScope
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.micrometer.prometheus.PrometheusConfig.DEFAULT
import io.micrometer.prometheus.PrometheusMeterRegistry
import jakarta.mail.Authenticator
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Store
import no.nav.emottak.configuration.Smtp
import no.nav.emottak.configuration.toProperties

suspend fun ResourceScope.httpClient(): HttpClient =
    install({ HttpClient(CIO) }) { h, _: ExitCase -> h.close() }

suspend fun ResourceScope.metricsRegistry(): PrometheusMeterRegistry =
    install({ PrometheusMeterRegistry((DEFAULT)) }) { p, _: ExitCase -> p.close() }

suspend fun ResourceScope.store(smtp: Smtp): Store =
    install(
        {
            Session.getInstance(
                smtp.toProperties(),
                object : Authenticator() {
                    override fun getPasswordAuthentication() = PasswordAuthentication(
                        smtp.username.value,
                        smtp.password.value
                    )
                }
            )
                .getStore(smtp.storeProtocol.value).also { it.connect() }
        }
    ) { s, _: ExitCase -> s.close() }
