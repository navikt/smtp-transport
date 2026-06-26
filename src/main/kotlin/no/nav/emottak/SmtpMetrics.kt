package no.nav.emottak

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import no.nav.emottak.smtp.ForwardableMimeMessage
import java.util.concurrent.atomic.AtomicInteger

const val MESSAGES_RECEIVED_COUNTER = "smtp_transport_messages_received_total"
const val TAG_FORWARDING_SYSTEM = "forwarding_system"
const val TAG_SERVICE = "service"
const val TAG_ACTION = "action"

fun MeterRegistry.incrementMessagesReceived(forwardableMimeMessage: ForwardableMimeMessage) =
    counter(
        MESSAGES_RECEIVED_COUNTER,
        TAG_FORWARDING_SYSTEM,
        forwardableMimeMessage.forwardingSystem.name,
        TAG_SERVICE,
        forwardableMimeMessage.service,
        TAG_ACTION,
        forwardableMimeMessage.action
    ).increment()

const val MESSAGES_SENT_COUNTER = "smtp_transport_messages_sent_total"
const val TAG_MESSAGE_TYPE = "message_type"
const val TAG_SENDER_ADDRESS = "sender_address"

fun MeterRegistry.incrementMessagesSent(messageType: String, service: String, action: String, senderAddress: String) =
    counter(
        MESSAGES_SENT_COUNTER,
        TAG_MESSAGE_TYPE,
        messageType,
        TAG_SERVICE,
        service,
        TAG_ACTION,
        action,
        TAG_SENDER_ADDRESS,
        senderAddress
    ).increment()

const val INBOX_SIZE_GAUGE = "smtp_transport_inbox_size"

fun MeterRegistry.registerInboxSizeGauge(value: AtomicInteger): Gauge =
    Gauge.builder(INBOX_SIZE_GAUGE) { value.get().toDouble() }
        .description("Number of messages in the POP3 inbox at last poll")
        .register(this)
