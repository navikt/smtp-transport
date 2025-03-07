package no.nav.emottak.util

import io.github.nomisRev.kafka.receiver.ReceiverRecord

fun <K, V> ReceiverRecord<K, V>.getHeaderValueAsString(value: String): String =
    when (val header = headers().lastHeader(value)) {
        null -> ""
        else -> String(header.value())
    }
