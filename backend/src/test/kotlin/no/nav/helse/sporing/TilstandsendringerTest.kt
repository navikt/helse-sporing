package no.nav.helse.sporing

import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

internal class TilstandsendringerTest {
    private val repo = TestRepo()
    private val testRapid = TestRapid()
        .apply { Tilstandsendringer(this, repo) }

    @BeforeEach
    fun setup() {
        testRapid.reset()
        repo.reset()
    }

    @Test
    fun tilstandsendringer() {
        testRapid.sendTestMessage(tilstandsendringJson(UUID.randomUUID(), "START", "SYKMELDING_MOTTATT"))
        assertEquals(1, repo.antallTilstandsendringer())
    }

    @Test
    fun `ingen tilstandsendring når forrige og gjelende tilstand er like`() {
        testRapid.sendTestMessage(tilstandsendringJson(UUID.randomUUID(), "AVSLUTTET_UTEN_UTBETALING", "AVSLUTTET_UTEN_UTBETALING"))
        assertEquals(0, repo.antallTilstandsendringer())
    }

    private fun tilstandsendringJson(
        vedtaksperiodeId: UUID,
        fraTilstand: String,
        tilTilstand: String,
        tidspunkt: LocalDateTime = LocalDateTime.now()
    ): String {
        @Language("JSON")
        val template = """
{
    "@event_name": "vedtaksperiode_endret",
    "@id": "${UUID.randomUUID()}",
    "@forårsaket_av": {
      "id": "${UUID.randomUUID()}",
      "event_name": "ny_søknad",
      "opprettet": "${LocalDateTime.now()}"
    },
    "@opprettet": "$tidspunkt",
    "vedtaksperiodeId": "$vedtaksperiodeId",
    "forrigeTilstand": "$fraTilstand",
    "gjeldendeTilstand": "$tilTilstand"
}
"""
        return template
    }

    private class TestRepo : TilstandsendringRepository {
        private val tilstandsendringer = mutableListOf<Triple<String, String, LocalDateTime>>()

        internal fun antallTilstandsendringer() = tilstandsendringer.size
        internal fun tilstandsendring(indeks: Int) = tilstandsendringer[indeks]

        internal fun reset() {
            tilstandsendringer.clear()
        }

        override fun lagre(meldingId: UUID, vedtaksperiodeId: UUID, fraTilstand: String, tilTilstand: String, fordi: String, når: LocalDateTime, årsak: Årsak) {
            tilstandsendringer.add(Triple(fraTilstand, tilTilstand, når))
        }

        override fun tilstandsendringer(fordi: List<String>, etter: LocalDateTime?, ignorerTilstand: List<String>, ignorerFordi: List<String>): List<TilstandsendringDto> {
            throw NotImplementedError()
        }

        override fun tilstandsendringer(vedtaksperiodeId: UUID): List<TilstandsendringDto> {
            throw NotImplementedError()
        }

        override fun personendringer(vedtaksperioder: List<UUID>): List<PersonendringDto> {
            throw NotImplementedError()
        }
    }
}