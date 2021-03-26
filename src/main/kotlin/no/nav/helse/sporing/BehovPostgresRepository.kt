package no.nav.helse.sporing

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import org.intellij.lang.annotations.Language
import java.util.*
import javax.sql.DataSource

internal class BehovPostgresRepository(dataSourceProvider: () -> DataSource): BehovRepository {
    private val dataSource by lazy(dataSourceProvider)
    private val separator = "|"

    @Language("PostgreSQL")
    private val insertBehovStatement = """
        INSERT INTO behov (id, typer)
        VALUES (:id, :typer)
        ON CONFLICT (id) DO NOTHING
    """
    override fun lagre(meldingId: UUID, behov: List<String>) {
        using(sessionOf(dataSource, returnGeneratedKey = true)) {
            it.run(queryOf(insertBehovStatement, mapOf(
                "id" to meldingId,
                "typer" to behov.joinToString(separator = separator)
            )).asExecute)
        }
    }

    @Language("PostgreSQL")
    private val selectBehovStatement = """
        SELECT typer FROM behov WHERE id = ?
    """
    override fun finnBehov(meldingId: UUID) = using(sessionOf(dataSource)) {
        it.run(queryOf(selectBehovStatement, meldingId).map { row ->
            row.string("typer").split(separator)
        }.asSingle)
    }

}