package no.nav.helse.sporing

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import org.flywaydb.core.Flyway
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.time.LocalDateTime
import java.util.*
import javax.sql.DataSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class MigrationTest : AbstractDatabaseTest() {

    @Test
    fun `oppretter tilstandsendringer`() {
        val vedtaksperiodeId1 = UUID.randomUUID()
        val vedtaksperiodeId2 = UUID.randomUUID()
        val førstegang = LocalDateTime.now().minusDays(2)
        val andregang = LocalDateTime.now().minusDays(1)
        val tredjegang = LocalDateTime.now()
        repository.lagre(UUID.randomUUID(), vedtaksperiodeId1, "START", "MOTTATT_SYKMELDING", "Sykmelding", førstegang)
        repository.lagre(UUID.randomUUID(), vedtaksperiodeId2, "START", "MOTTATT_SYKMELDING", "Sykmelding", andregang)
        repository.lagre(UUID.randomUUID(), vedtaksperiodeId2, "MOTTATT_SYKMELDING", "START", "Sykmelding", tredjegang)

        val tilstandsendringer = tilstandsendringer()
        assertEquals(2, tilstandsendringer.size)
        assertTrue(tilstandsendringer.first().førstegang(førstegang))
        assertTrue(tilstandsendringer.first().sistegang(andregang))
        assertTrue(tilstandsendringer.last().førstegang(tredjegang))
        assertTrue(tilstandsendringer.last().sistegang(tredjegang))
    }

    @Test
    fun `oppretter ikke nye rader for samme melding`() {
        val meldingId = UUID.randomUUID()
        val førstegang = LocalDateTime.now().minusDays(2)
        val andregang = LocalDateTime.now().minusDays(4)
        repository.lagre(meldingId, UUID.randomUUID() , "START", "MOTTATT_SYKMELDING", "Sykmelding", førstegang)
        repository.lagre(meldingId, UUID.randomUUID(), "START", "MOTTATT_SYKMELDING", "Sykmelding", andregang)

        val tilstandsendringer = tilstandsendringer()
        assertEquals(1, tilstandsendringer.size)
        assertTrue(tilstandsendringer.first().førstegang(førstegang))
        assertTrue(tilstandsendringer.first().sistegang(førstegang))
    }

    private fun tilstandsendringer() = using(sessionOf(PostgresDatabase.connection())) {
        it.run(queryOf("SELECT * FROM tilstandsendring ORDER BY id ASC").map {
            Tilstandsendring(it.string("fra_tilstand"), it.string("til_tilstand"), it.string("fordi"), it.localDateTime("forste_gang"), it.localDateTime("siste_gang"))
        }.asList)
    }

    private class Tilstandsendring(
        private val fraTilstand: String,
        private val tilTilstand: String,
        private val fordi: String,
        private val førstegang: LocalDateTime,
        private val sistegang: LocalDateTime) {

        fun førstegang(other: LocalDateTime) = other.withNano(0) == førstegang.withNano(0)
        fun sistegang(other: LocalDateTime) = other.withNano(0) == sistegang.withNano(0)
    }
}