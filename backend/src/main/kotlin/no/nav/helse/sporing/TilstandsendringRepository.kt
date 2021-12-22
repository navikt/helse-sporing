package no.nav.helse.sporing

import java.time.LocalDateTime
import java.util.*

internal interface TilstandsendringRepository {
    fun lagre(meldingId: UUID, vedtaksperiodeId: UUID, fraTilstand: String, tilTilstand: String, fordi: String, når: LocalDateTime)
    fun tilstandsendringer(fordi: List<String>, etter: LocalDateTime?, ignorerTilstand: List<String>, ignorerFordi: List<String>): List<TilstandsendringDto>
    fun tilstandsendringer(vedtaksperiodeId: UUID): List<TilstandsendringDto>
}