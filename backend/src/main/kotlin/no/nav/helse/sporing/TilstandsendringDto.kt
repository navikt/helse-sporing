package no.nav.helse.sporing

import java.time.LocalDateTime
import java.util.*

internal class TilstandsendringDto(
    val fraTilstand: String,
    val tilTilstand: String,
    val fordi: String,
    val førstegang: LocalDateTime,
    val sistegang: LocalDateTime,
    val antall: Long
)

internal class PersonendringDto(
    val meldingId: UUID,
    val navn: String,
    val opprettet: LocalDateTime,
    val vedtaksperiodeId: UUID,
    val når: LocalDateTime,
    val fraTilstand: String,
    val tilTilstand: String
)