package no.nav.helse.sporing

import PostgresDatabase
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance

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