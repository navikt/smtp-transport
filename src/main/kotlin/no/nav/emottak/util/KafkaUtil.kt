package no.nav.emottak.util

import io.github.nomisRev.kafka.receiver.ReceiverRecord
import no.nav.emottak.log

const val EMAIL_ADDRESSES = "emailAddresses"

fun <K, V> ReceiverRecord<K, V>.getHeaderValueAsString(value: String): String =
    when (val header = headers().lastHeader(value)) {
        null -> "".also { log.warn("Kafka header missing: $value") }
        else -> String(header.value())
    }
