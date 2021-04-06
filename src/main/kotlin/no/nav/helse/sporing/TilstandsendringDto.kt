package no.nav.helse.sporing

import java.time.LocalDateTime

internal class TilstandsendringDto(
    val fraTilstand: String,
    val tilTilstand: String,
    val fordi: String,
    val førstegang: LocalDateTime,
    val sistegang: LocalDateTime,
    val antall: Long
)