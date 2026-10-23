package no.nav.helse.sporing

import kotliquery.Session
import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.*
import javax.sql.DataSource

internal class PostgresRepository(
    dataSourceProvider: () -> DataSource,
) : TilstandsendringRepository {
    private companion object {
        private val log = LoggerFactory.getLogger(Tilstandsendringer::class.java)
        private val sikkerLog = LoggerFactory.getLogger("tjenestekall")
    }

    private val dataSource by lazy(dataSourceProvider)

    override fun lagre(
        meldingId: UUID,
        vedtaksperiodeId: UUID,
        fraTilstand: String,
        tilTilstand: String,
        fordi: String,
        når: LocalDateTime,
        årsak: Årsak,
    ) {
        sessionOf(dataSource, returnGeneratedKey = true).use { session ->
            session.transaction { txSession ->
                val årsakId = lagreÅrsak(txSession, årsak.id, årsak.navn, årsak.opprettet) ?: return@transaction log.info("tilstandsendring ble ikke lagret pga manglende årsakId")
                val tilstandsendringId = lagreTransisjon(txSession, fraTilstand, tilTilstand, fordi, når) ?: return@transaction log.info("tilstandsendring ble ikke lagret pga manglende tilstandsendringId")
                kobleVedtaksperiodeTilTransisjon(txSession, meldingId, tilstandsendringId, årsakId, vedtaksperiodeId, når)
            }
        }
    }

    @Language("PostgreSQL")
    private val selectTransitionStatemenet = """
        SELECT t.*, vt.count
        FROM tilstandsendring t
        JOIN (
            SELECT tilstandsendring_id, count(*) AS count
            FROM vedtaksperiode_tilstandsendring
            GROUP BY tilstandsendring_id
        ) vt ON vt.tilstandsendring_id = t.id;
    """

    @Language("PostgreSQL")
    private val selectUnikeTransitionStatement = """
        select count(1) as count, string_agg(fordi, ',') as fordi, fra_tilstand, til_tilstand, min(forste_gang) as forste_gang,max(siste_gang) as siste_gang 
        from tilstandsendring group by fra_tilstand,til_tilstand;
    """

    override fun tilstandsendringer(
        bareUnike: Boolean,
        fordi: List<String>,
        etter: LocalDateTime?,
        ignorerTilstand: List<String>,
        ignorerFordi: List<String>,
    ): List<TilstandsendringDto> {
        val tilstandsendringer =
            sessionOf(dataSource).use {
                val spørring = if (bareUnike) selectUnikeTransitionStatement else selectTransitionStatemenet
                it.run(
                    queryOf(spørring)
                        .map { row ->
                            TilstandsendringDto(
                                fraTilstand = row.string("fra_tilstand"),
                                tilTilstand = row.string("til_tilstand"),
                                fordi = row.string("fordi").takeUnless { bareUnike } ?: "",
                                førstegang = row.localDateTime("forste_gang"),
                                sistegang = row.localDateTime("siste_gang"),
                                antall = row.long("count"),
                            )
                        }.asList,
                )
            }
        return filtrer(fordi.map(String::lowercase), etter, ignorerTilstand.map(String::lowercase), ignorerFordi.map(String::lowercase), tilstandsendringer)
    }

    private fun filtrer(
        fordi: List<String>,
        etter: LocalDateTime?,
        ignorerTilstand: List<String>,
        ignorerFordi: List<String>,
        tilstander: List<TilstandsendringDto>,
    ): List<TilstandsendringDto> =
        tilstander
            .filter { fordi.isEmpty() || it.fordi.lowercase() in fordi }
            .filter { it.fordi.lowercase() !in ignorerFordi }
            .filter { it.tilTilstand.lowercase() !in ignorerTilstand }
            .filter { etter == null || it.sistegang >= etter }

    @Language("PostgreSQL")
    private val selectVedtaksperiodeTransitionStatemenet = """
        SELECT t.fra_tilstand, t.til_tilstand, t.fordi, vt.naar 
        FROM vedtaksperiode_tilstandsendring vt
        INNER JOIN tilstandsendring t ON vt.tilstandsendring_id = t.id
        WHERE vt.vedtaksperiode_id = :vedtaksperiodeId
        ORDER BY vt.naar ASC, vt.id ASC
    """

    override fun tilstandsendringer(vedtaksperiodeId: UUID) =
        sessionOf(dataSource).use {
            it.run(
                queryOf(
                    selectVedtaksperiodeTransitionStatemenet,
                    mapOf(
                        "vedtaksperiodeId" to vedtaksperiodeId,
                    ),
                ).map { row ->
                    TilstandsendringDto(
                        fraTilstand = row.string("fra_tilstand"),
                        tilTilstand = row.string("til_tilstand"),
                        fordi = row.string("fordi"),
                        førstegang = row.localDateTime("naar"),
                        sistegang = row.localDateTime("naar"),
                        antall = 1,
                    )
                }.asList,
            )
        }

    @Language("PostgreSQL")
    private val insertÅrsakStatement = """
        INSERT INTO arsak (melding_id, navn, opprettet) VALUES (:id, :navn, :opprettet) ON CONFLICT(melding_id) DO UPDATE SET navn=EXCLUDED.navn RETURNING id
    """

    private fun lagreÅrsak(
        session: Session,
        id: UUID,
        navn: String,
        opprettet: LocalDateTime,
    ): Long? =
        session.run(
            queryOf(
                insertÅrsakStatement,
                mapOf(
                    "id" to id,
                    "navn" to navn,
                    "opprettet" to opprettet,
                ),
            ).asUpdateAndReturnGeneratedKey,
        )

    @Language("PostgreSQL")
    private val insertTransitionStatement = """
        INSERT INTO tilstandsendring (fra_tilstand, til_tilstand, fordi, forste_gang, siste_gang)
        VALUES (:fraTilstand, :tilTilstand, :fordi, :naar, :naar)
        ON CONFLICT(fra_tilstand, til_tilstand, fordi) DO 
        UPDATE SET siste_gang = GREATEST(EXCLUDED.siste_gang, tilstandsendring.siste_gang)
        RETURNING id
    """

    private fun lagreTransisjon(
        session: Session,
        fraTilstand: String,
        tilTilstand: String,
        fordi: String,
        når: LocalDateTime,
    ): Long? =
        session.run(
            queryOf(
                insertTransitionStatement,
                mapOf(
                    "fraTilstand" to fraTilstand,
                    "tilTilstand" to tilTilstand,
                    "fordi" to fordi,
                    "naar" to når,
                ),
            ).asUpdateAndReturnGeneratedKey,
        )

    @Language("PostgreSQL")
    private val insertVedtaksperiodeTransitionStatement = """
        INSERT INTO vedtaksperiode_tilstandsendring (melding_id, vedtaksperiode_id, tilstandsendring_id, arsak_id, naar)
        VALUES (:meldingId, :vedtaksperiodeId, :tilstandsendringId, :arsakId, :naar)
        ON CONFLICT (melding_id) DO 
        UPDATE SET arsak_id = EXCLUDED.arsak_id 
        WHERE vedtaksperiode_tilstandsendring.arsak_id IS NULL 
    """

    private fun kobleVedtaksperiodeTilTransisjon(
        session: Session,
        meldingId: UUID,
        tilstandsendringId: Long,
        årsakId: Long,
        vedtaksperiodeId: UUID,
        når: LocalDateTime,
    ) {
        session.run(
            queryOf(
                insertVedtaksperiodeTransitionStatement,
                mapOf(
                    "meldingId" to meldingId,
                    "vedtaksperiodeId" to vedtaksperiodeId,
                    "tilstandsendringId" to tilstandsendringId,
                    "arsakId" to årsakId,
                    "naar" to når,
                ),
            ).asExecute,
        )
    }
}
