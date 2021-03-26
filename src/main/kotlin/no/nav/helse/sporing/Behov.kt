package no.nav.helse.sporing

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import java.util.*

internal class Behov(rapidsConnection: RapidsConnection, repo: BehovRepository) {
    init {
        River(rapidsConnection)
            .validate {
                it.demandValue("@event_name", "behov")
                it.requireKey("@id")
                it.requireArray("@behov")
            }
            .onSuccess { packet, _ ->
                repo.lagre(UUID.fromString(packet["@id"].asText()), packet["@behov"].map(JsonNode::asText))
            }
    }
}
