package tm.mail.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/**
 * Hydra collection wrapper for paginated responses from Mail.tm API.
 *
 * @property items List of items in the collection
 * @property totalItems Total number of items across all pages
 * @property view Pagination view information
 */
@Serializable
data class HydraCollection<T>(
    @SerialName("hydra:member") val items: List<T> = emptyList(),
    @SerialName("hydra:totalItems") val totalItems: Int = 0,
    @SerialName("hydra:view") val view: HydraView? = null
)

/**
 * Pagination view information for Hydra collections.
 *
 * @property id Current page identifier
 * @property type Resource type
 * @property first URL to first page
 * @property last URL to last page
 * @property previous URL to previous page (null if first page)
 * @property next URL to next page (null if last page)
 */
@Serializable
data class HydraView(
    @SerialName("@id") val id: String? = null,
    @SerialName("@type") val type: String? = null,
    @SerialName("hydra:first") val first: String? = null,
    @SerialName("hydra:last") val last: String? = null,
    @SerialName("hydra:previous") val previous: String? = null,
    @SerialName("hydra:next") val next: String? = null
)

/**
 * Represents an available email domain on Mail.tm.
 *
 * @property id Unique domain identifier
 * @property domain Domain name (e.g., "example.com")
 * @property isActive Whether the domain is currently active
 * @property isPrivate Whether the domain is private (restricted)
 * @property createdAt ISO 8601 timestamp when domain was created
 * @property updatedAt ISO 8601 timestamp when domain was last updated
 */
@Serializable
data class DomainResponse(
    val id: String,
    val domain: String,
    val isActive: Boolean,
    val isPrivate: Boolean,
    val createdAt: String,
    val updatedAt: String
)

/**
 * Request body for creating a new email account.
 *
 * @property address Email address (must include valid domain)
 * @property password Account password (minimum 8 characters)
 */
@Serializable
data class AccountCreateRequest(
    val address: String,
    val password: String
)

/**
 * Represents an email account on Mail.tm.
 *
 * @property id Unique account identifier
 * @property address Email address
 * @property quota Total storage quota in bytes
 * @property used Used storage in bytes
 * @property isDisabled Whether the account is disabled
 * @property isDeleted Whether the account is marked for deletion
 * @property createdAt ISO 8601 timestamp when account was created
 * @property updatedAt ISO 8601 timestamp when account was last updated
 */
@Serializable
data class AccountResponse(
    val id: String,
    val address: String,
    val quota: Long,
    val used: Long,
    val isDisabled: Boolean,
    val isDeleted: Boolean,
    val createdAt: String,
    val updatedAt: String
)

/**
 * Request body for authentication token creation.
 *
 * @property address Email address
 * @property password Account password
 */
@Serializable
data class TokenCreateRequest(
    val address: String,
    val password: String
)

/**
 * Response containing JWT authentication token.
 *
 * @property id Token identifier
 * @property token JWT bearer token for API authentication
 */
@Serializable
data class TokenResponse(
    val id: String,
    val token: String
)

/**
 * Represents an email address with display name.
 *
 * @property name Display name (can be empty)
 * @property address Email address
 */
@Serializable
data class MessageAddressResponse(
    val name: String,
    val address: String
)

/**
 * Represents an email attachment.
 *
 * @property id Unique attachment identifier
 * @property filename Original filename of the attachment
 * @property contentType MIME content type
 * @property disposition Content disposition (attachment/inline)
 * @property transferEncoding Transfer encoding used
 * @property related Whether attachment is related to message content
 * @property size Attachment size in bytes
 * @property downloadUrl URL to download the attachment
 */
@Serializable
data class AttachmentResponse(
    val id: String,
    val filename: String,
    val contentType: String,
    val disposition: String,
    val transferEncoding: String,
    val related: Boolean,
    val size: Long,
    val downloadUrl: String
)

/**
 * Summary information about an email message.
 *
 * @property id Unique message identifier
 * @property accountId Account ID that owns this message
 * @property msgid Original email Message-ID header
 * @property from Sender information
 * @property to List of recipients
 * @property subject Email subject line
 * @property intro Preview text of the message body
 * @property seen Whether the message has been marked as read
 * @property isDeleted Whether the message is marked for deletion
 * @property hasAttachments Whether the message contains attachments
 * @property size Message size in bytes
 * @property downloadUrl URL to download the raw message
 * @property createdAt ISO 8601 timestamp when message was received
 * @property updatedAt ISO 8601 timestamp when message was last updated
 */
@Serializable
data class MessageSummaryResponse(
    val id: String,
    val accountId: String,
    val msgid: String,
    val from: MessageAddressResponse,
    val to: List<MessageAddressResponse> = emptyList(),
    val subject: String,
    val intro: String,
    val seen: Boolean,
    val isDeleted: Boolean,
    val hasAttachments: Boolean,
    val size: Long,
    val downloadUrl: String,
    val createdAt: String,
    val updatedAt: String
)

/**
 * TLS verification information for email security.
 *
 * @property name TLS cipher name
 * @property standardName Standard TLS cipher name
 * @property version TLS version used
 */
@Serializable
data class TlsVerificationResponse(
    val name: String? = null,
    val standardName: String? = null,
    val version: String? = null
)

/**
 * Email verification information including security checks.
 *
 * @property tls TLS encryption information
 * @property spf SPF verification result
 * @property dkim DKIM verification result
 */
@Serializable
data class MessageVerificationsResponse(
    val tls: TlsVerificationResponse? = null,
    val spf: Boolean? = null,
    val dkim: Boolean? = null
)

/**
 * Complete details of an email message.
 *
 * @property id Unique message identifier
 * @property accountId Account ID that owns this message
 * @property msgid Original email Message-ID header
 * @property from Sender information
 * @property to List of recipients
 * @property cc List of CC recipients
 * @property bcc List of BCC recipients
 * @property subject Email subject line
 * @property intro Preview text of the message body
 * @property seen Whether the message has been marked as read
 * @property flagged Whether the message is flagged
 * @property isDeleted Whether the message is marked for deletion
 * @property verifications Security verification results (SPF, DKIM, TLS)
 * @property retention Whether message is subject to retention
 * @property retentionDate ISO 8601 timestamp when message will be deleted
 * @property text Plain text content of the message
 * @property html HTML content parts of the message
 * @property hasAttachments Whether the message contains attachments
 * @property attachments List of message attachments
 * @property size Message size in bytes
 * @property downloadUrl URL to download the raw message
 * @property sourceUrl URL to get message source
 * @property createdAt ISO 8601 timestamp when message was received
 * @property updatedAt ISO 8601 timestamp when message was last updated
 */
@Serializable
data class MessageDetailResponse(
    @SerialName("@context") val context: String? = null,
    @SerialName("@id") val jsonLdId: String? = null,
    @SerialName("@type") val jsonLdType: String? = null,
    val id: String,
    val accountId: String,
    val msgid: String,
    val from: MessageAddressResponse,
    val to: List<MessageAddressResponse> = emptyList(),
    val cc: List<MessageAddressResponse> = emptyList(),
    val bcc: List<MessageAddressResponse> = emptyList(),
    val subject: String,
    val intro: String? = null,
    val seen: Boolean,
    val flagged: Boolean,
    val isDeleted: Boolean,
    val verifications: MessageVerificationsResponse? = null,
    val retention: Boolean,
    val retentionDate: String,
    val text: String,
    val html: List<String> = emptyList(),
    val hasAttachments: Boolean,
    val attachments: List<AttachmentResponse> = emptyList(),
    val size: Long,
    val downloadUrl: String,
    val sourceUrl: String? = null,
    val createdAt: String,
    val updatedAt: String
)

/**
 * Raw email message source data.
 *
 * @property id Source identifier (same as message ID)
 * @property downloadUrl URL to download the raw email source
 * @property data Raw email content as string
 */
@Serializable
data class SourceResponse(
    val id: String,
    val downloadUrl: String,
    val data: String
)

/**
 * Request body for updating message read status.
 *
 * @property seen Whether the message should be marked as read
 */
@Serializable
data class MessageSeenRequest(
    val seen: Boolean
)

/**
 * Rate limiting information from API responses.
 *
 * @property limit Maximum requests per second (typically 8)
 * @property remaining Remaining requests in current window
 * @property reset Timestamp when rate limit window resets
 */
data class RateLimitInfo(
    val limit: Int,
    val remaining: Int,
    val reset: Long
)

/**
 * Server-Sent Events configuration for real-time updates.
 *
 * @property mercureUrl The Mercure hub URL for SSE connections
 * @property accountId Account ID to subscribe to updates for
 */
data class SSEConfig(
    val mercureUrl: String = "https://mercure.mail.tm/.well-known/mercure",
    val accountId: String
)