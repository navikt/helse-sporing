package no.nav.helse.sporing

import java.time.LocalDateTime
import java.util.*

interface TilstandsendringRepository {
    fun lagre(meldingId: UUID, vedtaksperiodeId: UUID, fraTilstand: String, tilTilstand: String, fordi: String, når: LocalDateTime)
}