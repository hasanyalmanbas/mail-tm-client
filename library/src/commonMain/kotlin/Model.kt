package tm.mail.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class HydraCollection<T>(
    @SerialName("hydra:member") val items: List<T> = emptyList(),
    @SerialName("hydra:totalItems") val totalItems: Int = 0
)

@Serializable
data class DomainDto(
    val id: String,
    val domain: String,
    val isActive: Boolean,
    val isPrivate: Boolean,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class AccountCreateBody(val address: String, val password: String)

@Serializable
data class AccountDto(
    val id: String,
    val address: String,
    val quota: Long,
    val used: Long,
    val isDisabled: Boolean,
    val isDeleted: Boolean,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class TokenBody(val address: String, val password: String)

@Serializable
data class TokenResponse(
    val id: String,
    val token: String
)

@Serializable
data class MessageAddressDto(val name: String, val address: String)

@Serializable
data class AttachmentDto(
    val id: String,
    val filename: String,
    val contentType: String,
    val disposition: String,
    val transferEncoding: String,
    val related: Boolean,
    val size: Long,
    val downloadUrl: String
)

@Serializable
data class MessageSummaryDto(
    val id: String,
    val accountId: String,
    val msgid: String,
    val from: MessageAddressDto,
    val to: List<MessageAddressDto> = emptyList(),
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

@Serializable
data class MessageDetailDto(
    val id: String,
    val accountId: String,
    val msgid: String,
    val from: MessageAddressDto,
    val to: List<MessageAddressDto> = emptyList(),
    val cc: List<String> = emptyList(),
    val bcc: List<String> = emptyList(),
    val subject: String,
    val seen: Boolean,
    val flagged: Boolean,
    val verifications: List<String> = emptyList(),
    val retention: Boolean,
    val retentionDate: String,
    val text: String,
    val html: List<String> = emptyList(),
    val hasAttachments: Boolean,
    val attachments: List<AttachmentDto> = emptyList(),
    val size: Long,
    val downloadUrl: String,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class SourceDto(
    val id: String,
    val downloadUrl: String,
    val data: String
)

@Serializable
data class PatchMessageSeen(val seen: Boolean)