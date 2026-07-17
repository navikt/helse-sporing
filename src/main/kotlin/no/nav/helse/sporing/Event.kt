package no.nav.helse.sporing

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import tools.jackson.databind.JsonNode

internal object Event {
    internal fun eventName(message: JsonMessage): String {
        val eventName = message["@forårsaket_av.event_name"].asText()
        if (eventName != "behov") return eventName
        return message["@forårsaket_av.behov"]
            .toList()
            .map(JsonNode::asText)
            .sorted()
            .map(String::lowercase)
            .joinToString(separator = "", transform = { it.replaceFirstChar(Char::uppercase) })
    }
}
