package no.nav.helse.sporing

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
import io.ktor.util.pipeline.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.helse.rapids_rivers.*
import no.nav.helse.sporing.DataSourceInitializer.Companion.causes
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import java.lang.Thread.sleep
import java.net.ConnectException
import java.time.LocalDateTime
import java.time.format.DateTimeParseException
import java.util.*
import javax.sql.DataSource
import kotlin.reflect.KClass

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

    val rapidsConnection = RapidApplication.Builder(RapidApplication.RapidApplicationConfig.fromEnv(env))
        .withKtorModule(ktorApi(repo))
        .build()
        .apply {
            register(dataSourceInitializer)
            Tilstandsendringer(this, repo)
        }
    rapidsConnection.start()
}

private class DataSourceInitializer(private val hikariConfig: HikariConfig) : RapidsConnection.StatusListener {
    private lateinit var dataSource: DataSource

    fun getDataSource(): DataSource {
        check(this::dataSource.isInitialized) { "The data source has not been initialized yet!" }
        return dataSource
    }

    override fun onStartup(rapidsConnection: RapidsConnection) {
        while (!initializeDataSource()) {
            log.info("Database is not available yet, trying again")
            sleep(250)
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

internal fun ktorApi(repo: TilstandsendringRepository): Application.() -> Unit {
    return {
        install(ContentNegotiation) { register(ContentType.Application.Json, JacksonConverter(objectMapper)) }
        requestResponseTracing(log)
        routing {
            tilstandsmaskinRoute("/tilstandsmaskin.json") { fordi, etter ->
                call.respond(OK, TilstandsendringerResponse(repo.tilstandsendringer(fordi, etter)))
            }
            tilstandsmaskinRoute("/tilstandsmaskin.dot") { fordi, etter ->
                call.respondText(ContentType.Text.Plain, OK) {
                    GraphvizFormatter.General.format(repo.tilstandsendringer(fordi, etter))
                }
            }
            vedtaksperiodeRoute("/tilstandsmaskin/{vedtaksperiodeId}.json") { vedtaksperiodeId ->
                call.respond(OK, TilstandsendringerResponse(repo.tilstandsendringer(vedtaksperiodeId)))
            }
            vedtaksperiodeRoute("/tilstandsmaskin/{vedtaksperiodeId}.dot") { vedtaksperiodeId ->
                call.respondText(ContentType.Text.Plain, OK) {
                    GraphvizFormatter.Specific.format(repo.tilstandsendringer(vedtaksperiodeId))
                }
            }
            tilstandsmaskinRoute("/") { fordi, etter ->
                call.respondText(ContentType.Text.Html, OK) {
                    getResourceAsText("/index.html")
                        .replace("{fordi}", "$fordi")
                        .replace("{etter}", "$etter")
                }
            }
            vedtaksperiodeRoute("/tilstandsmaskin/{vedtaksperiodeId}") { vedtaksperiodeId ->
                call.respondText(ContentType.Text.Html, OK) {
                    getResourceAsText("/vedtaksperiode.html")
                        .replace("{vedtaksperiodeId}", "$vedtaksperiodeId")
                }
            }
            static("public") {
                resources("public")
            }
        }
    }
}

private val re = Regex("[^A-Za-z0-9æøåÆØÅ_-]")
private fun String.alphaNumericalOnlyOrNull(): String? {
    return re.replace(this, "").takeIf(String::isNotEmpty)
}

private fun Routing.tilstandsmaskinRoute(uri: String, body: suspend PipelineContext<Unit, ApplicationCall>.(fordi: String?, etter: LocalDateTime?) -> Unit) {
    get(uri) {
        withContext(Dispatchers.IO) {
            val fordi = call.request.queryParameters["fordi"]?.takeIf(String::isNotEmpty)?.alphaNumericalOnlyOrNull()
            val etter = call.request.queryParameters["etter"]?.takeIf(String::isNotEmpty)?.let {
                try {
                    LocalDateTime.parse(it)
                } catch (err: DateTimeParseException) {
                    return@withContext call.respond(BadRequest, "Please use a valid LocalDateTime")
                }
            }
            body(this@get, fordi, etter)
        }
    }
}

private fun Routing.vedtaksperiodeRoute(uri: String, body: suspend PipelineContext<Unit, ApplicationCall>.(vedtaksperiodeId: UUID) -> Unit) {
    get(uri) {
        withContext(Dispatchers.IO) {
            val vedtaksperiodeId =
                try { call.parameters["vedtaksperiodeId"]?.let { UUID.fromString(it) }
            } catch (err: IllegalArgumentException) { return@withContext call.respond(BadRequest, "Please use a valid UUID") }
                    ?: return@withContext call.respond(BadRequest, "Please set vedtaksperiodeId in url")
            body(this@get, vedtaksperiodeId)
        }
    }
}

private fun getResourceAsText(path: String): String {
    return object {}.javaClass.getResource(path).readText()
}

internal class TilstandsendringerResponse(val tilstandsendringer: List<TilstandsendringDto>)
