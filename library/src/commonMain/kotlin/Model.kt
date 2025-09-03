package tm.mail.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class HydraCollection<T>(
    @SerialName("hydra:member") val items: List<T> = emptyList(),
    @SerialName("hydra:totalItems") val totalItems: Int = 0
)

@Serializable
data class Domain(
    val id: String,
    val domain: String,
    val isActive: Boolean,
    val isPrivate: Boolean,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

@Serializable
data class AccountCreateBody(val address: String, val password: String)

@Serializable
data class Account(
    val id: String,
    val address: String,
    val quota: Long? = null,
    val used: Long? = null,
    val isDisabled: Boolean? = null,
    val isDeleted: Boolean? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

@Serializable
data class TokenBody(val address: String, val password: String)

@Serializable
data class TokenResponse(
    val id: String? = null,
    val token: String
)

@Serializable
data class MessageAddress(val name: String? = null, val address: String? = null)

@Serializable
data class Attachment(
    val id: String,
    val filename: String? = null,
    val contentType: String? = null,
    val disposition: String? = null,
    val transferEncoding: String? = null,
    val related: Boolean? = null,
    val size: Long? = null,
    val downloadUrl: String? = null
)

@Serializable
data class MessageListItem(
    val id: String,
    val accountId: String? = null,
    val msgid: String? = null,
    val from: MessageAddress? = null,
    val to: List<MessageAddress> = emptyList(),
    val subject: String? = null,
    val intro: String? = null,
    val seen: Boolean? = null,
    val isDeleted: Boolean? = null,
    val hasAttachments: Boolean? = null,
    val size: Long? = null,
    val downloadUrl: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

@Serializable
data class MessageDetail(
    val id: String,
    val accountId: String? = null,
    val msgid: String? = null,
    val from: MessageAddress? = null,
    val to: List<MessageAddress> = emptyList(),
    val cc: List<String> = emptyList(),
    val bcc: List<String> = emptyList(),
    val subject: String? = null,
    val seen: Boolean? = null,
    val flagged: Boolean? = null,
    val verifications: List<String> = emptyList(),
    val retention: Boolean? = null,
    val retentionDate: String? = null,
    val text: String? = null,
    val html: List<String> = emptyList(),
    val hasAttachments: Boolean? = null,
    val attachments: List<Attachment> = emptyList(),
    val size: Long? = null,
    val downloadUrl: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

@Serializable
data class Source(
    val id: String,
    val downloadUrl: String? = null,
    val data: String? = null
)

@Serializable
data class PatchMessageSeen(val seen: Boolean)