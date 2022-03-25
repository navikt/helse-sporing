package no.nav.helse.sporing

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalDateTime
import java.util.*
import javax.sql.DataSource

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
        maximumPoolSize = 1
        connectionTimeout = Duration.ofSeconds(15).toMillis()
        maxLifetime = Duration.ofMinutes(30).toMillis()
        initializationFailTimeout = Duration.ofMinutes(1).toMillis()
    }

    private val dataSourceInitializer = DataSourceInitializer(hikariConfig)
    private val repo = PostgresRepository(dataSourceInitializer::dataSource)

    private val azureClient = AzureClient(
        tokenEndpoint = env.getValue("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
        clientId = env.getValue("AZURE_APP_CLIENT_ID"),
        clientSecret = env.getValue("AZURE_APP_CLIENT_SECRET")
    )

    private val apiUrl = env["SPLEIS_API_URL"] ?: "http://spleis-api.tbd.svc.cluster.local"
    private val spleisClient = SpleisClient(apiUrl, azureClient, env.getValue("SPLEIS_SCOPE"))

    private val rapidsConnection = RapidApplication.Builder(RapidApplication.RapidApplicationConfig.fromEnv(env))
        .withKtorModule(ktorApi(repo, spleisClient))
        .build()
        .apply {
            register(dataSourceInitializer)
            Tilstandsendringer(this, repo)
            Forkastinger(this, repo)
        }

    override fun start() = rapidsConnection.start()
    override fun stop() = rapidsConnection.stop()

    private class DataSourceInitializer(private val hikariConfig: HikariConfig) : RapidsConnection.StatusListener {
        private val dataSource: DataSource by lazy {
            HikariDataSource(hikariConfig)
        }

        fun dataSource() = dataSource

        override fun onStartup(rapidsConnection: RapidsConnection) {
            migrate(dataSource)
        }

        private companion object {
            fun migrate(dataSource: DataSource) {
                Flyway.configure()
                    .dataSource(dataSource)
                    .load()
                    .migrate()
            }
        }
    }
}

internal class LocalApp(private val serverPort: Int = 4000): SporingApplication {
    private val repository = FilesystemRepository("/tilstandsmaskin.json")
    private val server: CIOApplicationEngine
    private val environment = applicationEngineEnvironment {
        connector { port = serverPort }
        module(ktorApi(repository, SpleisClient("http://foo.bar", AzureClient("http://bar.foo", "no", "thanks"), "scope")))
    }

    init {
        server = embeddedServer(CIO, environment)
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

        override fun personendringer(vedtaksperioder: List<UUID>): List<PersonendringDto> {
            throw NotImplementedError()
        }

        private fun getResourceAsText(path: String): String {
            return object {}.javaClass.getResource(path).readText()
        }
    }

}

