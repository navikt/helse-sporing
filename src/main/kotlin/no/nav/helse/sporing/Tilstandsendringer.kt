package no.nav.helse.sporing

import com.fasterxml.jackson.databind.JsonNode
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.rapids_rivers.*
import no.nav.helse.sporing.Event.eventName
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
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
                it.requireKey("@id", "@forårsaket_av.id", "@forårsaket_av.event_name",  "vedtaksperiodeId", "forrigeTilstand", "gjeldendeTilstand")
                it.interestedIn("@forårsaket_av.behov")
                it.require("@opprettet", JsonNode::asLocalDateTime)
                it.require("@forårsaket_av.opprettet", JsonNode::asLocalDateTime)
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
                val vedtaksperiodeId = UUID.fromString(message["vedtaksperiodeId"].asText())
                val årsak = Årsak(UUID.fromString(message["@forårsaket_av.id"].asText()), eventName, message["@forårsaket_av.opprettet"].asLocalDateTime())
                withMDC(mapOf(
                    "fraTilstand" to fraTilstand,
                    "tilTilstand" to tilTilstand,
                    "fordi" to eventName,
                    "vedtaksperiodeId" to "$vedtaksperiodeId"
                )) {
                    log.info(
                        "lagrer tilstandsendring {} {} {}",
                        keyValue("fraTilstand", fraTilstand),
                        keyValue("tilTilstand", tilTilstand),
                        keyValue("fordi", eventName),
                        keyValue("vedtaksperiodeId", vedtaksperiodeId)
                    )
                    repository.lagre(
                        meldingId = UUID.fromString(message["@id"].asText()),
                        vedtaksperiodeId = vedtaksperiodeId,
                        fraTilstand = fraTilstand,
                        fordi = eventName,
                        tilTilstand = tilTilstand,
                        når = message["@opprettet"].asLocalDateTime(),
                        årsak = årsak
                    )
                }
            }
    }
}