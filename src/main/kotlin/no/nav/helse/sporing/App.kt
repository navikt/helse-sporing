package no.nav.helse.sporing

import com.fasterxml.jackson.databind.JsonNode
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotliquery.Session
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.helse.rapids_rivers.*
import org.flywaydb.core.Flyway
import org.intellij.lang.annotations.Language
import java.time.LocalDateTime
import java.util.*
import javax.sql.DataSource

private fun fixJdbcUrl(url: String) =
    "jdbc:" + url.removePrefix("jdbc:").replace("postgres://", "postgresql://")
fun main() {
    val env = System.getenv()
    val hikariConfig = HikariConfig().apply {
        jdbcUrl = fixJdbcUrl(env.getValue("NAIS_DATABASE_SPORING_SPORING_URL"))
        maximumPoolSize = 3
        minimumIdle = 1
        idleTimeout = 10001
        connectionTimeout = 1000
        maxLifetime = 30001
    }
    val dataSource = HikariDataSource(hikariConfig)

    val repo = PostgresRepository(dataSource)

    RapidApplication.Builder(RapidApplication.RapidApplicationConfig.fromEnv(env))
        .withKtorModule {  }
        .build()
        .apply {
            register(repo)
            Tilstandsendringer(this, repo)
        }
        .start()
}

interface TilstandsendringRepository {
    fun lagre(meldingId: UUID, vedtaksperiodeId: UUID, fraTilstand: String, tilTilstand: String, fordi: String, når: LocalDateTime)
}

internal class PostgresRepository(private val dataSource: DataSource): RapidsConnection.StatusListener, TilstandsendringRepository{
    override fun lagre(meldingId: UUID, vedtaksperiodeId: UUID, fraTilstand: String, tilTilstand: String, fordi: String, når: LocalDateTime) {
        using(sessionOf(dataSource, returnGeneratedKey = true)) { session ->
            session.transaction { txSession ->
                val tilstandsendringId = lagreTransisjon(txSession, fraTilstand, tilTilstand, fordi, når) ?: return@transaction
                kobleVedtaksperiodeTilTransisjon(txSession, meldingId, tilstandsendringId, vedtaksperiodeId, når)
            }
        }
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
        return session.run(queryOf(insertTransitionStatement, mapOf(
            "fraTilstand" to fraTilstand,
            "tilTilstand" to tilTilstand,
            "fordi" to fordi,
            "naar" to når
        )).asUpdateAndReturnGeneratedKey)
    }

    @Language("PostgreSQL")
    private val insertVedtaksperiodeTransitionStatement = """
        INSERT INTO vedtaksperiode_tilstandsendring (melding_id, vedtaksperiode_id, tilstandsendring_id, naar)
        VALUES (:meldingId, :vedtaksperiodeId, :tilstandsendringId, :naar)
        ON CONFLICT (melding_id) DO NOTHING
    """
    private fun kobleVedtaksperiodeTilTransisjon(session: Session, meldingId: UUID, tilstandsendringId: Long, vedtaksperiodeId: UUID, når: LocalDateTime) {
        session.run(queryOf(insertVedtaksperiodeTransitionStatement, mapOf(
            "meldingId" to meldingId,
            "vedtaksperiodeId" to vedtaksperiodeId,
            "tilstandsendringId" to tilstandsendringId,
            "naar" to når
        )).asExecute)
    }

    override fun onStartup(rapidsConnection: RapidsConnection) {
        migrate()
    }

    private fun migrate() {
        Flyway.configure().dataSource(dataSource).load().migrate()
    }
}

private class Tilstandsendringer(rapidsConnection: RapidsConnection, repository: TilstandsendringRepository) {
    init {
        River(rapidsConnection)
            .validate {
                it.demandValue("@event_name", "vedtaksperiode_endret")
                it.requireKey("@id", "@forårsaket_av.event_name", "vedtaksperiodeId", "forrigeTilstand", "gjeldendeTilstand")
                it.interestedIn("@behov")
                it.require("@opprettet", JsonNode::asLocalDateTime)
            }
            .onSuccess { message, _ ->
                val eventName = eventName(message)
                repository.lagre(
                    meldingId = UUID.fromString(message["@id"].asText()),
                    vedtaksperiodeId = UUID.fromString(message["vedtaksperiodeId"].asText()),
                    fraTilstand = message["forrigeTilstand"].asText(),
                    fordi = eventName,
                    tilTilstand = message["gjeldendeTilstand"].asText(),
                    når = message["@opprettet"].asLocalDateTime()
                )
            }
    }

    private fun eventName(message: JsonMessage): String {
        if (message["@behov"].isMissingOrNull()) return message["@forårsaket_av.event_name"].asText()
        return message["@behov"].asSequence().map(JsonNode::asText).sorted().map(String::toLowerCase).joinToString(separator = "", transform = String::capitalize)
    }
}