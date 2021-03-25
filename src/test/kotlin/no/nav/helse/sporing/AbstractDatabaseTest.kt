package no.nav.helse.sporing

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import javax.sql.DataSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal abstract class AbstractDatabaseTest {

    protected lateinit var repository: PostgresRepository

    @BeforeAll
    fun createDatabase() {
        PostgresDatabase.start()
        repository = PostgresRepository { PostgresDatabase.connection() }
    }

    @AfterEach
    fun resetSchema() {
        PostgresDatabase.reset()
    }
}