package tm.mail.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class MailTmErrorResponse(
    val error: String? = null,
    val message: String? = null,
    @SerialName("@type") val type: String? = null,
    @SerialName("hydra:title") val hydraTitle: String? = null,
    @SerialName("hydra:description") val hydraDescription: String? = null,
    val violations: List<ValidationViolation>? = null
)

@Serializable
data class ValidationViolation(
    val propertyPath: String? = null,
    val message: String? = null,
    val code: String? = null
)

sealed class MailTmException(message: String, val originalResponse: MailTmErrorResponse? = null) : Exception(message) {

    // 400 - Bad Request
    class BadRequest(message: String, response: MailTmErrorResponse? = null) : MailTmException(message, response)
    
    // 401 - Unauthorized
    class Unauthorized(message: String, response: MailTmErrorResponse? = null) : MailTmException(message, response)
    
    // 404 - Not Found
    class NotFound(message: String, response: MailTmErrorResponse? = null) : MailTmException(message, response)
    
    // 409 - Conflict
    class Conflict(message: String, response: MailTmErrorResponse? = null) : MailTmException(message, response)
    
    // 422 - Unprocessable Entity (Validation errors)
    class UnprocessableEntity(message: String, response: MailTmErrorResponse? = null) : MailTmException(message, response)
    
    // 429 - Rate Limited
    class RateLimited(message: String, response: MailTmErrorResponse? = null) : MailTmException(message, response)
    
    // 5xx - Server errors
    class Server(message: String, response: MailTmErrorResponse? = null) : MailTmException(message, response)
    
    // Generic HTTP error
    class Http(val code: Int, message: String, response: MailTmErrorResponse? = null) : MailTmException("HTTP $code: $message", response)
    
    // Mail.tm specific errors
    class AccountAlreadyExists(message: String, response: MailTmErrorResponse? = null) : MailTmException(message, response)
    class InvalidDomain(message: String, response: MailTmErrorResponse? = null) : MailTmException(message, response)
    class InvalidCredentials(message: String, response: MailTmErrorResponse? = null) : MailTmException(message, response)
    class AccountDisabled(message: String, response: MailTmErrorResponse? = null) : MailTmException(message, response)
    class MessageNotFound(message: String, response: MailTmErrorResponse? = null) : MailTmException(message, response)
    class DomainNotAvailable(message: String, response: MailTmErrorResponse? = null) : MailTmException(message, response)
    class QuotaExceeded(message: String, response: MailTmErrorResponse? = null) : MailTmException(message, response)
    
    // Network/timeout errors
    class NetworkError(message: String, override val cause: Throwable? = null) : MailTmException(message, null) {
    }
    class TimeoutError(message: String, override val cause: Throwable? = null) : MailTmException(message, null) {
    }
}