package no.nav.emottak.util

import arrow.core.raise.catch
import kotlin.uuid.Uuid

fun String.isValidUuid(): Boolean = catch({ Uuid.parse(this); true }) { false }
