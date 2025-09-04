# Mail.tm Kotlin Client

[![Maven Central](https://img.shields.io/maven-central/v/io.github.hasanyalmanbas/mail-tm-client.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.hasanyalmanbas/mail-tm-client)
[![Build](https://github.com/hasanyalmanbas/mail-tm-client/actions/workflows/build.yml/badge.svg)](https://github.com/hasanyalmanbas/mail-tm-client/actions)

> **Status:** Production Ready — Complete implementation with comprehensive error handling and all API endpoints.

## What is it?
A **complete Kotlin Multiplatform (KMP)** client for the [mail.tm](https://api.mail.tm) API, built on **Ktor 3.x** and **kotlinx.serialization**.
- **Complete API coverage** - All mail.tm endpoints implemented
- **Smart error handling** - Mail.tm specific exceptions with detailed error messages
- **Authentication support** - Bearer token management with automatic retry
- **Helper functions** - Convenient methods for common operations
- **Works on Android, iOS** - Full multiplatform support
- **Mockable** with Ktor's `MockEngine` for unit tests

## Installation

### Gradle (Kotlin DSL)
```kotlin
// settings.gradle.kts
repositories {
    google()
    mavenCentral()
}

// build.gradle.kts
dependencies {
    implementation("io.github.hasanyalmanbas:mail-tm-client:1.0.2")
}
```

### Gradle (Groovy)
```gradle
// settings.gradle
repositories {
    google()
    mavenCentral()
}

// build.gradle
dependencies {
    implementation 'io.github.hasanyalmanbas:mail-tm-client:1.0.2'
}
```

## Requirements
- Kotlin **1.9+** (or newer matching your toolchain)
- Ktor **3.x**
- kotlinx.serialization **1.9+**
- KMP targets you plan to build (Android/iOS)

---

## Quick Start

```kotlin
import tm.mail.api.createMailTmClient
import tm.mail.api.ApiClient
import tm.mail.api.mailTmEngine

suspend fun demo() {
    // Simple way - use convenience function
    val client = createMailTmClient()

    // Or create manually with platform engine
    val manualClient = ApiClient(mailTmEngine())

    // Create account and authenticate in one step
    val authenticatedClient = ApiClient.createAccountAndAuthenticate(
        engine = mailTmEngine(),
        address = "user@example.com",
        password = "secure-password"
    )

    // Or authenticate with existing account
    val existingClient = ApiClient.authenticateExisting(
        engine = mailTmEngine(),
        address = "user@example.com",
        password = "secure-password"
    )

    // Get all messages
    val messages = authenticatedClient.getAllMessages()
    println("You have ${messages.size} messages")
}
```

## Advanced Usage

### Create Random Account
```kotlin
val client = ApiClient(mailTmEngine())

// Get a random available domain and create account
val randomAccount = client.createRandomAccount("my-password")
println("Created account: ${randomAccount.address}")

// Authenticate and start using
val token = client.createToken(randomAccount.address, "my-password")
client.setToken(token.token)
```

### Message Management
```kotlin
// Get unread messages only
val unreadMessages = client.getUnreadMessages()

// Get specific message details
val messageDetail = client.getMessageById("msg-id")

// Mark message as read
client.markMessageAsSeen("msg-id")

// Mark all messages as read
client.markAllMessagesAsSeen()

// Delete all messages
client.deleteAllMessages()

// Get message source
val source = client.getMessageSource("msg-id")
```

### Error Handling
```kotlin
try {
    val account = client.createAccount("test@example.com", "password")
} catch (e: MailTmException.AccountAlreadyExists) {
    println("Account already exists: ${e.message}")
    // Access original API response
    println("API Error: ${e.originalResponse?.error}")
} catch (e: MailTmException.InvalidDomain) {
    println("Invalid domain: ${e.message}")
} catch (e: MailTmException.RateLimited) {
    println("Rate limited, try again later")
} catch (e: MailTmException.NetworkError) {
    println("Network error: ${e.message}")
    // Access underlying cause
    e.cause?.printStackTrace()
}
```

---

## Complete API Coverage

### Authentication
- `POST /token` → `createToken(address, password)` - Get auth token
- Token management → `setToken(token)` - Set bearer token for requests

### Account Management
- `POST /accounts` → `createAccount(address, password)` - Create new account
- `GET /accounts/{id}` → `getAccountById(id)` - Get account details
- `DELETE /accounts/{id}` → `deleteAccount(id)` - Delete account
- `GET /me` → `getMe()` - Get current account info

### Domain Management
- `GET /domains` → `getDomains(page?)` - List available domains
- `GET /domains/{id}` → `getDomainById(id)` - Get domain details

### Message Operations
- `GET /messages` → `getMessages(page?)` - List messages
- `GET /messages/{id}` → `getMessageById(id)` - Get message details
- `DELETE /messages/{id}` → `deleteMessage(id)` - Delete message
- `PATCH /messages/{id}` → `markMessageAsSeen(id, seen)` - Mark as read/unread
- `GET /sources/{id}` → `getMessageSource(id)` - Get raw message source

### Helper Functions
- `createAccountAndAuthenticate()` - Create account and login in one step
- `authenticateExisting()` - Login with existing credentials
- `getRandomAvailableDomain()` - Get a random active domain
- `createRandomAccount()` - Create account with random username
- `getAllMessages()` - Get all messages (handles pagination)
- `getUnreadMessages()` - Get only unread messages
- `markAllMessagesAsSeen()` - Mark all messages as read
- `deleteAllMessages()` - Delete all messages

---

## Exception Types

The client provides specific exception types for different error scenarios:

### HTTP Status Based
- `MailTmException.BadRequest` - 400 Bad Request
- `MailTmException.Unauthorized` - 401 Unauthorized
- `MailTmException.NotFound` - 404 Not Found
- `MailTmException.Conflict` - 409 Conflict
- `MailTmException.UnprocessableEntity` - 422 Validation Error
- `MailTmException.RateLimited` - 429 Too Many Requests
- `MailTmException.Server` - 5xx Server Errors

### Mail.tm Specific
- `MailTmException.AccountAlreadyExists` - Email already registered
- `MailTmException.InvalidCredentials` - Wrong email/password
- `MailTmException.InvalidDomain` - Domain not valid
- `MailTmException.AccountDisabled` - Account is disabled
- `MailTmException.MessageNotFound` - Message doesn't exist
- `MailTmException.DomainNotAvailable` - Domain not available
- `MailTmException.QuotaExceeded` - Storage quota exceeded

### Network Related
- `MailTmException.NetworkError` - Connection/network issues
- `MailTmException.TimeoutError` - Request timeout

All exceptions include the original API error response when available:
```kotlin
catch (e: MailTmException) {
    println("Error: ${e.message}")
    e.originalResponse?.let { response ->
        println("API Error: ${response.error}")
        println("Violations: ${response.violations}")
    }
}
```

---

## Testing

The client is fully mockable using Ktor's `MockEngine`:

```kotlin
@Test
fun testCreateAccount() = runBlocking {
        val mockEngine = MockEngine { request ->
            respond(
                content = ByteReadChannel("""{"id":"123","address":"test@example.com"}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = ApiClient(mockEngine)
        val account = client.createAccount("test@example.com", "password")

        assertEquals("123", account.id)
        assertEquals("test@example.com", account.address)
    }
```

---

## Contributing

1. Fork the project
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

---

**Production Ready:** This client provides complete mail.tm API coverage with robust error handling and convenient helper functions.