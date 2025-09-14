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
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ServerResponseException

/**
 * Configuration class for ApiClient settings.
 *
 * @property baseUrl The base URL for the Mail.tm API
 * @property requestTimeoutMillis Request timeout in milliseconds
 * @property connectTimeoutMillis Connection timeout in milliseconds
 * @property socketTimeoutMillis Socket timeout in milliseconds
 * @property maxRetries Maximum number of retry attempts for failed requests
 * @property enableLogging Whether to enable HTTP request/response logging
 * @property logLevel The logging level to use
 */
data class ApiClientConfig(
    val baseUrl: String = "https://api.mail.tm",
    val requestTimeoutMillis: Long = 30_000L,
    val connectTimeoutMillis: Long = 15_000L,
    val socketTimeoutMillis: Long = 30_000L,
    val maxRetries: Int = 3,
    val enableLogging: Boolean = false,
    val logLevel: LogLevel = LogLevel.INFO
) {
    init {
        require(baseUrl.isNotBlank()) { "Base URL cannot be blank" }
        require(requestTimeoutMillis > 0) { "Request timeout must be positive" }
        require(connectTimeoutMillis > 0) { "Connect timeout must be positive" }
        require(socketTimeoutMillis > 0) { "Socket timeout must be positive" }
        require(maxRetries >= 0) { "Max retries cannot be negative" }
    }
}

/**
 * Mail.tm API client for interacting with temporary email services.
 *
 * This client provides a comprehensive interface for managing temporary email accounts,
 * sending/receiving messages, and handling authentication with the Mail.tm service.
 *
 * Example usage:
 * ```kotlin
 * val client = ApiClient.builder()
 *     .baseUrl("https://api.mail.tm")
 *     .enableLogging(true)
 *     .build(engine)
 *
 * val account = client.createAccount("test@example.org", "password")
 * client.authenticate("test@example.org", "password")
 * ```
 */
class ApiClient internal constructor(
    engine: HttpClientEngine,
    private val config: ApiClientConfig,
    private var bearerToken: String? = null
) : AutoCloseable {
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

        if (config.enableLogging) {
            install(Logging) {
                logger = Logger.DEFAULT
                level = config.logLevel
            }
        }

        install(HttpTimeout) {
            requestTimeoutMillis = config.requestTimeoutMillis
            connectTimeoutMillis = config.connectTimeoutMillis
            socketTimeoutMillis = config.socketTimeoutMillis
        }

        install(HttpRequestRetry) {
            maxRetries = config.maxRetries
            retryIf { req, resp ->
                (req.method in listOf(HttpMethod.Get, HttpMethod.Head, HttpMethod.Delete)) &&
                        (resp.status == HttpStatusCode.TooManyRequests || resp.status.value in 500..599)
            }
            delayMillis { (it.coerceAtLeast(1)) * 1000L }
        }


        defaultRequest {
            url(config.baseUrl)
            contentType(ContentType.Application.Json)
            headers.append(HttpHeaders.Accept, "application/ld+json, application/json")
            bearerToken?.let { token ->
                headers.append(HttpHeaders.Authorization, "Bearer $token")
            }
        }
    }

    /**
     * Sets the bearer token for API authentication.
     *
     * @param token The JWT token to use for authentication
     * @throws IllegalArgumentException if token is blank
     */
    fun setToken(token: String) {
        require(token.isNotBlank()) { "Token cannot be blank" }
        bearerToken = token
    }

    /**
     * Clears the current authentication token.
     */
    fun clearToken() {
        bearerToken = null
    }

    /**
     * Checks if the client is currently authenticated.
     *
     * @return true if a valid token is set, false otherwise
     */
    fun isAuthenticated(): Boolean = !bearerToken.isNullOrBlank()

    /**
     * Gets rate limit information from the last API response.
     *
     * @return Rate limit info or null if not available
     */
    fun getLastRateLimitInfo(): RateLimitInfo? = lastRateLimitInfo

    private var lastRateLimitInfo: RateLimitInfo? = null

    private suspend fun HttpResponse.handleErrors(): HttpResponse {
        // Extract rate limiting info from response headers
        extractRateLimitInfo()

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

    private fun HttpResponse.extractRateLimitInfo() {
        val limit = headers["X-RateLimit-Limit"]?.toIntOrNull()
        val remaining = headers["X-RateLimit-Remaining"]?.toIntOrNull()
        val reset = headers["X-RateLimit-Reset"]?.toLongOrNull()

        if (limit != null && remaining != null && reset != null) {
            lastRateLimitInfo = RateLimitInfo(limit, remaining, reset)
        }
    }

    private fun buildErrorMessage(errorResponse: MailTmErrorResponse?): String {
        return when {
            errorResponse?.violations?.isNotEmpty() == true -> {
                val violationMessages = errorResponse.violations.mapNotNull { violation ->
                    val path = violation.propertyPath?.let { "[$it] " } ?: ""
                    val message = violation.message ?: "Validation error"
                    val code = violation.code?.let { " (${it})" } ?: ""
                    "$path$message$code"
                }
                if (violationMessages.isNotEmpty()) {
                    "Validation errors: ${violationMessages.joinToString("; ")}"
                } else {
                    "Validation error occurred"
                }
            }

            errorResponse?.detail != null -> errorResponse.detail
            errorResponse?.hydraDescription != null -> errorResponse.hydraDescription
            errorResponse?.message != null -> errorResponse.message
            errorResponse?.error != null -> errorResponse.error
            errorResponse?.title != null -> errorResponse.title
            errorResponse?.hydraTitle != null -> errorResponse.hydraTitle
            else -> "Unknown error occurred"
        }
    }

    /**
     * Creates a new email account.
     *
     * @param address The email address for the new account
     * @param password The password for the new account
     * @return The created account details
     * @throws IllegalArgumentException if address or password is invalid
     * @throws MailTmException.AccountAlreadyExists if account already exists
     */
    suspend fun createAccount(address: String, password: String): AccountResponse {
        validateEmailAddress(address)
        validatePassword(password)
        return executeRequest("creating account") {
            http.post("/accounts") {
                setBody(AccountCreateRequest(address, password))
            }.handleErrors().body()
        }
    }

    private suspend inline fun <T> executeRequest(
        operation: String,
        block: () -> T
    ): T {
        return try {
            block()
        } catch (e: ClientRequestException) {
            e.response.handleErrors()
            throw MailTmException.Http(e.response.status.value, "Client error during $operation")
        } catch (e: ServerResponseException) {
            e.response.handleErrors()
            throw MailTmException.Server("Server error during $operation: ${e.response.status.value}")
        } catch (e: HttpRequestTimeoutException) {
            throw MailTmException.TimeoutError("Request timed out during $operation", e)
        } catch (e: MailTmException) {
            throw e
        } catch (e: Exception) {
            throw MailTmException.NetworkError("Network error during $operation: ${e.message}", e)
        }
    }

    private fun validateEmailAddress(address: String) {
        require(address.isNotBlank()) { "Email address cannot be blank" }
        require(address.contains("@")) { "Invalid email address format" }
        require(address.length <= 320) { "Email address too long (max 320 characters)" }
        require(address.count { it == '@' } == 1) { "Email address must contain exactly one @ symbol" }

        val parts = address.split("@")
        val localPart = parts[0]
        val domain = parts[1]

        require(localPart.isNotEmpty()) { "Email local part cannot be empty" }
        require(domain.isNotEmpty()) { "Email domain cannot be empty" }
        require(localPart.length <= 64) { "Email local part too long (max 64 characters)" }
        require(domain.length <= 253) { "Email domain too long (max 253 characters)" }
        require(domain.contains(".")) { "Email domain must contain at least one dot" }
        require(!domain.startsWith(".") && !domain.endsWith(".")) { "Email domain cannot start or end with dot" }
    }

    private fun validatePassword(password: String) {
        require(password.isNotBlank()) { "Password cannot be blank" }
        require(password.length >= 8) { "Password must be at least 8 characters long" }
    }

    private fun validateId(id: String, fieldName: String = "ID") {
        require(id.isNotBlank()) { "$fieldName cannot be blank" }
    }

    /**
     * Retrieves available email domains.
     *
     * @param page Optional page number for pagination
     * @return Collection of available domains
     */
    suspend fun getDomains(page: Int? = null): HydraCollection<DomainResponse> {
        page?.let { require(it > 0) { "Page number must be positive" } }
        return executeRequest("fetching domains") {
            http.get("/domains") {
                page?.let { parameter("page", it) }
            }.handleErrors().body()
        }
    }

    /**
     * Retrieves a domain by its ID.
     *
     * @param id The domain ID
     * @return The domain details
     * @throws IllegalArgumentException if id is blank
     */
    suspend fun getDomainById(id: String): DomainResponse {
        require(id.isNotBlank()) { "Domain ID cannot be blank" }
        return executeRequest("fetching domain by id") {
            http.get("/domains/$id").handleErrors().body()
        }
    }

    /**
     * Creates an authentication token for the given credentials.
     *
     * @param address The email address
     * @param password The account password
     * @return The authentication token response
     * @throws IllegalArgumentException if credentials are invalid
     * @throws MailTmException.InvalidCredentials if credentials are incorrect
     */
    suspend fun createToken(address: String, password: String): TokenResponse {
        validateEmailAddress(address)
        validatePassword(password)
        return executeRequest("creating token") {
            http.post("/token") {
                setBody(TokenCreateRequest(address, password))
            }.handleErrors().body()
        }
    }

    /**
     * Retrieves account details by ID.
     *
     * @param id The account ID
     * @return Account details
     * @throws IllegalArgumentException if id is blank
     */
    suspend fun getAccountById(id: String): AccountResponse {
        validateId(id, "Account ID")
        return executeRequest("fetching account by id") {
            http.get("/accounts/$id").handleErrors().body()
        }
    }

    /**
     * Deletes an account by ID.
     *
     * @param id The account ID to delete
     * @throws IllegalArgumentException if id is blank
     */
    suspend fun deleteAccount(id: String) {
        validateId(id, "Account ID")
        executeRequest("deleting account") {
            http.delete("/accounts/$id").handleErrors()
        }
    }

    /**
     * Retrieves the current authenticated user's account details.
     *
     * @return Current user's account details
     * @throws MailTmException.Unauthorized if not authenticated
     */
    suspend fun getMe(): AccountResponse {
        return executeRequest("fetching current user") {
            http.get("/me").handleErrors().body()
        }
    }

    /**
     * Retrieves messages for the authenticated user.
     *
     * @param page Optional page number for pagination
     * @return Collection of message summaries
     * @throws MailTmException.Unauthorized if not authenticated
     */
    suspend fun getMessages(page: Int? = null): HydraCollection<MessageSummaryResponse> {
        page?.let { require(it > 0) { "Page number must be positive" } }
        return executeRequest("fetching messages") {
            http.get("/messages") {
                page?.let { parameter("page", it) }
            }.handleErrors().body()
        }
    }

    suspend fun getMessageById(id: String): MessageDetailResponse {
        validateId(id, "Message ID")
        return executeRequest("fetching message by id") {
            http.get("/messages/$id").handleErrors().body()
        }
    }

    suspend fun deleteMessage(id: String) {
        validateId(id, "Message ID")
        executeRequest("deleting message") {
            http.delete("/messages/$id").handleErrors()
        }
    }

    suspend fun markMessageAsSeen(id: String, seen: Boolean = true) {
        validateId(id, "Message ID")
        executeRequest("marking message as seen") {
            http.patch("/messages/$id") {
                setBody(MessageSeenRequest(seen))
            }.handleErrors()
        }
    }

    suspend fun getMessageSource(id: String): SourceResponse {
        validateId(id, "Source ID")
        return executeRequest("fetching message source") {
            http.get("/sources/$id").handleErrors().body()
        }
    }

    /**
     * Closes the HTTP client and releases resources.
     * Implements AutoCloseable for use with try-with-resources.
     */
    override fun close() {
        http.close()
    }

    companion object {
        /**
         * Creates a builder for configuring ApiClient instances.
         *
         * @return A new ApiClientBuilder instance
         */
        fun builder(): ApiClientBuilder = ApiClientBuilder()

        /**
         * Creates an ApiClient with default configuration.
         *
         * @param engine The HTTP client engine to use
         * @return A new ApiClient instance
         */
        fun create(engine: HttpClientEngine): ApiClient {
            return ApiClient(engine, ApiClientConfig())
        }

        /**
         * Creates an ApiClient with custom configuration.
         *
         * @param engine The HTTP client engine to use
         * @param config The configuration to use
         * @return A new ApiClient instance
         */
        fun create(engine: HttpClientEngine, config: ApiClientConfig): ApiClient {
            return ApiClient(engine, config)
        }

        /**
         * Creates a new account and authenticates the client.
         *
         * @param engine The HTTP client engine to use
         * @param address The email address for the new account
         * @param password The password for the new account
         * @param config Optional configuration (uses default if not provided)
         * @return An authenticated ApiClient instance
         */
        suspend fun createAccountAndAuthenticate(
            engine: HttpClientEngine,
            address: String,
            password: String,
            config: ApiClientConfig = ApiClientConfig()
        ): ApiClient {
            val client = ApiClient(engine, config)
            return client.executeRequest("creating account and authenticating") {
                client.createAccount(address, password)
                val tokenResponse = client.createToken(address, password)
                client.setToken(tokenResponse.token)
                client
            }
        }

        /**
         * Authenticates with existing account credentials.
         *
         * @param engine The HTTP client engine to use
         * @param address The email address
         * @param password The account password
         * @param config Optional configuration (uses default if not provided)
         * @return An authenticated ApiClient instance
         */
        suspend fun authenticateExisting(
            engine: HttpClientEngine,
            address: String,
            password: String,
            config: ApiClientConfig = ApiClientConfig()
        ): ApiClient {
            val client = ApiClient(engine, config)
            return client.executeRequest("authenticating existing account") {
                val tokenResponse = client.createToken(address, password)
                client.setToken(tokenResponse.token)
                client
            }
        }
    }

    // Convenience methods
    /**
     * Gets a random available domain for account creation.
     *
     * @return A random active domain
     * @throws MailTmException if no active domains are available
     */
    suspend fun getRandomAvailableDomain(): DomainResponse {
        val domains = getDomains()
        val activeDomains = domains.items.filter { it.isActive && !it.isPrivate }
        if (activeDomains.isEmpty()) {
            throw MailTmException.DomainNotAvailable("No active domains available")
        }
        return activeDomains.random()
    }

    /**
     * Creates a random account with the specified password.
     *
     * @param password The password for the new account
     * @return The created account details
     * @throws IllegalArgumentException if password is invalid
     */
    suspend fun createRandomAccount(password: String): AccountResponse {
        validatePassword(password)
        val domain = getRandomAvailableDomain()
        val randomUsername = "user" + (10000..99999).random()
        val address = "$randomUsername@${domain.domain}"
        return createAccount(address, password)
    }

    /**
     * Retrieves all messages across all pages with optional limit.
     *
     * @param maxMessages Maximum number of messages to retrieve (null for no limit)
     * @return List of all messages
     */
    suspend fun getAllMessages(maxMessages: Int? = null): List<MessageSummaryResponse> {
        val allMessages = mutableListOf<MessageSummaryResponse>()
        var page = 1
        do {
            val response = getMessages(page)
            val remainingSlots = maxMessages?.let { it - allMessages.size }
            val itemsToAdd = if (remainingSlots != null && remainingSlots < response.items.size) {
                response.items.take(remainingSlots)
            } else {
                response.items
            }
            allMessages.addAll(itemsToAdd)
            page++

            if (maxMessages != null && allMessages.size >= maxMessages) break
        } while (response.items.isNotEmpty())
        return allMessages
    }

    /**
     * Retrieves only unread messages.
     *
     * @return List of unread messages
     */
    suspend fun getUnreadMessages(): List<MessageSummaryResponse> {
        return getAllMessages().filter { it.seen == false }
    }

    /**
     * Marks all messages as read.
     *
     * @param batchSize Number of messages to process in parallel (default: 5)
     */
    suspend fun markAllMessagesAsSeen(batchSize: Int = 5) {
        require(batchSize > 0) { "Batch size must be positive" }
        val messages = getUnreadMessages()
        messages.chunked(batchSize).forEach { batch ->
            batch.forEach { message ->
                markMessageAsSeen(message.id)
            }
        }
    }

    /**
     * Deletes all messages in the account.
     *
     * @param batchSize Number of messages to process in parallel (default: 5)
     */
    suspend fun deleteAllMessages(batchSize: Int = 5) {
        require(batchSize > 0) { "Batch size must be positive" }
        val messages = getAllMessages()
        messages.chunked(batchSize).forEach { batch ->
            batch.forEach { message ->
                deleteMessage(message.id)
            }
        }
    }

    /**
     * Creates SSE configuration for real-time message updates.
     * Note: SSE implementation requires platform-specific EventSource implementation.
     *
     * @param accountId Account ID to listen for updates
     * @return SSE configuration for connecting to Mercure hub
     * @throws IllegalArgumentException if not authenticated or accountId is invalid
     */
    fun createSSEConfig(accountId: String? = null): SSEConfig {
        require(isAuthenticated()) { "Must be authenticated to create SSE config" }
        val targetAccountId = accountId ?: run {
            // If no accountId provided, we'd need to call getMe() but this is not suspend
            // Users should provide accountId or call getMe() first
            throw IllegalArgumentException("Account ID is required for SSE configuration")
        }
        validateId(targetAccountId, "Account ID")
        return SSEConfig(accountId = targetAccountId)
    }
}

/**
 * Builder class for creating ApiClient instances with custom configuration.
 */
class ApiClientBuilder {
    private var baseUrl: String = "https://api.mail.tm"
    private var requestTimeoutMillis: Long = 30_000L
    private var connectTimeoutMillis: Long = 15_000L
    private var socketTimeoutMillis: Long = 30_000L
    private var maxRetries: Int = 3
    private var enableLogging: Boolean = false
    private var logLevel: LogLevel = LogLevel.INFO

    /**
     * Sets the base URL for the API.
     *
     * @param baseUrl The base URL
     * @return This builder instance
     */
    fun baseUrl(baseUrl: String) = apply {
        this.baseUrl = baseUrl
    }

    /**
     * Sets the request timeout.
     *
     * @param timeout The timeout in milliseconds
     * @return This builder instance
     */
    fun requestTimeout(timeout: Long) = apply {
        this.requestTimeoutMillis = timeout
    }

    /**
     * Sets the connection timeout.
     *
     * @param timeout The timeout in milliseconds
     * @return This builder instance
     */
    fun connectTimeout(timeout: Long) = apply {
        this.connectTimeoutMillis = timeout
    }

    /**
     * Sets the socket timeout.
     *
     * @param timeout The timeout in milliseconds
     * @return This builder instance
     */
    fun socketTimeout(timeout: Long) = apply {
        this.socketTimeoutMillis = timeout
    }

    /**
     * Sets the maximum number of retry attempts.
     *
     * @param retries The maximum number of retries
     * @return This builder instance
     */
    fun maxRetries(retries: Int) = apply {
        this.maxRetries = retries
    }

    /**
     * Enables or disables HTTP logging.
     *
     * @param enable Whether to enable logging
     * @return This builder instance
     */
    fun enableLogging(enable: Boolean = true) = apply {
        this.enableLogging = enable
    }

    /**
     * Sets the logging level.
     *
     * @param level The logging level
     * @return This builder instance
     */
    fun logLevel(level: LogLevel) = apply {
        this.logLevel = level
    }

    /**
     * Builds the ApiClient with the specified configuration.
     *
     * @param engine The HTTP client engine to use
     * @return A configured ApiClient instance
     */
    fun build(engine: HttpClientEngine): ApiClient {
        val config = ApiClientConfig(
            baseUrl = baseUrl,
            requestTimeoutMillis = requestTimeoutMillis,
            connectTimeoutMillis = connectTimeoutMillis,
            socketTimeoutMillis = socketTimeoutMillis,
            maxRetries = maxRetries,
            enableLogging = enableLogging,
            logLevel = logLevel
        )
        return ApiClient(engine, config)
    }
}