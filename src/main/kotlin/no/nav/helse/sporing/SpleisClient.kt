package no.nav.helse.sporing

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.azure.AzureTokenProvider
import io.ktor.http.*
import no.nav.helse.sporing.person.PersonDTO
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

internal class SpleisClient(
    private val baseUrl: String,
    private val azureClient: AzureTokenProvider,
    private val accesstokenScope: String
) {

    private companion object {
        private val objectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        private val tjenestekallLog = LoggerFactory.getLogger("tjenestekall")
    }

    internal fun hentVedtaksperioder(pid: String) = objectMapper.convertValue<PersonDTO>("/api/vedtaksperioder".request(HttpMethod.Get, pid))

    private fun String.request(method: HttpMethod, pid: String): JsonNode {
        val (responseCode, responseBody) = with(URI(baseUrl + this).toURL().openConnection() as HttpURLConnection) {
            requestMethod = method.value

            setRequestProperty("Authorization", "Bearer ${azureClient.bearerToken(accesstokenScope).token}")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("fnr", pid)
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
