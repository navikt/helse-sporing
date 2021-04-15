package no.nav.helse.sporing

import com.fasterxml.jackson.databind.JsonNode
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.rapids_rivers.*
import org.slf4j.LoggerFactory
import java.util.*

internal class Tilstandsendringer(rapidsConnection: RapidsConnection, repository: TilstandsendringRepository) {
    private companion object {
        private val log = LoggerFactory.getLogger(Tilstandsendringer::class.java)
        private val sikkerLog = LoggerFactory.getLogger("tjenestekall")
    }
    init {
        River(rapidsConnection)
            .validate {
                it.demandValue("@event_name", "vedtaksperiode_endret")
                it.requireKey("@id", "@forårsaket_av.id", "@forårsaket_av.event_name", "vedtaksperiodeId", "forrigeTilstand", "gjeldendeTilstand")
                it.interestedIn("@forårsaket_av.behov")
                it.require("@opprettet", JsonNode::asLocalDateTime)
                it.demand("forrigeTilstand") { forrigeTilstand ->
                    require(forrigeTilstand.textValue() != it["gjeldendeTilstand"].textValue())
                }
            }
            .onError { problems, _ ->
                log.error("Forstod ikke vedtaksperiode_endret (Se sikker logg for detaljer)")
                sikkerLog.error("Forstod ikke vedtaksperiode_endret:\n${problems.toExtendedReport()}")
            }
            .onSuccess { message, _ ->
                val fraTilstand = message["forrigeTilstand"].asText()
                val tilTilstand = message["gjeldendeTilstand"].asText()
                val eventName = eventName(message)
                log.info(
                    "lagrer tilstandsendring {} {} {}",
                    keyValue("fraTilstand", fraTilstand),
                    keyValue("tilTilstand", tilTilstand),
                    keyValue("fordi", eventName)
                )
                repository.lagre(
                    meldingId = UUID.fromString(message["@id"].asText()),
                    vedtaksperiodeId = UUID.fromString(message["vedtaksperiodeId"].asText()),
                    fraTilstand = fraTilstand,
                    fordi = eventName,
                    tilTilstand = tilTilstand,
                    når = message["@opprettet"].asLocalDateTime()
                )
            }
    }

    private fun eventName(message: JsonMessage): String {
        val eventName = message["@forårsaket_av.event_name"].asText()
        if (eventName != "behov") return eventName
        return message["@forårsaket_av.behov"]
            .map(JsonNode::asText)
            .sorted()
            .map(String::toLowerCase)
            .joinToString(separator = "", transform = String::capitalize)
    }
}