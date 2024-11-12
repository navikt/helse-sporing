package no.nav.helse.sporing

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.sporing.Event.eventName
import org.slf4j.LoggerFactory
import java.util.*

internal class Forkastinger(rapidsConnection: RapidsConnection, repository: TilstandsendringRepository) {
    private companion object {
        private val log = LoggerFactory.getLogger(Forkastinger::class.java)
        private val sikkerLog = LoggerFactory.getLogger("tjenestekall")
        private val søppelbøttetilstand = "Søppelbøtte"
    }
    init {
        River(rapidsConnection)
            .validate {
                it.demandValue("@event_name", "vedtaksperiode_forkastet")
                it.requireKey("@id", "@forårsaket_av.id", "@forårsaket_av.event_name", "vedtaksperiodeId", "tilstand")
                it.interestedIn("@forårsaket_av.behov")
                it.require("@opprettet", JsonNode::asLocalDateTime)
                it.require("@forårsaket_av.opprettet", JsonNode::asLocalDateTime)
            }
            .onError { problems, _, _ ->
                log.error("Forstod ikke vedtaksperiode_forkastet (Se sikker logg for detaljer)")
                sikkerLog.error("Forstod ikke vedtaksperiode_forkastet:\n${problems.toExtendedReport()}")
            }
            .onSuccess { message, _, _, _ ->
                val gjeldendeTilstand = message["tilstand"].asText()
                val eventName = eventName(message)
                val vedtaksperiodeId = UUID.fromString(message["vedtaksperiodeId"].asText())
                val årsak = Årsak(UUID.fromString(message["@forårsaket_av.id"].asText()), eventName, message["@forårsaket_av.opprettet"].asLocalDateTime())
                log.info(
                    "lagrer forkasting {} {} {}",
                    keyValue("tilstand", gjeldendeTilstand),
                    keyValue("fordi", eventName),
                    keyValue("vedtaksperiodeId", vedtaksperiodeId)
                )
                repository.lagre(
                    meldingId = UUID.fromString(message["@id"].asText()),
                    vedtaksperiodeId = vedtaksperiodeId,
                    fraTilstand = gjeldendeTilstand,
                    fordi = eventName,
                    tilTilstand = søppelbøttetilstand,
                    når = message["@opprettet"].asLocalDateTime(),
                    årsak = årsak
                )
            }
    }
}