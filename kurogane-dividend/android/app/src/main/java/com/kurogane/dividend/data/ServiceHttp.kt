package com.kurogane.dividend.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.io.IOException

class ServiceHttp(private val connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }) {
    suspend fun json(url: String, body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        val uri = URI(url)
        require(uri.scheme == "https" && uri.host != null && uri.userInfo == null && uri.fragment == null)
        val connection = connectionFactory(uri.toURL())
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 8_000
            connection.readTimeout = 12_000
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                val bytes = body.toString().toByteArray(Charsets.UTF_8)
                require(bytes.size <= 100_000)
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val status = connection.responseCode
            if (status !in 200..299) throw ServiceException(status)
            JSONObject(connection.inputStream.use { it.readBoundedBytes(1_000_000).toString(Charsets.UTF_8) })
        } finally { connection.disconnect() }
    }
}

class ServiceException(val status: Int) : IOException(when (status) {
    429 -> "送信回数の上限に達しました。時間をおいて再試行してください。"
    400, 413 -> "入力内容を確認してください。"
    else -> "サーバーへ接続できませんでした。再試行してください。"
})

data class ExchangeRate(val usdJpy: Double, val date: String) {
    companion object {
        fun parse(json: JSONObject): ExchangeRate {
            require(json.getString("base") == "USD" && json.getString("quote") == "JPY")
            val rate = json.getDouble("rate")
            require(rate.isFinite() && rate in 1.0..1000.0)
            val date = java.time.LocalDate.parse(json.getString("date"))
            require(!date.isAfter(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Tokyo"))))
            return ExchangeRate(rate, date.toString())
        }
    }
}

class ExchangeRateRepository(private val http: ServiceHttp = ServiceHttp()) {
    suspend fun latest(): ExchangeRate = ExchangeRate.parse(http.json("https://api.frankfurter.dev/v2/rate/USD/JPY"))
}

class FeedbackRepository(private val http: ServiceHttp = ServiceHttp(), private val baseUrl: String = com.kurogane.dividend.BuildConfig.DIVIEW_D1_API_BASE_URL) {
    suspend fun submit(draft: JSONObject): String {
        require(draft.optString("message").trim().length in 1..20_000)
        val id = draft.getString("requestId")
        require(id.matches(Regex("[a-f0-9-]{36}")))
        val result = http.json(baseUrl.trimEnd('/') + "/api/v1/feedback", draft)
        require(result.getString("receiptId") == id && result.getBoolean("accepted")) { "受付結果を確認できませんでした。再試行してください。" }
        return id
    }
}
