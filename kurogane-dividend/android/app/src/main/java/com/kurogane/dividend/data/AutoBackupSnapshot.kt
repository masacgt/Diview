package com.kurogane.dividend.data

import android.util.AtomicFile
import android.util.Base64
import android.util.JsonReader
import android.util.JsonToken
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** Version-one JSON stays portable: device-bound Keystore ciphertext is not a transfer format. */
internal class AutoBackupSnapshot(file: File) {
    private val atomicFile = AtomicFile(file)
    private val legacyBackupFile = File(file.path + ".bak")

    fun exists(): Boolean = synchronized(LOCK) { atomicFile.baseFile.isFile || legacyBackupFile.isFile }

    fun delete() = synchronized(LOCK) { atomicFile.delete() }

    fun readHoldings(): JSONArray? = readField("holdings") { it.readJsonArray() }
    fun readWatchlist(): JSONArray? = readField("watchlist") { it.readJsonArray() }

    fun readBackup(kind: DvmBackupKind): ByteArray? = readField("dvmBackups") { reader ->
        var payload: ByteArray? = null
        reader.beginObject()
        while (reader.hasNext()) {
            if (reader.nextName() == kind.name) {
                val encoded = reader.nextString()
                if (encoded.isNotBlank()) payload = Base64.decode(encoded, Base64.NO_WRAP)
            } else reader.skipValue()
        }
        reader.endObject()
        payload
    }

    /** Skip unrelated payloads instead of allocating the entire snapshot as one JSON string. */
    private fun <T> readField(name: String, read: (JsonReader) -> T): T? = synchronized(LOCK) {
        if (!exists()) return null
        try {
            JsonReader(atomicFile.openRead().bufferedReader(Charsets.UTF_8)).use { reader ->
                var version: Int? = null
                var value: T? = null
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "version" -> version = reader.nextInt()
                        name -> value = read(reader)
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                check(version == 1 && reader.peek() == JsonToken.END_DOCUMENT)
                value
            }
        } catch (failure: Exception) {
            // The existing restore-error UI handles this; never replace a damaged backup with defaults.
            android.util.Log.w("AutoBackupSnapshot", "Read failed: ${failure.javaClass.simpleName}")
            throw IOException("自動バックアップを読み込めませんでした", failure)
        }
    }

    fun write(holdings: JSONArray, watchlist: JSONArray, backup: (DvmBackupKind) -> ByteArray?) = synchronized(LOCK) {
        val stream = atomicFile.startWrite()
        try {
            // finishWrite/failWrite own the stream, so the buffered writer is flushed before finishing.
            val writer = stream.bufferedWriter(Charsets.UTF_8)
            writer.write("{\"version\":1,\"holdings\":")
            writer.write(holdings.toString())
            writer.write(",\"watchlist\":")
            writer.write(watchlist.toString())
            writer.write(",\"dvmBackups\":{")
            var separator = ""
            DvmBackupKind.entries.forEach { kind ->
                backup(kind)?.let { bytes ->
                    writer.write(separator)
                    writer.write("\"${kind.name}\":\"")
                    writer.write(Base64.encodeToString(bytes, Base64.NO_WRAP))
                    writer.write("\"")
                    separator = ","
                }
            }
            writer.write("}}")
            writer.flush()
            atomicFile.finishWrite(stream)
        } catch (failure: Throwable) {
            atomicFile.failWrite(stream)
            android.util.Log.w("AutoBackupSnapshot", "Write failed: ${failure.javaClass.simpleName}")
            throw failure
        }
    }

    private fun JsonReader.readJsonValue(depth: Int = 0): Any {
        require(depth <= 32) { "自動バックアップの形式が正しくありません" }
        return when (peek()) {
            JsonToken.BEGIN_OBJECT -> JSONObject().also { json ->
                beginObject()
                while (hasNext()) json.put(nextName(), readJsonValue(depth + 1))
                endObject()
            }
            JsonToken.BEGIN_ARRAY -> readJsonArray(depth)
            JsonToken.STRING -> nextString()
            JsonToken.NUMBER -> nextString().let { it.toLongOrNull() ?: it.toDouble() }
            JsonToken.BOOLEAN -> nextBoolean()
            JsonToken.NULL -> { nextNull(); JSONObject.NULL }
            else -> throw IOException("自動バックアップの形式が正しくありません")
        }
    }

    private fun JsonReader.readJsonArray(depth: Int = 0) = JSONArray().also { json ->
        beginArray()
        while (hasNext()) json.put(readJsonValue(depth + 1))
        endArray()
    }

    private companion object {
        // AtomicFile does not provide synchronization between store instances in this process.
        val LOCK = Any()
    }
}
