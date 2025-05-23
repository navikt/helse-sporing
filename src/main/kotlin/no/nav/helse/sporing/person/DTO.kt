package no.nav.helse.sporing.person

import java.time.LocalDate
import java.util.*

data class PersonDTO(
    val fødselsnummer: String,
    val arbeidsgivere: List<ArbeidsgiverDTO>
)

data class ArbeidsgiverDTO(
    val organisasjonsnummer: String,
    val vedtaksperioder: List<VedtaksperiodeDTO>
)

enum class PeriodetypeDTO {
    GAP, FORLENGELSE, GAP_SISTE, FORLENGELSE_SISTE
}

data class VedtaksperiodeDTO(
    val id: UUID,
    val fom: LocalDate,
    val tom: LocalDate,
    val periodetype: PeriodetypeDTO,
    val forkastet: Boolean
)
