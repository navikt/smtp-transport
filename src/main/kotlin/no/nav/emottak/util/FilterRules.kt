package no.nav.emottak.util

import arrow.core.memoize
import io.ktor.util.toLowerCasePreservingASCIIRules
import no.nav.emottak.config
import no.nav.emottak.configuration.ForwardingSystem
import no.nav.emottak.configuration.ServiceFilter
import no.nav.emottak.log

private const val MODE_BOTH = "both"
private const val MODE_NEW_EMOTTAK = "split"

private const val ALL_CPA_IDS = "all"
private const val ONLY_OLD_EMOTTAK = "none"
private const val PERCENT_PREFIX = "percentage"
private const val LAST_DIGIT_PREFIX = "lastdigit"

private const val COMMENT_PREFIX = "#"

enum class FilterType {
    ALL_TO_OLD,
    SOME_TO_BOTH,
    SOME_TO_NEW
}
enum class SelectionType {
    ALL,
    PERCENTAGE,
    CPAID_FORMAT,
    NONE
}

enum class FilterMatch { BLACK_LISTED, WHITE_LISTED, ALL, BY_PERCENTAGE, BY_CPAID_MATCH, UNKNOWN_SERVICE }

data class ServiceRule(
    val filterType: FilterType,
    val selectionType: SelectionType,
    val percentage: Int = 0,
    val lastDigits: Set<Int>,
    val whitelist: Set<String>,
    val blacklist: Set<String>
) {
    fun accepts(cpaId: String): Pair<Boolean, FilterMatch> {
        if (filterType == FilterType.ALL_TO_OLD) return Pair(false, FilterMatch.ALL)

        // Blacklist og whitelist overstyrer alle andre settinger
        if (blacklist.contains(cpaId)) return Pair(false, FilterMatch.BLACK_LISTED)
        if (whitelist.contains(cpaId)) return Pair(true, FilterMatch.WHITE_LISTED)

        // Hvis ingen av listene inneholder CPAen, brukes andelssettingen
        if (selectionType == SelectionType.ALL) return Pair(true, FilterMatch.ALL)
        if (selectionType == SelectionType.NONE) return Pair(false, FilterMatch.ALL)
        if (selectionType == SelectionType.PERCENTAGE) return Pair(acceptedThroughPercentage(percentage), FilterMatch.BY_PERCENTAGE)
        if (selectionType == SelectionType.CPAID_FORMAT) return Pair(acceptedThroughLastDigit(lastDigits, cpaId), FilterMatch.BY_CPAID_MATCH)
        throw IllegalStateException("Unknown selection type: $selectionType")
    }

    private fun acceptedThroughLastDigit(lastDigits: Set<Int>, cpaId: String): Boolean {
        val lastChar = cpaId.substring(cpaId.length - 1)
        if (lastChar.toIntOrNull() == null) return false
        val lastDigit = lastChar.toInt()
        return lastDigits.contains(lastDigit)
    }

    private fun acceptedThroughPercentage(percentage: Int): Boolean {
        return (Math.random() * 100) < percentage
    }
}

data class ForwardingDecision(
    val forwardTo: ForwardingSystem,
    val filterMatch: FilterMatch
)

fun Map<String, ServiceRule>.resolveForwarding(service: String, cpaId: String): ForwardingDecision {
    val rule = this[service]
        ?: return ForwardingDecision(ForwardingSystem.EMOTTAK, FilterMatch.UNKNOWN_SERVICE)
    val decision = rule.accepts(cpaId)
    val forwardTo = when (rule.filterType) {
        FilterType.ALL_TO_OLD -> ForwardingSystem.EMOTTAK
        FilterType.SOME_TO_BOTH -> if (decision.first) ForwardingSystem.BOTH else ForwardingSystem.EMOTTAK
        FilterType.SOME_TO_NEW -> if (decision.first) ForwardingSystem.EBMS else ForwardingSystem.EMOTTAK
    }
    return ForwardingDecision(forwardTo, decision.second)
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
    val filterType = when (mode.toLowerCasePreservingASCIIRules()) {
        ONLY_OLD_EMOTTAK -> FilterType.ALL_TO_OLD
        MODE_BOTH -> FilterType.SOME_TO_BOTH
        MODE_NEW_EMOTTAK -> FilterType.SOME_TO_NEW
        else -> { throw IllegalStateException("Unknown mode: $mode") }
    }
    val selectionType = if (selection.toLowerCasePreservingASCIIRules() == ALL_CPA_IDS) {
        SelectionType.ALL
    } else if (selection.toLowerCasePreservingASCIIRules() == ONLY_OLD_EMOTTAK) {
        SelectionType.NONE
    } else if (selection.toLowerCasePreservingASCIIRules().startsWith(PERCENT_PREFIX)) {
        SelectionType.PERCENTAGE
    } else if (selection.toLowerCasePreservingASCIIRules().startsWith(LAST_DIGIT_PREFIX)) {
        SelectionType.CPAID_FORMAT
    } else { throw IllegalStateException("Unknown selection: $selection") }

    var percentValue = 0
    if (selectionType == SelectionType.PERCENTAGE) percentValue = selection.substring(PERCENT_PREFIX.length).toInt()
    val lastDigits = mutableSetOf<Int>()
    if (selectionType == SelectionType.CPAID_FORMAT) {
        val digits: String = selection.substring(LAST_DIGIT_PREFIX.length)
        digits.forEach {
                char ->
            lastDigits.add(char.digitToInt())
        }
    }

    val whiteListCpas = mutableSetOf<String>()
    if (whitelist != null) {
        whiteListCpas.addAll(readCpaIds(whitelist, name))
    }
    val blackListCpas = mutableSetOf<String>()
    if (blacklist != null) {
        blackListCpas.addAll(readCpaIds(blacklist, name))
    }

    return ServiceRule(filterType, selectionType, percentValue, lastDigits, whiteListCpas, blackListCpas)
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

private fun ServiceRule.describe(serviceName: String): String {
    val selectionString = when (selectionType) {
        SelectionType.ALL -> "ALL"
        SelectionType.NONE -> "None/whitelist only"
        SelectionType.PERCENTAGE -> "$percentage % to NEW"
        SelectionType.CPAID_FORMAT -> "Last digit in CPA ID one of $lastDigits is sent to NEW"
    }
    return "Filter rule: service '$serviceName' -> $filterType, $selectionString, whitelist: ${whitelist.size}, blacklist: ${blacklist.size}}"
}
