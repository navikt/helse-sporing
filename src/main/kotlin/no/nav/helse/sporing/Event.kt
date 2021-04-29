package no.nav.helse.sporing

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.JsonMessage

internal object Event {
    internal fun eventName(message: JsonMessage): String {
        val eventName = message["@forårsaket_av.event_name"].asText()
        if (eventName != "behov") return eventName
        return message["@forårsaket_av.behov"]
            .map(JsonNode::asText)
            .sorted()
            .map(String::toLowerCase)
            .joinToString(separator = "", transform = String::capitalize)
    }
}