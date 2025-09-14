package tm.mail.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/**
 * Error response from Mail.tm API following JSON-LD/Hydra format.
 *
 * @property error Generic error message
 * @property message Detailed error description
 * @property type JSON-LD type identifier
 * @property hydraTitle Human-readable error title
 * @property hydraDescription Detailed error description
 * @property violations List of validation errors (for 422 responses)
 * @property status HTTP status code (if present in response)
 * @property detail Additional error details
 * @property title Error title
 */
@Serializable
data class MailTmErrorResponse(
    val error: String? = null,
    val message: String? = null,
    @SerialName("@type") val type: String? = null,
    @SerialName("hydra:title") val hydraTitle: String? = null,
    @SerialName("hydra:description") val hydraDescription: String? = null,
    val violations: List<ValidationViolation>? = null,
    val status: Int? = null,
    val detail: String? = null,
    val title: String? = null
)

/**
 * Validation violation details for 422 Unprocessable Entity responses.
 *
 * @property propertyPath JSON path to the invalid property
 * @property message Human-readable validation error message
 * @property code Machine-readable error code
 * @property invalidValue The invalid value that caused the violation (if present)
 */
@Serializable
data class ValidationViolation(
    val propertyPath: String? = null,
    val message: String? = null,
    val code: String? = null,
    val invalidValue: String? = null
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