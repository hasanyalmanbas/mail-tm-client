package tm.mail.api.test

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import tm.mail.api.ApiClient
import tm.mail.api.MailTmException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ApiClientTest {
    @Test
    fun createAccountTest() = runBlocking {
        val expectedAddress = "user@example.com"
        val expectedPassword = "pass-1234"

        val engine = MockEngine { req ->
            respond(
                content = ByteReadChannel(
                    """
                    {
                      "id": "acc_1",
                      "address": "$expectedAddress",
                      "quota": 10485760,
                      "used": 0,
                      "isDisabled": false,
                      "isDeleted": false,
                      "createdAt": "2025-08-20T14:30:19.955Z",
                      "updatedAt": "2025-08-20T14:30:19.955Z"
                    }
                    """.trimIndent()
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val apiClient = ApiClient(engine)

        val account =
            apiClient.createAccount(address = expectedAddress, password = expectedPassword)

        assertEquals("acc_1", account.id)
        assertEquals(expectedAddress, account.address)
        assertEquals(false, account.isDisabled)
        assertEquals(false, account.isDeleted)
    }

    @Test
    fun getDomainsTest() {
        runBlocking {
            val mockEngine = MockEngine { request ->
                respond(
                    content = ByteReadChannel(
                        """
                      {
                      "hydra:member":[
                        {
                          "id":"d1",
                          "domain":"example.com",
                          "isActive":true,
                          "isPrivate":true,
                          "createdAt":"2025-08-20T14:30:19.955Z",
                          "updatedAt":"2025-08-20T14:30:19.955Z"
                        }
                      ],
                      "hydra:totalItems":1
                    }
                    """.trimIndent()
                    ),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
            val apiClient = ApiClient(mockEngine)

            val res = apiClient.getDomains()

            assertEquals(1, res.items.size)
            assertEquals("example.com", res.items.first().domain)
            assertEquals(1, res.totalItems)
        }
    }

    @Test
    fun createTokenTest() = runBlocking {
        val expectedAddress = "user@example.com"
        val expectedPassword = "pass-1234"
        val expectedToken = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpYXQiOjE2OTI1MzY3NzksImV4cCI6MTY5MjU0Mzk3OSwicm9sZXMiOlsiUk9MRV9VU0VSIl0sInVzZXJuYW1lIjoidXNlckBleGFtcGxlLmNvbSJ9.token"

        val engine = MockEngine { req ->
            respond(
                content = ByteReadChannel(
                    """
                    {
                      "id": "token_1",
                      "token": "$expectedToken"
                    }
                    """.trimIndent()
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val apiClient = ApiClient(engine)
        val tokenResponse = apiClient.createToken(expectedAddress, expectedPassword)

        assertEquals(expectedToken, tokenResponse.token)
        assertEquals("token_1", tokenResponse.id)
    }

    @Test
    fun getMeTest() = runBlocking {
        val engine = MockEngine { req ->
            respond(
                content = ByteReadChannel(
                    """
                    {
                      "id": "acc_1",
                      "address": "user@example.com",
                      "quota": 10485760,
                      "used": 12345,
                      "isDisabled": false,
                      "isDeleted": false,
                      "createdAt": "2025-08-20T14:30:19.955Z",
                      "updatedAt": "2025-08-20T14:30:19.955Z"
                    }
                    """.trimIndent()
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val apiClient = ApiClient(engine)
        val account = apiClient.getMe()

        assertEquals("acc_1", account.id)
        assertEquals("user@example.com", account.address)
        assertEquals(12345L, account.used)
    }

    @Test
    fun getMessagesTest() = runBlocking {
        val engine = MockEngine { req ->
            respond(
                content = ByteReadChannel(
                    """
                    {
                      "hydra:member": [
                        {
                          "id": "msg_1",
                          "accountId": "acc_1",
                          "msgid": "msg123",
                          "from": {
                            "name": "Sender Name",
                            "address": "sender@example.com"
                          },
                          "to": [
                            {
                              "name": "Recipient",
                              "address": "user@example.com"
                            }
                          ],
                          "subject": "Test Message",
                          "intro": "This is a test message",
                          "seen": false,
                          "hasAttachments": false,
                          "size": 1234,
                          "createdAt": "2025-08-20T15:30:19.955Z"
                        }
                      ],
                      "hydra:totalItems": 1
                    }
                    """.trimIndent()
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val apiClient = ApiClient(engine)
        val messages = apiClient.getMessages()

        assertEquals(1, messages.items.size)
        assertEquals("msg_1", messages.items.first().id)
        assertEquals("Test Message", messages.items.first().subject)
        assertEquals(false, messages.items.first().seen)
    }

    @Test
    fun errorHandlingTest() {
        val engine = MockEngine { req ->
            respond(
                content = ByteReadChannel("{\"error\": \"Invalid credentials provided\"}"),
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val apiClient = ApiClient(engine)

        runBlocking {
            assertFailsWith<MailTmException.InvalidCredentials> {
                apiClient.getMe()
            }
        }
    }

    @Test
    fun rateLimitErrorTest() {
        val engine = MockEngine { req ->
            respond(
                content = ByteReadChannel("{\"error\": \"Rate limited\"}"),
                status = HttpStatusCode.TooManyRequests,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val apiClient = ApiClient(engine)

        runBlocking {
            assertFailsWith<MailTmException.RateLimited> {
                apiClient.getDomains()
            }
        }
    }

    @Test
    fun accountAlreadyExistsErrorTest() {
        val engine = MockEngine { req ->
            respond(
                content = ByteReadChannel("{\"error\": \"Account with this address already exists\"}"),
                status = HttpStatusCode.BadRequest,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val apiClient = ApiClient(engine)

        runBlocking {
            val exception = assertFailsWith<MailTmException.AccountAlreadyExists> {
                apiClient.createAccount("test@example.com", "password123")
            }
            assertEquals("Account with this address already exists", exception.message)
            assertEquals("Account with this address already exists", exception.originalResponse?.error)
        }
    }

    @Test
    fun validationErrorTest() {
        val engine = MockEngine { req ->
            respond(
                content = ByteReadChannel(
                    """
                    {
                      "@type": "hydra:Error",
                      "hydra:title": "Validation Error",
                      "hydra:description": "Invalid input data",
                      "violations": [
                        {
                          "propertyPath": "address",
                          "message": "Invalid email format",
                          "code": "INVALID_FORMAT"
                        }
                      ]
                    }
                    """.trimIndent()
                ),
                status = HttpStatusCode.UnprocessableEntity,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val apiClient = ApiClient(engine)

        runBlocking {
            val exception = assertFailsWith<MailTmException.UnprocessableEntity> {
                apiClient.createAccount("invalid-email", "password123")
            }
            assertEquals("Validation errors: Invalid email format", exception.message)
            assertEquals(1, exception.originalResponse?.violations?.size)
        }
    }

    @Test
    fun messageNotFoundErrorTest() {
        val engine = MockEngine { req ->
            respond(
                content = ByteReadChannel("{\"error\": \"Message not found\"}"),
                status = HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val apiClient = ApiClient(engine)

        runBlocking {
            assertFailsWith<MailTmException.MessageNotFound> {
                apiClient.getMessageById("nonexistent-id")
            }
        }
    }
}