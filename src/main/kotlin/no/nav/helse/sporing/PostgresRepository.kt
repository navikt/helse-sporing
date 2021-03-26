package no.nav.helse.sporing

import kotliquery.Session
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.helse.rapids_rivers.RapidsConnection
import org.flywaydb.core.Flyway
import org.intellij.lang.annotations.Language
import java.time.LocalDateTime
import java.util.*
import javax.sql.DataSource

internal class PostgresRepository(dataSourceProvider: () -> DataSource): RapidsConnection.StatusListener, TilstandsendringRepository {
    private val dataSource by lazy(dataSourceProvider)

    override fun lagre(meldingId: UUID, vedtaksperiodeId: UUID, fraTilstand: String, tilTilstand: String, fordi: String, når: LocalDateTime) {
        using(sessionOf(dataSource, returnGeneratedKey = true)) { session ->
            session.transaction { txSession ->
                val tilstandsendringId = lagreTransisjon(txSession, fraTilstand, tilTilstand, fordi, når) ?: return@transaction
                kobleVedtaksperiodeTilTransisjon(txSession, meldingId, tilstandsendringId, vedtaksperiodeId, når)
            }
        }
    }

    @Language("PostgreSQL")
    private val selectTransitionStatemenet = """
        SELECT * FROM tilstandsendring
    """
    override fun tilstandsendringer() = using(sessionOf(dataSource)) {
        it.run(queryOf(selectTransitionStatemenet).map { row ->
            TilstandsendringDto(
                fraTilstand = row.string("fra_tilstand"),
                tilTilstand = row.string("til_tilstand"),
                fordi = row.string("fordi"),
                førstegang = row.localDateTime("forste_gang"),
                sistegang = row.localDateTime("siste_gang")
            )
        }.asList)
    }

    @Language("PostgreSQL")
    private val selectVedtaksperiodeTransitionStatemenet = """
        SELECT t.fra_tilstand, t.til_tilstand, t.fordi, vt.naar 
        FROM vedtaksperiode_tilstandsendring vt
        INNER JOIN tilstandsendring t ON vt.tilstandsendring_id = t.id
        WHERE vt.vedtaksperiode_id = :vedtaksperiodeId
        ORDER BY vt.naar ASC, vt.id ASC
    """
    override fun tilstandsendringer(vedtaksperiodeId: UUID) = using(sessionOf(dataSource)) {
        it.run(queryOf(selectVedtaksperiodeTransitionStatemenet, mapOf(
            "vedtaksperiodeId" to vedtaksperiodeId
        )).map { row ->
            TilstandsendringDto(
                fraTilstand = row.string("fra_tilstand"),
                tilTilstand = row.string("til_tilstand"),
                fordi = row.string("fordi"),
                førstegang = row.localDateTime("naar"),
                sistegang = row.localDateTime("naar")
            )
        }.asList)
    }

    @Language("PostgreSQL")
    private val insertTransitionStatement = """
        INSERT INTO tilstandsendring (fra_tilstand, til_tilstand, fordi, forste_gang, siste_gang)
        VALUES (:fraTilstand, :tilTilstand, :fordi, :naar, :naar)
        ON CONFLICT(fra_tilstand, til_tilstand, fordi) DO 
        UPDATE 
            SET siste_gang = EXCLUDED.siste_gang 
            WHERE EXCLUDED.siste_gang >= tilstandsendring.siste_gang
        RETURNING id
    """
    private fun lagreTransisjon(session: Session, fraTilstand: String, tilTilstand: String, fordi: String, når: LocalDateTime): Long? {
        return session.run(
            queryOf(
                insertTransitionStatement, mapOf(
                    "fraTilstand" to fraTilstand,
                    "tilTilstand" to tilTilstand,
                    "fordi" to fordi,
                    "naar" to når
                )
            ).asUpdateAndReturnGeneratedKey)
    }

    @Language("PostgreSQL")
    private val insertVedtaksperiodeTransitionStatement = """
        INSERT INTO vedtaksperiode_tilstandsendring (melding_id, vedtaksperiode_id, tilstandsendring_id, naar)
        VALUES (:meldingId, :vedtaksperiodeId, :tilstandsendringId, :naar)
        ON CONFLICT (melding_id) DO NOTHING
    """
    private fun kobleVedtaksperiodeTilTransisjon(session: Session, meldingId: UUID, tilstandsendringId: Long, vedtaksperiodeId: UUID, når: LocalDateTime) {
        session.run(
            queryOf(
                insertVedtaksperiodeTransitionStatement, mapOf(
                    "meldingId" to meldingId,
                    "vedtaksperiodeId" to vedtaksperiodeId,
                    "tilstandsendringId" to tilstandsendringId,
                    "naar" to når
                )
            ).asExecute)
    }

    override fun onStartup(rapidsConnection: RapidsConnection) {
        migrate()
    }

    private fun migrate() {
        Flyway.configure().dataSource(dataSource).load().migrate()
    }

}