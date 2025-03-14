package no.nav.emottak.plugin

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import no.nav.emottak.AuthConfig.Companion.getTokenSupportConfig
import no.nav.emottak.config
import no.nav.security.token.support.v3.tokenValidationSupport

fun Application.configureAuthentication() {
    val config = config()
    install(Authentication) {
        tokenValidationSupport(
            config.azureAuth.azureAdAuth.value,
            getTokenSupportConfig()
        )
    }
}
