package tm.mail.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ServerResponseException

class ApiClient(
    engine: HttpClientEngine,
    private val baseUrl: String = "https://api.mail.tm",
    private var bearerToken: String? = null
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

        install(Auth) {
            bearer {
                loadTokens {
                    bearerToken?.let { BearerTokens(it, "") }
                }
            }
        }

        defaultRequest {
            url(baseUrl)
            contentType(ContentType.Application.Json)
            headers.append(HttpHeaders.Accept, "application/ld+json, application/json")
        }
    }

    fun setToken(token: String) {
        bearerToken = token
    }

    private suspend fun HttpResponse.handleErrors(): HttpResponse {
        if (status.isSuccess()) return this

        val errorResponse = try {
            body<MailTmErrorResponse>()
        } catch (e: Exception) {
            null
        }

        val errorMessage = buildErrorMessage(errorResponse)

        when (status) {
            HttpStatusCode.BadRequest -> {
                when {
                    errorMessage.contains("already exists", ignoreCase = true) ->
                        throw MailTmException.AccountAlreadyExists(errorMessage, errorResponse)

                    errorMessage.contains("invalid domain", ignoreCase = true) ->
                        throw MailTmException.InvalidDomain(errorMessage, errorResponse)

                    else -> throw MailTmException.BadRequest(errorMessage, errorResponse)
                }
            }

            HttpStatusCode.Unauthorized -> {
                when {
                    errorMessage.contains("invalid credentials", ignoreCase = true) ||
                            errorMessage.contains("invalid password", ignoreCase = true) ||
                            errorMessage.contains("wrong password", ignoreCase = true) ->
                        throw MailTmException.InvalidCredentials(errorMessage, errorResponse)

                    else -> throw MailTmException.Unauthorized(errorMessage, errorResponse)
                }
            }

            HttpStatusCode.NotFound -> {
                when {
                    errorMessage.contains("message", ignoreCase = true) ->
                        throw MailTmException.MessageNotFound(errorMessage, errorResponse)

                    else -> throw MailTmException.NotFound(errorMessage, errorResponse)
                }
            }

            HttpStatusCode.Conflict -> {
                when {
                    errorMessage.contains("already exists", ignoreCase = true) ->
                        throw MailTmException.AccountAlreadyExists(errorMessage, errorResponse)

                    else -> throw MailTmException.Conflict(errorMessage, errorResponse)
                }
            }

            HttpStatusCode.UnprocessableEntity -> {
                when {
                    errorMessage.contains("domain", ignoreCase = true) &&
                            errorMessage.contains("not available", ignoreCase = true) ->
                        throw MailTmException.DomainNotAvailable(errorMessage, errorResponse)

                    errorMessage.contains("quota", ignoreCase = true) ->
                        throw MailTmException.QuotaExceeded(errorMessage, errorResponse)

                    errorMessage.contains("disabled", ignoreCase = true) ->
                        throw MailTmException.AccountDisabled(errorMessage, errorResponse)

                    else -> throw MailTmException.UnprocessableEntity(errorMessage, errorResponse)
                }
            }

            HttpStatusCode.TooManyRequests -> throw MailTmException.RateLimited(
                errorMessage,
                errorResponse
            )

            in HttpStatusCode.InternalServerError..HttpStatusCode.GatewayTimeout ->
                throw MailTmException.Server(errorMessage, errorResponse)

            else -> throw MailTmException.Http(status.value, errorMessage, errorResponse)
        }

        return this
    }

    private fun buildErrorMessage(errorResponse: MailTmErrorResponse?): String {
        return when {
            errorResponse?.violations?.isNotEmpty() == true -> {
                val violationMessages = errorResponse.violations.mapNotNull { it.message }
                if (violationMessages.isNotEmpty()) {
                    "Validation errors: ${violationMessages.joinToString(", ")}"
                } else {
                    "Validation error occurred"
                }
            }

            errorResponse?.error != null -> errorResponse.error
            errorResponse?.message != null -> errorResponse.message
            errorResponse?.hydraDescription != null -> errorResponse.hydraDescription
            errorResponse?.hydraTitle != null -> errorResponse.hydraTitle
            else -> "Unknown error occurred"
        }
    }

    suspend fun createAccount(address: String, password: String): AccountDto {
        return try {
            http.post("/accounts") {
                setBody(AccountCreateBody(address, password))
            }.handleErrors().body()
        } catch (e: ClientRequestException) {
            throw handleHttpException(e)
        } catch (e: ServerResponseException) {
            throw handleHttpException(e)
        } catch (e: HttpRequestTimeoutException) {
            throw MailTmException.TimeoutError("Request timed out while creating account", e)
        } catch (e: MailTmException) {
            throw e
        } catch (e: Exception) {
            throw MailTmException.NetworkError(
                "Network error while creating account: ${e.message}",
                e
            )
        }
    }

    private suspend fun handleHttpException(e: Exception): MailTmException {
        return when (e) {
            is ClientRequestException -> e.response.handleErrors()
                .let { MailTmException.Http(e.response.status.value, "Client error") }

            is ServerResponseException -> e.response.handleErrors()
                .let { MailTmException.Server("Server error: ${e.response.status.value}") }

            else -> MailTmException.NetworkError("HTTP error: ${e.message}", e)
        }
    }

    suspend fun getDomains(page: Int? = null): HydraCollection<DomainDto> {
        return http.get("/domains") {
            page?.let { parameter("page", it) }
        }.handleErrors().body()
    }

    suspend fun getDomainById(id: String): DomainDto {
        return http.get("/domains/$id").handleErrors().body()
    }

    suspend fun createToken(address: String, password: String): TokenResponse {
        return try {
            http.post("/token") {
                setBody(TokenBody(address, password))
            }.handleErrors().body()
        } catch (e: ClientRequestException) {
            throw handleHttpException(e)
        } catch (e: ServerResponseException) {
            throw handleHttpException(e)
        } catch (e: HttpRequestTimeoutException) {
            throw MailTmException.TimeoutError("Request timed out while creating token", e)
        } catch (e: MailTmException) {
            throw e
        } catch (e: Exception) {
            throw MailTmException.NetworkError(
                "Network error while creating token: ${e.message}",
                e
            )
        }
    }

    suspend fun getAccountById(id: String): AccountDto {
        return http.get("/accounts/$id").handleErrors().body()
    }

    suspend fun deleteAccount(id: String) {
        http.delete("/accounts/$id").handleErrors()
    }

    suspend fun getMe(): AccountDto {
        return http.get("/me").handleErrors().body()
    }

    suspend fun getMessages(page: Int? = null): HydraCollection<MessageSummaryDto> {
        return try {
            http.get("/messages") {
                page?.let { parameter("page", it) }
            }.handleErrors().body()
        } catch (e: ClientRequestException) {
            throw handleHttpException(e)
        } catch (e: ServerResponseException) {
            throw handleHttpException(e)
        } catch (e: HttpRequestTimeoutException) {
            throw MailTmException.TimeoutError("Request timed out while fetching messages", e)
        } catch (e: MailTmException) {
            throw e
        } catch (e: Exception) {
            throw MailTmException.NetworkError(
                "Network error while fetching messages: ${e.message}",
                e
            )
        }
    }

    suspend fun getMessageById(id: String): MessageDetailDto {
        return http.get("/messages/$id").handleErrors().body()
    }

    suspend fun deleteMessage(id: String) {
        http.delete("/messages/$id").handleErrors()
    }

    suspend fun markMessageAsSeen(id: String, seen: Boolean = true) {
        http.patch("/messages/$id") {
            setBody(PatchMessageSeen(seen))
        }.handleErrors()
    }

    suspend fun getMessageSource(id: String): SourceDto {
        return http.get("/sources/$id").handleErrors().body()
    }

    suspend fun close() {
        http.close()
    }

    companion object {
        suspend fun createAccountAndAuthenticate(
            engine: HttpClientEngine,
            address: String,
            password: String,
            baseUrl: String = "https://api.mail.tm"
        ): ApiClient {
            val client = ApiClient(engine, baseUrl)
            try {
                client.createAccount(address, password)
                val tokenResponse = client.createToken(address, password)
                client.setToken(tokenResponse.token)
                return client
            } catch (e: Exception) {
                client.close()
                throw e
            }
        }

        suspend fun authenticateExisting(
            engine: HttpClientEngine,
            address: String,
            password: String,
            baseUrl: String = "https://api.mail.tm"
        ): ApiClient {
            val client = ApiClient(engine, baseUrl)
            try {
                val tokenResponse = client.createToken(address, password)
                client.setToken(tokenResponse.token)
                return client
            } catch (e: Exception) {
                client.close()
                throw e
            }
        }
    }

    suspend fun getRandomAvailableDomain(): DomainDto {
        val domains = getDomains()
        val activeDomains = domains.items.filter { it.isActive && !it.isPrivate }
        return activeDomains.random()
    }

    suspend fun createRandomAccount(password: String): AccountDto {
        val domain = getRandomAvailableDomain()
        val randomUsername = "user" + (10000..99999).random()
        val address = "$randomUsername@${domain.domain}"
        return createAccount(address, password)
    }

    suspend fun getAllMessages(): List<MessageSummaryDto> {
        val allMessages = mutableListOf<MessageSummaryDto>()
        var page = 1
        do {
            val response = getMessages(page)
            allMessages.addAll(response.items)
            page++
        } while (response.items.isNotEmpty())
        return allMessages
    }

    suspend fun getUnreadMessages(): List<MessageSummaryDto> {
        return getAllMessages().filter { it.seen == false }
    }

    suspend fun markAllMessagesAsSeen() {
        val messages = getUnreadMessages()
        messages.forEach { message ->
            markMessageAsSeen(message.id)
        }
    }

    suspend fun deleteAllMessages() {
        val messages = getAllMessages()
        messages.forEach { message ->
            deleteMessage(message.id)
        }
    }
}