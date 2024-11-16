package no.nav.helse.sporing

import com.github.navikt.tbd_libs.test_support.TestDataSource
import databaseContainer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

internal abstract class AbstractDatabaseTest {

    private lateinit var testDataSource: TestDataSource
    protected val dataSource get() = testDataSource.ds
    protected lateinit var repository: PostgresRepository

    @BeforeEach
    fun setup() {
        testDataSource = databaseContainer.nyTilkobling()
        repository = PostgresRepository(testDataSource::ds)
    }

    @AfterEach
    fun cleanup() {
        databaseContainer.droppTilkobling(testDataSource)
    }
}