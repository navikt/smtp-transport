package no.nav.emottak

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HeadersBuilder
import io.ktor.http.content.PartData
import io.ktor.util.CaseInsensitiveMap
import jakarta.mail.internet.MimeUtility.unfold
import no.nav.emottak.configuration.Ebms
import no.nav.emottak.smtp.EmailMsg
import no.nav.emottak.smtp.MimeHeaders.CONTENT_DESCRIPTION
import no.nav.emottak.smtp.MimeHeaders.CONTENT_DISPOSITION
import no.nav.emottak.smtp.MimeHeaders.CONTENT_ID
import no.nav.emottak.smtp.MimeHeaders.CONTENT_TRANSFER_ENCODING
import no.nav.emottak.smtp.MimeHeaders.CONTENT_TYPE
import no.nav.emottak.smtp.MimeHeaders.MIME_VERSION
import no.nav.emottak.smtp.MimeHeaders.SOAP_ACTION
import no.nav.emottak.smtp.SMTPHeaders.DATE
import no.nav.emottak.smtp.SMTPHeaders.FROM
import no.nav.emottak.smtp.SMTPHeaders.MESSAGE_ID
import no.nav.emottak.smtp.SMTPHeaders.TO
import no.nav.emottak.smtp.SMTPHeaders.X_MAILER
import no.nav.emottak.smtp.log

suspend fun HttpClient.postEbmsMessageSinglePart(ebms: Ebms, message: EmailMsg) = post(ebms.providerUrl) {
    headers(
        message.headers.filterHeader(
            MIME_VERSION,
            CONTENT_ID,
            SOAP_ACTION,
            CONTENT_TYPE,
            CONTENT_TRANSFER_ENCODING,
            FROM,
            TO,
            MESSAGE_ID,
            DATE,
            X_MAILER
        )
    )
    setBody(
        message.parts.first().bytes
    )
}

suspend fun HttpClient.postEbmsMessageMultiPart(ebms: Ebms, message: EmailMsg): HttpResponse {
    val partData: List<PartData> = message.parts.map { part ->
        PartData.FormItem(
            String(part.bytes),
            {},
            Headers.build(
                part.headers.filterHeader(
                    CONTENT_ID,
                    CONTENT_TYPE,
                    CONTENT_TRANSFER_ENCODING,
                    CONTENT_DISPOSITION,
                    CONTENT_DESCRIPTION
                )
            )
        )
    }
    val contentType = message.headers[CONTENT_TYPE]!!
    val boundary = ContentType.parse(contentType).parameter("boundary")

    return post(ebms.providerUrl) {
        headers(
            message.headers.filterHeader(
                MIME_VERSION,
                CONTENT_ID,
                SOAP_ACTION,
                CONTENT_TYPE,
                CONTENT_TRANSFER_ENCODING,
                FROM,
                TO,
                MESSAGE_ID,
                DATE,
                X_MAILER
            )
        )
        setBody(
            MultiPartFormDataContent(
                partData,
                boundary!!,
                ContentType.parse(contentType)
            )
        )
    }
}

fun Map<String, String>.filterHeader(vararg headerNames: String): HeadersBuilder.() -> Unit = {
    val caseInsensitiveMap = CaseInsensitiveMap<String>().apply { putAll(this@filterHeader) }
    headerNames.map { Pair(it, caseInsensitiveMap[it]) }
        .forEach {
            if (it.second != null) {
                val headerValue = unfold(it.second!!.replace("\t", " "))
                append(it.first, headerValue)
            }
        }
    appendMessageIdWhenContentIdMissing(caseInsensitiveMap)
}

private fun HeadersBuilder.appendMessageIdWhenContentIdMissing(caseInsensitiveMap: CaseInsensitiveMap<String>) {
    if (unfold(caseInsensitiveMap[CONTENT_TYPE])?.contains("text/xml") == true) {
        if (caseInsensitiveMap[CONTENT_ID] != null) {
            log.warn(
                "Content-Id header already set for text/xml: " + caseInsensitiveMap[CONTENT_ID] +
                    "\nMessage-Id: " + caseInsensitiveMap[MESSAGE_ID]
            )
        } else {
            val headerValue = unfold(caseInsensitiveMap[MESSAGE_ID]!!.replace("\t", " "))
            append(CONTENT_ID, headerValue)
            log.info("Header: <$CONTENT_ID> - <$headerValue>")
        }
    }
}
