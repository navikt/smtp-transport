package no.nav.emottak.util

import arrow.core.memoize
import no.nav.emottak.config
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

enum class FilterMatch { CONFIGURED, CPA_ID_NOT_IN_LIST, UNKNOWN_SERVICE }

data class ForwardingDecision(
    val forwardTo: ForwardingSystem,
    val filterMatch: FilterMatch
)

fun Map<String, ServiceRule>.resolveForwarding(service: String, cpaId: String): ForwardingDecision {
    val rule = this[service]
        ?: return ForwardingDecision(ForwardingSystem.EMOTTAK, FilterMatch.UNKNOWN_SERVICE)
    return if (rule.accepts(cpaId)) {
        ForwardingDecision(rule.forwardTo, FilterMatch.CONFIGURED)
    } else {
        ForwardingDecision(ForwardingSystem.EMOTTAK, FilterMatch.CPA_ID_NOT_IN_LIST)
    }
}

val filterRules: () -> Map<String, ServiceRule> = {
    config().services.toServiceRules()
        .onEach { (name, rule) -> log.info(rule.describe(name)) }
}
    .memoize()

fun List<ServiceFilter>.toServiceRules(): Map<String, ServiceRule> {
    val duplicates = groupingBy { it.name }.eachCount().filterValues { it > 1 }.keys
    check(duplicates.isEmpty()) {
        "Duplicate service names in filter: ${duplicates.joinToString()}"
    }
    return associate { it.name to it.toServiceRule() }
}

private fun ServiceFilter.toServiceRule(): ServiceRule {
    val allCpaIds = cpaIdsFile.equals(ALL_CPA_IDS, ignoreCase = true)
    check(forwardTo != ForwardingSystem.EMOTTAK || allCpaIds) {
        "Service '$name' declares forwardTo = EMOTTAK and must use cpaIdsFile = \"$ALL_CPA_IDS\", " +
            "a CPA id list has no effect when EMOTTAK is also the fallback"
    }
    return if (allCpaIds) {
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
