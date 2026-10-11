package com.kurogane.dividend.data

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.SocketTimeoutException

class ServiceHttpTest {
    private class Connection(private val status: Int = 200, private val body: String = "{}", private val timeout: Boolean = false) : HttpURLConnection(URL("https://example.com")) {
        var disconnected = false
        val sent = ByteArrayOutputStream()
        override fun getResponseCode(): Int { if (timeout) throw SocketTimeoutException(); return status }
        override fun getInputStream() = ByteArrayInputStream(body.toByteArray())
        override fun getOutputStream() = sent
        override fun disconnect() { disconnected = true }
        override fun connect() {}
        override fun usingProxy() = false
    }
    @Test fun postsJapaneseTextAndChecksReceipt() = runBlocking {
        val id = "d982c634-a464-4a62-a266-35a1ee8c3821"
        val connection = Connection(201, """{"accepted":true,"receiptId":"$id"}""")
        val client = FeedbackRepository(ServiceHttp { connection }, "https://example.com")
        assertEquals(id, client.submit(JSONObject().put("requestId", id).put("message", "お問い合わせ")))
        assertTrue(connection.sent.toString(Charsets.UTF_8.name()).contains("お問い合わせ"))
        assertEquals("POST", connection.requestMethod)
        assertFalse(connection.instanceFollowRedirects)
        assertTrue(connection.disconnected)
    }
    @Test fun rejectsUnconfirmedSuccess() {
        val connection = Connection(200, """{"accepted":true,"receiptId":"another"}""")
        assertThrows(IllegalArgumentException::class.java) { runBlocking {
            FeedbackRepository(ServiceHttp { connection }, "https://example.com").submit(JSONObject().put("requestId", "d982c634-a464-4a62-a266-35a1ee8c3821").put("message", "test"))
        } }
    }
    @Test fun errorsTimeoutMalformedJsonAndOversizeDisconnect() {
        for (connection in listOf(Connection(429), Connection(503), Connection(timeout = true), Connection(body = "broken"), Connection(body = "x".repeat(1_000_001)))) {
            assertThrows(Exception::class.java) { runBlocking { ServiceHttp { connection }.json("https://example.com") } }
            assertTrue(connection.disconnected)
        }
    }
    @Test fun rejectsInsecureAndEmbeddedCredentialUrls() {
        for (url in listOf("http://example.com", "https://secret@example.com", "https://example.com#fragment")) {
            assertThrows(IllegalArgumentException::class.java) { runBlocking { ServiceHttp { error("must not connect") }.json(url) } }
        }
    }
    @Test fun validatesCurrencyFiniteRateAndDate() {
        fun rate(base: String = "USD", value: Any = 158.0, date: String = "2026-01-01") = JSONObject().put("base", base).put("quote", "JPY").put("rate", value).put("date", date)
        assertEquals(158.0, ExchangeRate.parse(rate()).usdJpy, 0.0)
        for (input in listOf(rate(base = "EUR"), rate(value = -1), rate(value = 1001), rate(date = "2099-01-01"), rate(date = "broken"))) {
            assertThrows(Exception::class.java) { ExchangeRate.parse(input) }
        }
    }
}
