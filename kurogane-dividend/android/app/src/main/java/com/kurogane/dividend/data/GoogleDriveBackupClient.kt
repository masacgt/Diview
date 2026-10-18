package com.kurogane.dividend.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Small Drive v3 REST client. Call from Dispatchers.IO and pass a short-lived OAuth access token. */
object GoogleDriveBackupClient {
    private const val API = "https://www.googleapis.com/drive/v3/files"
    private const val UPLOAD_API = "https://www.googleapis.com/upload/drive/v3/files"
    private const val MAX_BYTES = 50 * 1024 * 1024

    fun upload(accessToken: String, fileName: String, content: ByteArray) {
        require(content.size <= MAX_BYTES) { "DVMバックアップが大きすぎます" }
        val existingId = findFile(accessToken, fileName)
        if (existingId != null) {
            execute(
                "PATCH", "$UPLOAD_API/${encodePath(existingId)}?uploadType=media", accessToken,
                content, "application/octet-stream"
            )
            return
        }

        val boundary = "DiviewDvm${System.currentTimeMillis()}"
        val metadata = JSONObject()
            .put("name", fileName)
            .put("mimeType", "application/octet-stream")
            .put("parents", JSONArray().put("root"))
            .toString().toByteArray(Charsets.UTF_8)
        val body = ByteArrayOutputStream(content.size + metadata.size + 256).apply {
            write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n".toByteArray(Charsets.UTF_8))
            write(metadata)
            write("\r\n--$boundary\r\nContent-Type: application/octet-stream\r\n\r\n".toByteArray(Charsets.UTF_8))
            write(content)
            write("\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8))
        }.toByteArray()
        execute(
            "POST", "$UPLOAD_API?uploadType=multipart&fields=id,name", accessToken,
            body, "multipart/related; boundary=$boundary"
        )
    }

    fun download(accessToken: String, fileName: String): ByteArray? {
        val fileId = findFile(accessToken, fileName) ?: return null
        return execute(
            "GET", "$API/${encodePath(fileId)}?alt=media", accessToken, null, null, MAX_BYTES
        )
    }

    private fun findFile(accessToken: String, fileName: String): String? {
        val query = "name = '${fileName.replace("'", "\\'")}' and 'root' in parents and trashed = false"
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val response = execute(
            "GET", "$API?q=$encodedQuery&spaces=drive&pageSize=100&orderBy=modifiedTime%20desc&fields=files(id,name,modifiedTime)",
            accessToken, null, null, MAX_BYTES
        )
        val files = JSONObject(response.toString(Charsets.UTF_8)).optJSONArray("files") ?: return null
        return (0 until files.length()).asSequence()
            .mapNotNull { files.optJSONObject(it)?.optString("id")?.takeIf(String::isNotBlank) }
            .firstOrNull()
    }

    private fun execute(
        method: String,
        url: String,
        accessToken: String,
        body: ByteArray?,
        contentType: String?,
        maxResponseBytes: Int = 1024 * 1024
    ): ByteArray {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 40_000
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json, application/octet-stream")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", contentType ?: "application/octet-stream")
                setFixedLengthStreamingMode(body.size)
            }
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body) }
            val status = connection.responseCode
            if (status !in 200..299) {
                connection.errorStream?.close()
                throw IllegalStateException("Google Driveとの通信に失敗しました（HTTP $status）")
            }
            val length = connection.contentLengthLong
            require(length <= maxResponseBytes || length < 0) { "DVMバックアップが大きすぎます" }
            return connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= maxResponseBytes) { "DVMバックアップが大きすぎます" }
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun encodePath(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
