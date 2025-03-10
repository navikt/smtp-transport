package no.nav.emottak.util

import io.github.nomisRev.kafka.receiver.ReceiverRecord
import no.nav.emottak.log

fun <K, V> ReceiverRecord<K, V>.getHeaderValueAsString(value: String): String =
    when (val header = headers().lastHeader(value)) {
        null -> "".also { log.warn("Header missing: $value") }
        else -> String(header.value())
    }
