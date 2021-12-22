package no.nav.helse.sporing

import io.ktor.http.*
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import java.io.InputStream
import java.lang.Thread.sleep
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class KtorApiTest() {

    private fun randomPort() = ServerSocket(0).use { it.localPort }
    private val serverPort = randomPort()
    private val testApp = LocalApp(serverPort)

    private val baseUrl = "http://localhost:$serverPort"

    @BeforeAll
    fun setup() {
        testApp.start()
    }

    @AfterAll
    fun shutdown() {
        testApp.stop()
    }

    //@Test
    fun runTestServer() {
        println("Server available at: $baseUrl")
        while (true) {
            sleep(500)
        }
    }

    private fun String.handleRequest(
        method: HttpMethod,
        builder: HttpURLConnection.() -> Unit = {}
    ): HttpURLConnection {
        val url = URL("$baseUrl$this")
        val con = url.openConnection() as HttpURLConnection
        con.requestMethod = method.value
        con.builder()
        con.connectTimeout = 1000
        con.readTimeout = 5000
        return con
    }

    private val HttpURLConnection.responseBody: String get() {
        val stream: InputStream? = if (responseCode in 200..299) inputStream else errorStream
        return stream?.use { it.bufferedReader().readText() } ?: ""
    }
}