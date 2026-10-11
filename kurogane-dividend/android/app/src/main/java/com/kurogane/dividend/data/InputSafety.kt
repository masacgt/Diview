package com.kurogane.dividend.data

import kotlinx.coroutines.CancellationException
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Keeps cancellation visible to callers and does not swallow VM errors. */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Result.failure(error)
}

fun InputStream.readBoundedBytes(maxBytes: Int): ByteArray {
    require(maxBytes > 0)
    val output = ByteArrayOutputStream(minOf(maxBytes, 8192))
    val buffer = ByteArray(8192)
    while (true) {
        val remainingWithProbe = maxBytes.toLong() - output.size() + 1L
        val count = read(buffer, 0, minOf(buffer.size.toLong(), remainingWithProbe).toInt())
        if (count < 0) break
        if (count == 0) {
            val byte = read()
            if (byte < 0) break
            require(output.size() < maxBytes) { "ファイルがサイズ上限を超えています" }
            output.write(byte)
            continue
        }
        require(count <= maxBytes - output.size()) { "ファイルがサイズ上限を超えています" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
