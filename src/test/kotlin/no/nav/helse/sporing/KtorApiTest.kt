package no.nav.helse.sporing

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.http.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.*
import java.io.InputStream
import java.lang.Thread.sleep
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import javax.sql.DataSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class KtorApiTest() {

    private fun randomPort() = ServerSocket(0).use { it.localPort }

    private val serverPort = randomPort()
    private val baseUrl = "http://localhost:$serverPort"
    private lateinit var server: NettyApplicationEngine
    private val repository = FilesystemRepository("/tilstandsmaskin.json")

    @BeforeAll
    fun setup() {
        val environment = applicationEngineEnvironment {
            connector { port = serverPort }
            module(ktorApi(repository))
        }
        server = embeddedServer(Netty, environment)
        server.start(wait = false)
    }

    @AfterAll
    fun shutdown() {
        server.stop(0, 0)
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