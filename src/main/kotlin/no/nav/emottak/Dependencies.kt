package no.nav.emottak

import arrow.fx.coroutines.ExitCase
import arrow.fx.coroutines.ResourceScope
import io.micrometer.prometheus.PrometheusConfig.DEFAULT
import io.micrometer.prometheus.PrometheusMeterRegistry
import jakarta.mail.Authenticator
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Store
import no.nav.emottak.configuration.Smtp
import no.nav.emottak.configuration.toProperties

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
    ) { p, _: ExitCase -> p.close() }
