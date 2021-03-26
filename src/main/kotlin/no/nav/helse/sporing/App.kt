package no.nav.helse.sporing

import ch.qos.logback.core.status.StatusListener
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.content.*
import io.ktor.jackson.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.helse.rapids_rivers.*
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.Thread.sleep
import java.net.ConnectException
import java.time.LocalDateTime
import java.util.*
import javax.sql.DataSource

private val objectMapper = jacksonObjectMapper()
    .registerModule(JavaTimeModule())
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

private val log = LoggerFactory.getLogger("no.nav.helse.sporing.App")

fun main() {
    val env = System.getenv()
    val hikariConfig = HikariConfig().apply {
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

    val dataSourceInitializer = DataSourceInitializer(hikariConfig)
    val repo = PostgresRepository(dataSourceInitializer::getDataSource)

    RapidApplication.Builder(RapidApplication.RapidApplicationConfig.fromEnv(env))
        .withKtorModule(ktorApi(repo))
        .build()
        .apply {
            register(object : RapidsConnection.StatusListener {
                override fun onStartup(rapidsConnection: RapidsConnection) {
                    while (!dataSourceInitializer.initializeDataSource()) {
                        log.info("Database is not available yet, trying again", dataSourceInitializer.getError())
                        sleep(250)
                    }
                }
            })
            register(repo)
            Tilstandsendringer(this, repo)
        }
        .start()
}

private class DataSourceInitializer(private val hikariConfig: HikariConfig) {
    private var dataSource: DataSource? = null
    private var lastError: Exception? = null

    fun getDataSource(): DataSource {
        return requireNotNull(dataSource) { "The data source has not been initialized yet!" }
    }

    fun getError() = requireNotNull(lastError)

    fun initializeDataSource(): Boolean {
        try {
            lastError = null
            dataSource = HikariDataSource(hikariConfig)
            return true
        } catch (err: Exception) {
            lastError = err
            val causes = mutableListOf<Throwable>(err)
            var nextError: Throwable? = err.cause
            while (nextError != null) {
                causes.add(nextError)
                nextError = nextError.cause
            }
            if (causes.none { it is ConnectException }) throw err
        }
        return false
    }
}

internal fun ktorApi(repo: PostgresRepository): Application.() -> Unit {
    return {
        install(ContentNegotiation) { register(ContentType.Application.Json, JacksonConverter(objectMapper)) }
        requestResponseTracing(log)
        routing {
            get("/tilstandsmaskin.json") {
                withContext(Dispatchers.IO) {
                    call.respond(OK, TilstandsendringerResponse(repo.tilstandsendringer()))
                }
            }
            get("/tilstandsmaskin/{vedtaksperiodeId}.json") {
                withContext(Dispatchers.IO) {
                    val vedtaksperiodeId = call.vedtaksperiode() ?: return@withContext
                    call.respond(OK, TilstandsendringerResponse(repo.tilstandsendringer(vedtaksperiodeId)))
                }
            }
            get("/") {
                withContext(Dispatchers.IO) {
                    call.respondText(ContentType.Text.Html, OK) { getResourceAsText("/index.html") }
                }
            }
            get("/tilstandsmaskin/{vedtaksperiodeId}") {
                withContext(Dispatchers.IO) {
                    val vedtaksperiodeId = call.vedtaksperiode() ?: return@withContext
                    call.respondText(ContentType.Text.Html, OK) {
                        getResourceAsText("/vedtaksperiode.html")
                            .replace("{vedtaksperiodeId}", "$vedtaksperiodeId")
                    }
                }
            }
            static("public") {
                resources("public")
            }
        }
    }
}

private suspend fun ApplicationCall.vedtaksperiode(): UUID? {
    val vedtaksperiodeId = try {
        parameters["vedtaksperiodeId"]?.let { UUID.fromString(it) }
    } catch (err: IllegalArgumentException) {
        respond(BadRequest, "Please use a valid UUID")
        return null
    }

    if (vedtaksperiodeId == null) respond(BadRequest, "Please set vedtaksperiodeId in url")
    return vedtaksperiodeId
}

private fun getResourceAsText(path: String): String {
    return object {}.javaClass.getResource(path).readText()
}

internal class TilstandsendringerResponse(val tilstandsendringer: List<PostgresRepository.TilstandsendringDto>)
