import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import tm.mail.api.ApiClient
import kotlin.test.Test
import kotlin.test.assertEquals

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
}