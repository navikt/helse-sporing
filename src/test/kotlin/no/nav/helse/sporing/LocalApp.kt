package no.nav.helse.sporing

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.navikt.tbd_libs.azure.AzureToken
import com.github.navikt.tbd_libs.azure.AzureTokenProvider
import com.github.navikt.tbd_libs.result_object.Result
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import java.time.LocalDateTime
import java.util.*
import kotlin.reflect.jvm.internal.impl.descriptors.Visibilities.Local

fun main() {
    LocalApp().start()
}

internal class LocalApp(private val serverPort: Int = 4000): SporingApplication {
    private val repository = FilesystemRepository("/tilstandsmaskin.json")
    private val server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private val environment = applicationEnvironment {}

    init {
        server = embeddedServer(CIO,
            environment = environment,
            configure = { connector { port = serverPort } }
        ) {
            ktorApi(repository, SpleisClient("http://foo.bar", object : AzureTokenProvider {
                override fun bearerToken(scope: String): Result<AzureToken> {
                    throw NotImplementedError("ikke implementert")
                }

                override fun onBehalfOfToken(scope: String, token: String): Result<AzureToken> {
                    throw NotImplementedError("ikke implementert")
                }
            }, "scope"))
        }
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