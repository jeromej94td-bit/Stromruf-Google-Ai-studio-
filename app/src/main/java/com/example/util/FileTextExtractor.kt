package com.example.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.InputStream
import java.util.zip.ZipInputStream

object FileTextExtractor {

    fun getFileName(context: Context, uri: Uri): String {
        var name: String? = null
        if (uri.scheme == "content") {
            runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) name = cursor.getString(idx)
                    }
                }
            }
        }
        if (name.isNullOrBlank()) {
            name = uri.path?.substringAfterLast('/')
        }
        return name?.takeIf { it.isNotBlank() } ?: "Datei_${System.currentTimeMillis()}"
    }

    fun extractText(context: Context, uri: Uri): Pair<String, String> {
        val fileName = getFileName(context, uri)
        val lowerName = fileName.lowercase()

        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                when {
                    lowerName.endsWith(".docx") -> extractDocx(stream)
                    lowerName.endsWith(".xlsx") -> extractXlsx(stream)
                    lowerName.endsWith(".pdf") -> extractPdf(stream)
                    else -> stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                }
            } ?: ""
        }.getOrElse {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                } ?: ""
            }.getOrDefault("")
        }

        return fileName to text.trim()
    }

    private fun extractDocx(inputStream: InputStream): String {
        val zip = ZipInputStream(inputStream)
        var entry = zip.nextEntry
        val sb = StringBuilder()
        while (entry != null) {
            if (entry.name == "word/document.xml") {
                val xml = zip.bufferedReader(Charsets.UTF_8).readText()
                val text = xml.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ")
                sb.append(text)
                break
            }
            entry = zip.nextEntry
        }
        return sb.toString()
    }

    private fun extractXlsx(inputStream: InputStream): String {
        val zip = ZipInputStream(inputStream)
        var entry = zip.nextEntry
        val sb = StringBuilder()
        while (entry != null) {
            if (entry.name == "xl/sharedStrings.xml") {
                val xml = zip.bufferedReader(Charsets.UTF_8).readText()
                val text = xml.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ")
                sb.append(text)
                break
            }
            entry = zip.nextEntry
        }
        return sb.toString()
    }

    private fun extractPdf(inputStream: InputStream): String {
        val bytes = inputStream.readBytes()
        val raw = String(bytes, Charsets.ISO_8859_1)
        val sb = StringBuilder()

        val btMatcher = java.util.regex.Pattern.compile("BT(.*?)ET", java.util.regex.Pattern.DOTALL).matcher(raw)
        while (btMatcher.find()) {
            val block = btMatcher.group(1) ?: continue
            val textMatcher = java.util.regex.Pattern.compile("\\((.*?)\\)\\s*Tj").matcher(block)
            while (textMatcher.find()) {
                textMatcher.group(1)?.let { sb.append(it).append(" ") }
            }
            val tjArrayMatcher = java.util.regex.Pattern.compile("\\[(.*?)\\]\\s*TJ").matcher(block)
            while (tjArrayMatcher.find()) {
                val arrContent = tjArrayMatcher.group(1) ?: continue
                val innerMatcher = java.util.regex.Pattern.compile("\\((.*?)\\)").matcher(arrContent)
                while (innerMatcher.find()) {
                    innerMatcher.group(1)?.let { sb.append(it) }
                }
                sb.append(" ")
            }
        }
        val result = sb.toString().trim()
        if (result.length > 20) return result

        val fallbackSb = StringBuilder()
        var curLen = 0
        for (b in bytes) {
            val c = b.toInt() and 0xFF
            if (c in 32..126 || c == 10 || c == 13 || c == 9) {
                fallbackSb.append(c.toChar())
                curLen++
            } else {
                if (curLen >= 4) fallbackSb.append(" ")
                curLen = 0
            }
        }
        val filtered = fallbackSb.toString().replace(Regex("\\s+"), " ").trim()
        return filtered.split(" ")
            .filter { !it.startsWith("/") && !it.startsWith("%") && it.length > 1 }
            .joinToString(" ")
    }
}
