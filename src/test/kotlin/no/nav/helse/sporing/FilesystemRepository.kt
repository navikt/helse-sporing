package no.nav.helse.sporing

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.time.LocalDateTime
import java.util.*

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
