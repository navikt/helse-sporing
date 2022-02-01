package no.nav.helse.sporing

import no.nav.helse.sporing.PersonendringerHtmlBuilder.Endring.Companion.sorter
import no.nav.helse.sporing.person.ArbeidsgiverDTO
import no.nav.helse.sporing.person.PeriodetypeDTO
import no.nav.helse.sporing.person.PersonDTO
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

internal class PersonendringerHtmlBuilder(person: PersonDTO, tilstandsendringer: List<PersonendringDto>) {
    private val endringer = mutableMapOf<UUID, Endring>()

    init {
        sySammen(person, tilstandsendringer.sortedBy { it.når })
    }

    private fun sySammen(person: PersonDTO, tilstandsendringer: List<PersonendringDto>) {
        val vedtaksperioder = mutableMapOf<UUID, Vedtaksperiode>()
        val forrigeTilstand = mutableMapOf<String, MutableMap<UUID, Vedtaksperiodeendring>>()

        tilstandsendringer.forEach { rad ->
            val orgnr = person.arbeidsgivere.firstOrNull { it.vedtaksperioder.any { it.id == rad.vedtaksperiodeId } }?.organisasjonsnummer ?: "UKJENT"
            val vedtaksperiode = vedtaksperioder.getOrPut(rad.vedtaksperiodeId) {
                val vedtaksperiodeDto = person.arbeidsgivere.first { it.organisasjonsnummer == orgnr }.vedtaksperioder.first { it.id == rad.vedtaksperiodeId }
                // TODO: verdiene fra vedtaksperiodeDto er teoretisk sett bare gyldige i alle siste versjon av personen. Vi har ikke sporing av
                // historiske verdier for disse feltene
                Vedtaksperiode(rad.vedtaksperiodeId, rad.når, vedtaksperiodeDto.fom, vedtaksperiodeDto.tom, vedtaksperiodeDto.periodetype)
            }
            val vedtaksperiodeendring = Vedtaksperiodeendring(vedtaksperiode, rad.når, rad.tilTilstand)
            endringer.getOrPut(rad.meldingId) { Endring(rad.meldingId, rad.navn, rad.opprettet, forrigeTilstand.mapValues { it.value.values.toList() }.toMap()) }
                .add(person, vedtaksperiodeendring)
            forrigeTilstand.getOrPut(orgnr) { mutableMapOf() }[rad.vedtaksperiodeId] = vedtaksperiodeendring
        }
    }

    internal fun render(): String {
        val sb = StringBuilder()
        sb.appendLine("<div class='tabell tidslinje'>")
        endringer.values.sorter().forEach { it.renderHtml(sb) }
        sb.appendLine("</div>")
        return sb.toString()
    }

    internal class Endring(
        private val meldingId: UUID,
        private val navn: String,
        private val opprettet: LocalDateTime,
        vedtaksperioder: Map<String, List<Vedtaksperiodeendring>>
    ) {
        private val vedtaksperioder = vedtaksperioder.mapValues { it.value.toMutableList() }.toMutableMap()

        private val egneEndringer = mutableListOf<Vedtaksperiodeendring>()
        private val endringstidspunkt get() = Vedtaksperiodeendring.eldste(egneEndringer)

        internal fun add(person: PersonDTO, vedtaksperiodeendring: Vedtaksperiodeendring) = apply {
            val arbeidsgiver = person.arbeidsgivere.first { it.vedtaksperioder.any { vedtaksperiodeendring.gjelder(it.id) } }
            opprettNyEllerErstatt(arbeidsgiver.organisasjonsnummer, vedtaksperiodeendring.endretNå())
            Vedtaksperiodeendring.sorter(arbeidsgiver, this.vedtaksperioder.getValue(arbeidsgiver.organisasjonsnummer))
        }

        private fun opprettNyEllerErstatt(orgnr: String, vedtaksperiodeendring: Vedtaksperiodeendring) {
            egneEndringer.add(vedtaksperiodeendring)
            vedtaksperioder.getOrPut(orgnr) { mutableListOf() }
            val agperioder = vedtaksperioder.getValue(orgnr)
            val index = agperioder.indexOf(vedtaksperiodeendring)
            if (index == -1) agperioder.add(vedtaksperiodeendring)
            else agperioder[index] = vedtaksperiodeendring
        }

        internal fun renderHtml(sb: StringBuilder) {
            sb.appendLine("<div class='rad'>")
            sb.appendLine("<div class='celle hendelse'><span title='$meldingId opprettet $opprettet'>${fintNavn(navn)}</span></div>")
            sb.appendLine("<div class='celle arbeidsgivere'><div class='tabell'>")
            vedtaksperioder.forEach { (orgnr, perioder) ->
                sb.appendLine("<div class='rad'>")
                sb.appendLine("<div class='celle arbeidsgiver'>$orgnr</div>")
                perioder.forEach { it.renderHtml(sb) }
                sb.appendLine("</div>")
            }
            sb.appendLine("</div></div>")
            sb.appendLine("</div>")
        }

        override fun toString() = "$navn ($meldingId) @ $opprettet"

        internal companion object {
            fun Collection<Endring>.sorter() = sortedBy { it.endringstidspunkt }
            private fun fintNavn(navn: String) = when (navn) {
                "ArbeidsavklaringspengerDagpengerDødsinfoForeldrepengerInstitusjonsoppholdOmsorgspengerOpplæringspengerPleiepengerSykepengehistorikk" -> "Ytelser (med Sykepengehistorikk)"
                "Arbeidsforholdv2InntekterforsammenligningsgrunnlagInntekterforsykepengegrunnlagMedlemskap" -> "Vilkårsgrunnlag"
                "ArbeidsavklaringspengerDagpengerDødsinfoForeldrepengerInstitusjonsoppholdOmsorgspengerOpplæringspengerPleiepenger" -> "Ytelser (uten Sykepengehistorikk)"
                else -> navn.split(' ', '_').joinToString(separator = " ")
            }
        }
    }

    internal class Vedtaksperiode(
        private val id: UUID,
        private val opprettet: LocalDateTime,
        private val fom: LocalDate,
        private val tom: LocalDate,
        private val periodetype: PeriodetypeDTO
    ) {
        internal fun renderHtml(sb: StringBuilder, endretNå: Boolean, tilTilstand: String, når: LocalDateTime) {
            val classes = mutableListOf<String>()

            when (periodetype) {
                PeriodetypeDTO.GAP -> classes.add("gap")
                PeriodetypeDTO.GAP_SISTE -> { classes.add("gap"); classes.add("siste") }
                PeriodetypeDTO.FORLENGELSE_SISTE -> { classes.add("forlengelse"); classes.add("siste") }
                PeriodetypeDTO.FORLENGELSE -> classes.add("forlengelse")
            }

            if (endretNå) classes.add("endret")
            else classes.add("uendret")

            sb.appendLine("<div class='celle vedtaksperiode ${classes.joinToString(separator = " ")}'><span title='Endret $når | Periode $fom til $tom'>${tilTilstand.split('_').map(String::lowercase).map {
                it.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            }.joinToString(separator = " ")}</span></div>")
        }

        override fun equals(other: Any?) = other is Vedtaksperiode && other.id == this.id
        fun equals(other: UUID) = other == this.id

        override fun toString() = "$id @ $opprettet"
    }

    internal class Vedtaksperiodeendring(
        private val vedtaksperiode: Vedtaksperiode,
        private val når: LocalDateTime,
        private val tilstand: String,
        private val endretNå: Boolean = false
    ) {
        override fun equals(other: Any?) = other is Vedtaksperiodeendring && other.vedtaksperiode == this.vedtaksperiode
        fun gjelder(vedtaksperiodeId: UUID) = this.vedtaksperiode.equals(vedtaksperiodeId)

        internal fun renderHtml(sb: StringBuilder) {
            vedtaksperiode.renderHtml(sb, endretNå, tilstand, når)
        }

        fun endretNå() = Vedtaksperiodeendring(vedtaksperiode, når, tilstand, true)

        internal companion object {
            internal fun sorter(arbeidsgiver: ArbeidsgiverDTO, liste: MutableList<Vedtaksperiodeendring>) {
                liste.sortBy { endring ->
                    arbeidsgiver.vedtaksperioder.indexOfFirst { vedtaksperiode ->
                        endring.vedtaksperiode.equals(vedtaksperiode.id)
                    }
                }
            }

            fun eldste(liste: List<Vedtaksperiodeendring>) = liste.minOf { it.når }
        }
    }
}
