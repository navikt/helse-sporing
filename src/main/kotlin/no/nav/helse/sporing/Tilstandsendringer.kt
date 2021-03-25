package no.nav.helse.sporing

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.*
import org.slf4j.LoggerFactory
import java.util.*

internal class Tilstandsendringer(rapidsConnection: RapidsConnection, repository: TilstandsendringRepository) {
    private companion object {
        private val sikkerLog = LoggerFactory.getLogger("tjenestekall")
    }
    init {
        River(rapidsConnection)
            .validate {
                it.demandValue("@event_name", "vedtaksperiode_endret")
                it.requireKey("@id", "@forårsaket_av.event_name", "vedtaksperiodeId", "forrigeTilstand", "gjeldendeTilstand")
                it.interestedIn("@behov")
                it.require("@opprettet", JsonNode::asLocalDateTime)
            }
            .onError { a, b ->
                sikkerLog.error("Forstod ikke vedtaksperiode_endret:\n${a.toExtendedReport()}")
            }
            .onSuccess { message, _ ->
                val eventName = eventName(message)
                repository.lagre(
                    meldingId = UUID.fromString(message["@id"].asText()),
                    vedtaksperiodeId = UUID.fromString(message["vedtaksperiodeId"].asText()),
                    fraTilstand = message["forrigeTilstand"].asText(),
                    fordi = eventName,
                    tilTilstand = message["gjeldendeTilstand"].asText(),
                    når = message["@opprettet"].asLocalDateTime()
                )
            }
    }

    private fun eventName(message: JsonMessage): String {
        if (message["@behov"].isMissingOrNull()) return message["@forårsaket_av.event_name"].asText()
        return message["@behov"].asSequence().map(JsonNode::asText).sorted().map(String::toLowerCase).joinToString(separator = "", transform = String::capitalize)
    }
}