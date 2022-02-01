package no.nav.helse.sporing

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.http.*
import no.nav.helse.sporing.person.PersonDTO
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

internal class SpleisClient(
    private val baseUrl: String,
    private val azureClient: AzureClient,
    private val accesstokenScope: String
) {

    private companion object {
        private val objectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        private val tjenestekallLog = LoggerFactory.getLogger("tjenestekall")
    }

    internal fun hentVedtaksperioder(fnr: String) = objectMapper.convertValue<PersonDTO>("/api/vedtaksperioder".request(HttpMethod.Get, fnr))

    private fun String.request(method: HttpMethod, fnr: String): JsonNode {
        val (responseCode, responseBody) = with(URL(baseUrl + this).openConnection() as HttpURLConnection) {
            requestMethod = method.value

            setRequestProperty("Authorization", "Bearer ${azureClient.getToken(accesstokenScope)}")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("fnr", fnr)
            connectTimeout = 10000
            readTimeout = 10000

            val stream: InputStream? = if (responseCode < 300) this.inputStream else this.errorStream
            responseCode to stream?.bufferedReader()?.readText()
        }

        tjenestekallLog.info("svar fra spleis: url=$baseUrl$this responseCode=$responseCode responseBody=$responseBody")

        if (responseCode >= 300 || responseBody == null) {
            throw SpleisApiException("unknown error (responseCode=$responseCode) from spleis", responseCode, responseBody)
        }

        return objectMapper.readTree(responseBody)
    }
}

internal class SpleisApiException(message: String, val statusCode: Int, val responseBody: String?) : RuntimeException(message)
