package com.kurogane.dividend.data

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Only this directory is exposed by FileProvider. No storage permission is required. */
class RoadmapImageStore(private val context: Context) {
    private val directory get() = File(context.cacheDir, "roadmap-images").apply {
        if (!isDirectory && !mkdirs()) throw IOException("画像用フォルダーを作成できませんでした")
    }

    fun createPng(bitmap: Bitmap): File {
        val folder = directory
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        folder.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
        val date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val file = File(folder, "Diview-$date-${UUID.randomUUID().toString().take(8)}.png")
        try {
            file.outputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "画像を生成できませんでした" }
            }
            return file
        } catch (error: Throwable) { file.delete(); throw error }
    }

    fun pendingFile(name: String): File {
        require(name == File(name).name && name.endsWith(".png"))
        return File(directory, name).also { check(it.isFile && it.length() > 0) { "画像を再生成してください" } }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun saveToPictures(file: File): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Diview")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("保存先を作成できませんでした")
        try {
            writeDocument(file, uri)
            check(resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null) == 1)
            return uri
        } catch (error: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
    }

    fun writeDocument(file: File, uri: Uri) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
            file.inputStream().use { it.copyTo(output) }
        } ?: throw IOException("保存先を開けませんでした")
    }

    fun shareIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.roadmap-images", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, "Diview", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
