package no.nav.emottak

import no.nav.security.token.support.v3.IssuerConfig
import no.nav.security.token.support.v3.TokenSupportConfig

class AuthConfig {
    companion object {
        private val config = config()

        fun getTokenSupportConfig(): TokenSupportConfig = TokenSupportConfig(
            IssuerConfig(
                name = config.azureAuth.azureAdAuth.value,
                discoveryUrl = config.azureAuth.azureWellKnownUrl.value,
                acceptedAudience = listOf(config.azureAuth.appScope.value)
            )
        )
    }
}
