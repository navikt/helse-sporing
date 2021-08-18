package no.nav.helse.sporing

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal class GraphvizFormatter private constructor(private val transitionFormatter: TransitionFormatter) {

    internal companion object {
        private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM yyyy HH:mm:ss.SSS")
        internal val Specific = GraphvizFormatter(TransitionFormatter { sb: StringBuilder, index: Int, eventFormatter: Formatter, fraTilstand: String, tilTilstand: String, fordi: String, når: LocalDateTime ->
            sb
                .append("\t")
                .append(fraTilstand)
                .append(" -> ")
                .append(tilTilstand)
                .append(" [")
                .append("label=\"")
                .append("#${index + 1} ")
                .append(eventFormatter.format(fordi))
                .append(" (")
                .append(når.format(dateFormatter))
                .append(")")
                .append("\"")
                .appendLine("];")
        })

        internal val General = GraphvizFormatter(TransitionFormatter { sb: StringBuilder, _: Int, eventFormatter: Formatter, fraTilstand: String, tilTilstand: String, fordi: String, _: LocalDateTime ->
            sb
                .append("\t")
                .append(fraTilstand)
                .append(" -> ")
                .append(tilTilstand)
                .append(" [")
                .append("label=\"")
                .append(eventFormatter.format(fordi))
                .append("\"")
                .appendLine("];")
        })
    }
    private val eventFormatter = EventFormatter()
    private val edgeFormatter = EdgeFormatter()

    private val clusters = listOf(
        // blue states
        Cluster("blue", setOf(
            "MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE", "MOTTATT_SYKMELDING_FERDIG_FORLENGELSE", "AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE",
            "AVVENTER_SØKNAD_UFERDIG_FORLENGELSE", "AVVENTER_UFERDIG_FORLENGELSE", "AVVENTER_SØKNAD_FERDIG_FORLENGELSE",
            "AVVENTER_INNTEKTSMELDING_FERDIG_FORLENGELSE"
        )),
        // green states
        Cluster("green", setOf(
            "MOTTATT_SYKMELDING_FERDIG_GAP", "MOTTATT_SYKMELDING_UFERDIG_GAP", "AVVENTER_SØKNAD_FERDIG_GAP",
            "AVVENTER_SØKNAD_UFERDIG_GAP", "AVVENTER_INNTEKTSMELDING_UFERDIG_GAP", "AVVENTER_GAP", "AVVENTER_INNTEKTSMELDING_FERDIG_GAP",
            "AVVENTER_UFERDIG_GAP", "AVVENTER_VILKÅRSPRØVING_GAP",
            "AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK_FERDIG_GAP",
            "AVVENTER_ARBEIDSGIVERSØKNAD_UFERDIG_GAP",
            "AVVENTER_ARBEIDSGIVERSØKNAD_FERDIG_GAP"
        )),
        Cluster("orange", setOf(
            "AVVENTER_HISTORIKK", "AVVENTER_SIMULERING", "AVVENTER_GODKJENNING", "AVVENTER_ARBEIDSGIVERE", "TIL_UTBETALING"
        )),
        Cluster("yellow", setOf(
            "AVSLUTTET", "TIL_INFOTRYGD", "AVSLUTTET_UTEN_UTBETALING", "AVSLUTTET_UTEN_UTBETALING_MED_INNTEKTSMELDING",
            "UTEN_UTBETALING_MED_INNTEKTSMELDING_UFERDIG_FORLENGELSE",
            "UTEN_UTBETALING_MED_INNTEKTSMELDING_UFERDIG_GAP"
        ))
    )

    internal fun format(tilstandsendringer: List<TilstandsendringDto>): String {
        val sb = StringBuilder()
        sb.appendLine("digraph {")

        val edgesWithoutCluster = mutableListOf<Edge>()
        clusters.forEach { it.clear() }
        tilstandsendringer
            .flatMap { listOf(it.fraTilstand, it.tilTilstand) }
            .map(::Edge)
            .distinct()
            .onEach { edge ->
                if (!clusters.any { it.take(edge) }) edgesWithoutCluster.add(edge)
            }

        clusters.onEachIndexed { index, cluster -> cluster.format(sb, index, edgeFormatter) }
        edgesWithoutCluster.onEach { it.format(sb, edgeFormatter) }

        tilstandsendringer.forEachIndexed { index, dto ->
            transitionFormatter.format(sb, index, eventFormatter, dto.fraTilstand, dto.tilTilstand, dto.fordi, dto.sistegang)
        }
        sb.appendLine("}")
        return sb.toString()
    }

    private class Edge(private val name: String) {
        internal fun format(sb: StringBuilder, formatter: Formatter) {
            sb
                .append("\t\t")
                .appendLine(formatter.format(name))
        }

        internal fun within(states: Set<String>) = name in states
        override fun hashCode() = name.hashCode()
        override fun equals(other: Any?) = other is Edge && other.name == this.name
    }

    private class Cluster(private val color: String, private val states: Set<String>) {
        private val edges = mutableSetOf<Edge>()

        internal fun clear() {
            edges.clear()
        }

        internal fun format(sb: StringBuilder, index: Int, formatter: Formatter) {
            sb.append("\t")
                .append("subgraph cluster_")
                .append(index + 1)
                .appendLine(" {")
                .appendLine("\t\tcolor=$color;")
                .appendLine("\t\trankdir=\"LR\";")
                .appendLine()

            edges.forEach { it.format(sb, formatter) }
            sb.appendLine("\t}")
        }

        internal fun take(edge: Edge): Boolean {
            if (edge !in this) return false
            edges.add(edge)
            return true
        }
        internal operator fun contains(edge: Edge) = edge.within(states)
    }

    private class EdgeFormatter : Formatter {

        private val String.humanReadable get() = this
            .split("_")
            .map(String::lowercase)
            .joinToString(separator = " ", transform = { it.replaceFirstChar(Char::uppercase) })

        private val defaultEdgeFormatter = Formatter { edge: String ->
            StringBuilder()
                .append(edge)
                .append(" [label=\"")
                .append(edge.humanReadable)
                .append("\"")
                .append("];")
                .toString()
        }

        private val endFormatter = Formatter { edge: String ->
            StringBuilder()
                .append(edge)
                .append(" [")
                .append("shape=Mdiamond,")
                .append("label=\"")
                .append(edge.humanReadable)
                .append("\"")
                .append("];")
                .toString()
        }

        private val edgeFormatters = mapOf<String, Formatter>(
            "TIL_INFOTRYGD" to endFormatter,
            "START" to endFormatter,
            "AVSLUTTET" to endFormatter,
            "AVSLUTTET_UTEN_UTBETALING" to endFormatter,
            "AVSLUTTET_UTEN_UTBETALING_MED_INNTEKTSMELDING" to endFormatter
        )

        override fun format(name: String): String {
            return (edgeFormatters[name] ?: defaultEdgeFormatter).format(name)
        }
    }

    private class EventFormatter : Formatter {
        private val events = mapOf(
            "ArbeidsavklaringspengerDagpengerDødsinfoForeldrepengerInstitusjonsoppholdOmsorgspengerOpplæringspengerPleiepengerSykepengehistorikk" to "Ytelser (med sykepengehistorikk)",
            "ArbeidsavklaringspengerDagpengerDødsinfoForeldrepengerInstitusjonsoppholdOmsorgspengerOpplæringspengerPleiepenger" to "Ytelser (uten sykepengehistorikk)",
            "InntekterforsammenligningsgrunnlagMedlemskapOpptjening" to "Vilkårsgrunnlag"
        )
        override fun format(name: String): String {
            return events[name] ?: name
        }
    }

    internal fun interface Formatter {
        fun format(name: String): String
    }

    internal fun interface TransitionFormatter {
        fun format(sb: StringBuilder, index: Int, eventFormatter: Formatter, fraTilstand: String, tilTilstand: String, fordi: String, når: LocalDateTime)
    }
}