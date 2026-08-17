package no.nav.emottak.util

import arrow.core.memoize
import no.nav.emottak.config
import no.nav.emottak.configuration.EbmsFilter
import no.nav.emottak.configuration.ForwardingSystem
import no.nav.emottak.configuration.ServiceFilter
import no.nav.emottak.log

private const val ALL_CPA_IDS = "all"
private const val COMMENT_PREFIX = "#"

data class ServiceRule(
    val forwardTo: ForwardingSystem,
    val allCpaIds: Boolean,
    val cpaIds: Set<String>
) {
    fun accepts(cpaId: String): Boolean = allCpaIds || cpaIds.contains(cpaId.lowercase())
}

val filterRules: () -> Map<String, ServiceRule> = {
    config().ebmsFilter.toServiceRules()
        .onEach { (name, rule) -> log.info(rule.describe(name)) }
}
    .memoize()

fun EbmsFilter.toServiceRules(): Map<String, ServiceRule> {
    val duplicates = services.groupingBy { it.name }.eachCount().filterValues { it > 1 }.keys
    check(duplicates.isEmpty()) {
        "Duplicate service names in ebmsFilter.services: ${duplicates.joinToString()}"
    }
    return services.associate { it.name to it.toServiceRule() }
}

private fun ServiceFilter.toServiceRule(): ServiceRule {
    check(forwardTo != ForwardingSystem.EMOTTAK) {
        "Service '$name' cannot declare forwardTo = EMOTTAK, it is the implicit fallback"
    }
    return if (cpaIdsFile.equals(ALL_CPA_IDS, ignoreCase = true)) {
        ServiceRule(forwardTo, allCpaIds = true, cpaIds = emptySet())
    } else {
        ServiceRule(forwardTo, allCpaIds = false, cpaIds = readCpaIds(cpaIdsFile, name))
    }
}

private fun readCpaIds(cpaIdsFile: String, serviceName: String): Set<String> {
    val resource = "/${cpaIdsFile.removePrefix("/")}"
    val stream = ServiceRule::class.java.getResourceAsStream(resource)
        ?: error("CPA id file '$resource' for service '$serviceName' not found on the classpath")
    return stream.bufferedReader()
        .use { reader -> reader.readLines() }
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith(COMMENT_PREFIX) }
        .map { it.lowercase() }
        .toSet()
}

private fun ServiceRule.describe(serviceName: String): String =
    "Filter rule: service '$serviceName' -> $forwardTo, cpaIds=${if (allCpaIds) ALL_CPA_IDS else cpaIds.size.toString()}"
