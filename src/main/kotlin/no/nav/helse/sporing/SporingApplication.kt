package no.nav.helse.sporing

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.azure.createAzureTokenClientFromEnvironment
import com.github.navikt.tbd_libs.naisful.naisApp
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.micrometer.core.instrument.Clock
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.prometheus.metrics.model.registry.PrometheusRegistry
import no.nav.helse.rapids_rivers.RapidApplication
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import java.time.Duration
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
        maximumPoolSize = 2
        connectionTimeout = Duration.ofSeconds(15).toMillis()
        maxLifetime = Duration.ofMinutes(30).toMillis()
        initializationFailTimeout = Duration.ofMinutes(1).toMillis()
    }

    private val dataSourceInitializer = DataSourceInitializer(hikariConfig)
    private val repo = PostgresRepository(dataSourceInitializer::dataSource)

    private val azureClient = createAzureTokenClientFromEnvironment(env)

    private val apiUrl = env["SPLEIS_API_URL"] ?: "http://spleis-api.tbd.svc.cluster.local"
    private val spleisClient = SpleisClient(apiUrl, azureClient, env.getValue("SPLEIS_SCOPE"))
    private val meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT, PrometheusRegistry.defaultRegistry, Clock.SYSTEM)
    private val rapidsConnection = RapidApplication.create(
        env = env,
        meterRegistry = meterRegistry,
        builder = {
            withKtor { preStopHook, rapid ->
                naisApp(
                    meterRegistry = meterRegistry,
                    objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS),
                    applicationLogger = log,
                    callLogger = LoggerFactory.getLogger("no.nav.helse.sporing.CallLogging"),
                    naisEndpoints = com.github.navikt.tbd_libs.naisful.NaisEndpoints.Default,
                    aliveCheck = rapid::isReady,
                    readyCheck = rapid::isReady,
                    preStopHook = preStopHook::handlePreStopRequest,
                    applicationModule = ktorApi(repo, spleisClient)
                )
            }
        }
    )
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

