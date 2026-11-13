package com.lifehub.app.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.OutputStream
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object ExportFiles {
    const val MIME_JSON = "application/json"
    const val MIME_ZIP = "application/zip"

    fun fileName(at: ZonedDateTime, ext: String): String =
        "lifehub-export-" + at.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + "." + ext

    /** cacheDir/exports/<name> (matches res/xml/file_paths.xml); older exports are deleted first. */
    fun writeToCache(context: Context, name: String, write: (OutputStream) -> Unit): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val f = File(dir, name)
        f.outputStream().use(write)
        return f
    }

    fun shareIntent(context: Context, file: File, mime: String): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, null).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
