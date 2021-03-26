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
    private val behovrepo = TestBehovRepo()
    private val testRapid = TestRapid()
        .apply { Tilstandsendringer(this, repo, behovrepo) }

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
      "event_name": "ny_søknad"
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

        override fun lagre(meldingId: UUID, vedtaksperiodeId: UUID, fraTilstand: String, tilTilstand: String, fordi: String, når: LocalDateTime) {
            tilstandsendringer.add(Triple(fraTilstand, tilTilstand, når))
        }

        override fun tilstandsendringer(): List<TilstandsendringDto> {
            throw NotImplementedError()
        }

        override fun tilstandsendringer(vedtaksperiodeId: UUID): List<TilstandsendringDto> {
            throw NotImplementedError()
        }
    }

    private class TestBehovRepo() : BehovRepository {
        override fun lagre(meldingId: UUID, behov: List<String>) {
            throw NotImplementedError()
        }

        override fun finnBehov(meldingId: UUID): List<String>? {
            return null
        }
    }
}