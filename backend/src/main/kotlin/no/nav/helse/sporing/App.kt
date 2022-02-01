package no.nav.helse.sporing

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.content.*
import io.ktor.jackson.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeParseException
import java.util.*

private val objectMapper = jacksonObjectMapper()
    .registerModule(JavaTimeModule())
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

fun main() {
    val env = System.getenv()
    val isLocal = env.getOrDefault("localDevelopment", "false").toBoolean()
    val app = if (isLocal) LocalApp() else ProductionApp(env)
    app.start()
}

internal fun ktorApi(repo: TilstandsendringRepository, spleisClient: SpleisClient): Application.() -> Unit {
    return {
        install(ContentNegotiation) { register(ContentType.Application.Json, JacksonConverter(objectMapper)) }
        requestResponseTracing(log)
        routing {
            tilstandsmaskinRoute("/tilstandsmaskin.json") { fordi, etter, ignorerTilstand, ignorerFordi ->
                call.respond(OK, TilstandsendringerResponse(repo.tilstandsendringer(fordi, etter, ignorerTilstand, ignorerFordi)))
            }
            tilstandsmaskinRoute("/tilstandsmaskin.dot") { fordi, etter, ignorerTilstand, ignorerFordi ->
                call.respondText(ContentType.Text.Plain, OK) {
                    GraphvizFormatter.General.format(repo.tilstandsendringer(fordi, etter, ignorerTilstand, ignorerFordi))
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
            tilstandsmaskinRoute("/") { fordi, etter, ignorerTilstand, ignorerFordi  ->
                call.respondText(ContentType.Text.Html, OK) {
                    getResourceAsText("/index.html")
                        .replace("{fordi}", fordi.joinToString(prefix = "?", separator = "&") { "fordi=$it" })
                        .replace("{ignorerTilstand}", ignorerTilstand.joinToString(prefix = "&", separator = "&") { "ignorerTilstand=$it" })
                        .replace("{ignorerFordi}", ignorerFordi.joinToString(prefix = "&", separator = "&") { "ignorerFordi=$it" })
                        .replace("{etter}", "&etter=${etter?.toString() ?: ""}")
                }
            }
            vedtaksperiodeRoute("/tilstandsmaskin/{vedtaksperiodeId}") { vedtaksperiodeId ->
                call.respondText(ContentType.Text.Html, OK) {
                    getResourceAsText("/vedtaksperiode.html")
                        .replace("{vedtaksperiodeId}", "$vedtaksperiodeId")
                }
            }
            get("/person/{pid}") {
                withContext(Dispatchers.IO) {
                    val pid = call.parameters["pid"]?.let(::numericalOnlyOrNull) ?: return@withContext call.respond(BadRequest, "Please set pid in url (numbers only)")
                    val vedtaksperioder = spleisClient.hentVedtaksperioder(pid)
                    call.respond(OK, PersonendringerResponse(repo.personendringer(vedtaksperioder.arbeidsgivere.flatMap { it.vedtaksperioder.map { it.id } })))
                }
            }
            static("public") {
                resources("public")
            }
        }
    }
}

private val re = Regex("[^A-Za-z0-9æøåÆØÅ_-]")
private fun alphaNumericalOnlyOrNull(str: String): String? {
    return re.replace(str, "").takeIf(String::isNotEmpty)
}

private val reNumerical = Regex("[^0-9]")
private fun numericalOnlyOrNull(str: String): String? {
    return reNumerical.replace(str, "").takeIf(String::isNotEmpty)
}

private fun Routing.tilstandsmaskinRoute(uri: String, body: suspend PipelineContext<Unit, ApplicationCall>.(fordi: List<String>, etter: LocalDateTime?, ignorer: List<String>, ignorerFordi: List<String>) -> Unit) {
    get(uri) {
        withContext(Dispatchers.IO) {
            val fordi = call.queryParam("fordi").mapNotNull(::alphaNumericalOnlyOrNull)
            val ignorerTilstand = call.queryParam("ignorerTilstand").mapNotNull(::alphaNumericalOnlyOrNull)
            val ignorerFordi = call.queryParam("ignorerFordi").mapNotNull(::alphaNumericalOnlyOrNull)
            val etter = call.queryParam("etter").firstOrNull()?.let {
                try {
                    LocalDateTime.parse(it)
                } catch (err: DateTimeParseException) {
                    return@withContext call.respond(BadRequest, "Please use a valid LocalDateTime")
                }
            }
            body(this@get, fordi, etter, ignorerTilstand, ignorerFordi)
        }
    }
}

private fun ApplicationCall.queryParam(name: String): List<String> =
    request.queryParameters.getAll(name)?.filter(String::isNotBlank) ?: emptyList()

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
internal class PersonendringerResponse(val tilstandsendringer: List<PersonendringDto>)
