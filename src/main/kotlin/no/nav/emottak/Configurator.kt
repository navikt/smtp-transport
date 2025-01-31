package no.nav.emottak

import arrow.core.memoize
import com.sksamuel.hoplite.ConfigLoader
import com.sksamuel.hoplite.ExperimentalHoplite
import com.sksamuel.hoplite.addResourceSource
import no.nav.emottak.configuration.Config
import no.nav.emottak.hoplite.FixedEnvVarContextResolver
import no.nav.emottak.hoplite.FixedReferenceContextResolver

@OptIn(ExperimentalHoplite::class)
val config: () -> Config = {
    ConfigLoader.builder()
        // .addEnvironmentSource()
        .removePreprocessors()
        .addResolvers(FixedReferenceContextResolver, FixedEnvVarContextResolver)
        .addResourceSource("/application-personal.conf", optional = true)
        .addResourceSource("/application.conf")
        .withExplicitSealedTypes()
        .build()
        .loadConfigOrThrow<Config>()
}
    .memoize()
