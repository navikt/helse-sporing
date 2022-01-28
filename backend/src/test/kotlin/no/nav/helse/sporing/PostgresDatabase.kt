import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import org.flywaydb.core.Flyway
import org.intellij.lang.annotations.Language
import org.testcontainers.containers.PostgreSQLContainer
import javax.sql.DataSource

internal object PostgresDatabase {

    private var state: DBState = NotStarted
    private val postgres = PostgreSQLContainer<Nothing>("postgres:13")
    private var dataSource: DataSource? = null

    fun start(): PostgresDatabase {
        state.start(this)
        return this
    }

    fun reset() {
        state.reset(this)
    }

    fun connection() = state.connection(this)

    private fun stop(): PostgresDatabase {
        state.stop(this)
        return this
    }

    private fun startDatbase() {
        postgres.start()
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = postgres.jdbcUrl
            username = postgres.username
            password = postgres.password
            maximumPoolSize = 3
            initializationFailTimeout = 5000
            minimumIdle = 1
            idleTimeout = 10001
            connectionTimeout = 1000
            maxLifetime = 30001
        }
        dataSource = HikariDataSource(hikariConfig)
        createSchema(connection())
        Runtime.getRuntime().addShutdownHook(Thread(this::stop))
    }

    private fun createSchema(dataSource: DataSource) {
        Flyway.configure().dataSource(dataSource).initSql("create user cloudsqliamuser with encrypted password 'foo';").load().migrate()
        using(sessionOf(dataSource)) { it.run(queryOf(truncateTablesSql).asExecute) }
    }

    private fun resetSchema() {
        using(sessionOf(connection())) { it.run(queryOf("SELECT truncate_tables();").asExecute) }
    }

    private fun stopDatabase() {
        postgres.stop()
    }

    private interface DBState {
        fun connection(db: PostgresDatabase): DataSource {
            throw IllegalStateException("Cannot create connection in state ${this::class.simpleName}")
        }
        fun start(db: PostgresDatabase) {}
        fun stop(db: PostgresDatabase) {}
        fun reset(db: PostgresDatabase) {}
    }

    private object NotStarted : DBState {
        override fun start(db: PostgresDatabase) {
            state = Started
            db.startDatbase()
        }
    }

    private object Started : DBState {
        override fun stop(db: PostgresDatabase) {
            db.state = NotStarted
            db.stopDatabase()
        }

        override fun connection(db: PostgresDatabase): DataSource {
            return db.dataSource!!
        }

        override fun reset(db: PostgresDatabase) {
            db.resetSchema()
        }

    }

    @Language("PostgreSQL")
    private val truncateTablesSql = """
CREATE OR REPLACE FUNCTION truncate_tables() RETURNS void AS ${'$'}${'$'}
DECLARE
    statements CURSOR FOR
        SELECT tablename FROM pg_tables
        WHERE schemaname = 'public' AND tablename NOT LIKE 'flyway%';
BEGIN
    FOR stmt IN statements LOOP
        EXECUTE 'TRUNCATE TABLE ' || quote_ident(stmt.tablename) || ' CASCADE;';
    END LOOP;
END;
${'$'}${'$'} LANGUAGE plpgsql;
"""
}