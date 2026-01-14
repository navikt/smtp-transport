package no.nav.emottak

import arrow.core.memoize
import com.sksamuel.hoplite.ConfigLoader
import com.sksamuel.hoplite.ExperimentalHoplite
import com.sksamuel.hoplite.addResourceSource
import no.nav.emottak.configuration.Config
import no.nav.emottak.utils.environment.getEnvVar

@OptIn(ExperimentalHoplite::class)
val config: () -> Config = {
    ConfigLoader.builder()
        .addResourceSource("/application-personal.conf", optional = true)
        .addResourceSource("/kafka_common.conf")
        .addResourceSource("/application.conf")
        .addResourceSource(filterConfigResolver())
        .withExplicitSealedTypes()
        .build()
        .loadConfigOrThrow<Config>()
}
    .memoize()

private fun filterConfigResolver() = when (getEnvVar("NAIS_CLUSTER_NAME", "local")) {
    "prod-fss" -> "/filter-prod.conf"
    "dev-fss" -> "/filter-dev.conf"
    else -> "/filter-dev.conf"
}.also {
    log.info("Loading filter configuration from $it")
}
