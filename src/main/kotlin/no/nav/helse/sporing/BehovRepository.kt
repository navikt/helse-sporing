package no.nav.helse.sporing

import java.time.LocalDateTime
import java.util.*

internal interface BehovRepository {
    fun lagre(meldingId: UUID, behov: List<String>)
    fun finnBehov(meldingId: UUID): List<String>?
}