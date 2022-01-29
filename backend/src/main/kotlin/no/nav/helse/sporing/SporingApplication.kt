package no.nav.helse.sporing

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import java.net.ConnectException
import java.time.LocalDateTime
import java.util.*
import javax.sql.DataSource
import kotlin.reflect.KClass

internal interface SporingApplication {
    fun start()
    fun stop()
}

private val log = LoggerFactory.getLogger("no.nav.helse.sporing.App")

internal class ProductionApp(private val env: Map<String, String>): SporingApplication {
    private val hikariConfig = HikariConfig().apply {
        jdbcUrl = String.format(
            "jdbc:postgresql://%s:%s/%s?user=%s",
            env.getValue("NAIS_DATABASE_SPORING_SPORING_HOST"),
            env.getValue("NAIS_DATABASE_SPORING_SPORING_PORT"),
            env.getValue("NAIS_DATABASE_SPORING_SPORING_DATABASE"),
            env.getValue("NAIS_DATABASE_SPORING_SPORING_USERNAME")
        )
        password = env.getValue("NAIS_DATABASE_SPORING_SPORING_PASSWORD")
        maximumPoolSize = 3
        minimumIdle = 1
        idleTimeout = 10001
        connectionTimeout = 1000
        maxLifetime = 30001
    }

    private val dataSourceInitializer = DataSourceInitializer(hikariConfig)
    private val repo = PostgresRepository(dataSourceInitializer::getDataSource)

    private val rapidsConnection = RapidApplication.Builder(RapidApplication.RapidApplicationConfig.fromEnv(env))
        .withKtorModule(ktorApi(repo))
        .build()
        .apply {
            register(dataSourceInitializer)
            Tilstandsendringer(this, repo)
            Forkastinger(this, repo)
        }

    override fun start() = rapidsConnection.start()
    override fun stop() = rapidsConnection.stop()

    private class DataSourceInitializer(private val hikariConfig: HikariConfig) : RapidsConnection.StatusListener {
        private lateinit var dataSource: DataSource

        fun getDataSource(): DataSource {
            check(this::dataSource.isInitialized) { "The data source has not been initialized yet!" }
            return dataSource
        }

        override fun onStartup(rapidsConnection: RapidsConnection) {
            while (!initializeDataSource()) {
                log.info("Database is not available yet, trying again")
                Thread.sleep(250)
            }
            migrate(dataSource)
        }

        private fun initializeDataSource(): Boolean {
            try {
                dataSource = HikariDataSource(hikariConfig)
                return true
            } catch (err: Exception) {
                err.allow(ConnectException::class)
            }
            return false
        }

        private companion object {
            fun Throwable.allow(clazz: KClass<out Throwable>) {
                if (causes().any { clazz.isInstance(it) }) return
                throw this
            }

            fun Throwable.causes(): List<Throwable> {
                return mutableListOf<Throwable>(this).apply {
                    var nextError: Throwable? = cause
                    while (nextError != null) {
                        add(nextError)
                        nextError = nextError.cause
                    }
                }
            }
            fun migrate(dataSource: DataSource) {
                Flyway.configure().dataSource(dataSource).load().migrate()
            }
        }
    }
}

internal class LocalApp(private val serverPort: Int = 4000): SporingApplication {
    private val repository = FilesystemRepository("/tilstandsmaskin.json")
    private val server: NettyApplicationEngine
    private val environment = applicationEngineEnvironment {
        connector { port = serverPort }
        module(ktorApi(repository))
    }

    init {
        server = embeddedServer(Netty, environment)
    }

    override fun start() {
        server.start(wait = false)
    }
    override fun stop() = server.stop(0, 0)

    internal class FilesystemRepository(private val file: String) : TilstandsendringRepository {
        private val objectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

        private val testdata: List<TilstandsendringDto> by lazy {
            objectMapper.readValue<TilstandsendringerResponse>(getResourceAsText(file)).tilstandsendringer
        }

        override fun lagre(meldingId: UUID, vedtaksperiodeId: UUID, fraTilstand: String, tilTilstand: String, fordi: String, når: LocalDateTime, årsak: Årsak) {
            throw NotImplementedError()
        }

        override fun tilstandsendringer(fordi: List<String>, etter: LocalDateTime?, ignorerTilstand: List<String>, ignorerFordi: List<String>): List<TilstandsendringDto> {
            return testdata
                .filter { fordi.isEmpty() || it.fordi.lowercase() in fordi.map(String::lowercase) }
                .filter { it.fordi.lowercase() !in ignorerFordi.map(String::lowercase) }
                .filter { it.tilTilstand.lowercase() !in ignorerTilstand.map(String::lowercase) }
                .filter { etter == null || it.sistegang >= etter }
        }

        override fun tilstandsendringer(vedtaksperiodeId: UUID): List<TilstandsendringDto> {
            return testdata
        }

        private fun getResourceAsText(path: String): String {
            return object {}.javaClass.getResource(path).readText()
        }
    }

}

