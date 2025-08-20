package tm.mail.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class ApiClient(
    engine: HttpClientEngine,
    private val baseUrl: String = "https://api.mail.tm"
) {

    private val http = HttpClient(engine) {
        expectSuccess = false

        val jsonCfg = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = true
            isLenient = true
        }

        install(ContentNegotiation) {
            json(jsonCfg)
            json(jsonCfg, contentType = ContentType.parse("application/ld+json"))
        }

        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.INFO
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 30_000
        }

        install(HttpRequestRetry) {
            maxRetries = 3
            retryIf { req, resp ->
                (req.method in listOf(HttpMethod.Get, HttpMethod.Head, HttpMethod.Delete)) &&
                        (resp.status == HttpStatusCode.TooManyRequests || resp.status.value in 500..599)
            }
            delayMillis { (it.coerceAtLeast(1)) * 1000L }
        }

        defaultRequest {
            url { protocol = URLProtocol.HTTPS; host = baseUrl }
            contentType(ContentType.Application.Json)
            headers.append(HttpHeaders.Accept, "application/ld+json, application/json")
        }
    }

    suspend fun createAccount(address: String, password: String): Account =
        http.post("/accounts") { setBody(AccountCreateBody(address, password)) }.body()

    suspend fun getDomains(page: Int? = 1): HydraCollection<Domain> =
        http.get("/domains") {
            if (page != null) parameter("page", page)
        }.body<HydraCollection<Domain>>()

}