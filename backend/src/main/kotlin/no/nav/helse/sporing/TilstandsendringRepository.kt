package no.nav.helse.sporing

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

internal interface TilstandsendringRepository {
    fun lagre(meldingId: UUID, vedtaksperiodeId: UUID, fraTilstand: String, tilTilstand: String, fordi: String, når: LocalDateTime, årsak: Årsak)
    fun tilstandsendringer(fordi: List<String>, etter: LocalDateTime?, ignorerTilstand: List<String>, ignorerFordi: List<String>): List<TilstandsendringDto>
    fun tilstandsendringer(vedtaksperiodeId: UUID): List<TilstandsendringDto>
    fun personendringer(vedtaksperioder: List<UUID>): List<PersonendringDto>
}

internal class Årsak(val id: UUID, val navn: String, val opprettet: LocalDateTime)