package no.nav.helse.sporing

import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import java.time.LocalDateTime
import java.util.*

internal class FilesystemRepository(
    private val file: String,
) : TilstandsendringRepository {
    private val objectMapper = jacksonObjectMapper()

    private val testdata: List<TilstandsendringDto> by lazy {
        objectMapper.readValue<TilstandsendringerResponse>(getResourceAsText(file)).tilstandsendringer
    }

    override fun lagre(
        meldingId: UUID,
        vedtaksperiodeId: UUID,
        fraTilstand: String,
        tilTilstand: String,
        fordi: String,
        når: LocalDateTime,
        årsak: Årsak,
    ): Unit = throw NotImplementedError()

    override fun tilstandsendringer(
        bareUnike: Boolean,
        fordi: List<String>,
        etter: LocalDateTime?,
        ignorerTilstand: List<String>,
        ignorerFordi: List<String>,
    ): List<TilstandsendringDto> =
        testdata
            .filter { fordi.isEmpty() || it.fordi.lowercase() in fordi.map(String::lowercase) }
            .filter { it.fordi.lowercase() !in ignorerFordi.map(String::lowercase) }
            .filter { it.tilTilstand.lowercase() !in ignorerTilstand.map(String::lowercase) }
            .filter { etter == null || it.sistegang >= etter }

    override fun tilstandsendringer(vedtaksperiodeId: UUID): List<TilstandsendringDto> = testdata

    override fun personendringer(vedtaksperioder: List<UUID>): List<PersonendringDto> = throw NotImplementedError()

    private fun getResourceAsText(path: String): String = object {}.javaClass.getResource(path).readText()
}
