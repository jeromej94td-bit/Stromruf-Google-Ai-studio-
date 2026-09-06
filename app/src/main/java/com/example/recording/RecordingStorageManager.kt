package com.example.recording

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages storage destination for SmartCalls recordings.
 * Supports:
 * - App-internal default storage
 * - Custom local folder via Storage Access Framework (SAF)
 * - Google Drive target folder (selected via SAF Google Drive provider)
 * - Auto-export of new call recordings
 * - Share & direct upload / export to Google Drive and other apps
 */
class RecordingStorageManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("smart_calls_storage_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "RecordingStorageMgr"
        private const val KEY_CUSTOM_URI = "custom_recordings_tree_uri"
        private const val KEY_FOLDER_NAME = "custom_recordings_folder_name"
        private const val KEY_AUTO_EXPORT = "auto_export_to_target_folder"
        private const val KEY_IS_GDRIVE = "is_google_drive_folder"
    }

    /**
     * Get the configured custom folder URI if any.
     */
    fun getCustomFolderUri(): Uri? {
        val raw = prefs.getString(KEY_CUSTOM_URI, null) ?: return null
        return try {
            Uri.parse(raw)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get the readable name of the storage destination.
     */
    fun getStorageDisplayName(): String {
        val uri = getCustomFolderUri() ?: return "📱 Interner App-Speicher"
        val savedName = prefs.getString(KEY_FOLDER_NAME, null)
        if (!savedName.isNullOrBlank()) {
            return if (isGoogleDrive()) "☁️ Google Drive: $savedName" else "📁 $savedName"
        }
        val doc = DocumentFile.fromTreeUri(context, uri)
        val name = doc?.name ?: "Ausgewählter Zielordner"
        return if (isGoogleDrive()) "☁️ Google Drive: $name" else "📁 $name"
    }

    /**
     * Check if the currently chosen folder belongs to Google Drive.
     */
    fun isGoogleDrive(): Boolean {
        if (prefs.getBoolean(KEY_IS_GDRIVE, false)) return true
        val uri = getCustomFolderUri() ?: return false
        val uriStr = uri.toString().lowercase()
        return uriStr.contains("com.google.android.apps.docs.storage") ||
                uriStr.contains("googledrive") ||
                uriStr.contains("drive")
    }

    /**
     * Whether newly finished SmartCalls should automatically be saved into the chosen target folder.
     */
    fun isAutoExportEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_EXPORT, true)
    }

    fun setAutoExportEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_EXPORT, enabled).apply()
    }

    /**
     * Save the user selected Tree Uri and take persistable permissions.
     */
    fun setCustomFolderUri(uri: Uri): Boolean {
        return try {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (e: SecurityException) {
                Log.w(TAG, "Could not take persistable permission: ${e.message}")
                return false
            }

            val doc = DocumentFile.fromTreeUri(context, uri)
            if (doc?.isDirectory != true || !doc.canWrite()) return false
            val folderName = doc?.name ?: "Zielordner"
            val uriStr = uri.toString().lowercase()
            val isDrive = uriStr.contains("com.google.android.apps.docs.storage") ||
                    uriStr.contains("googledrive") ||
                    uriStr.contains("drive") ||
                    folderName.lowercase().contains("drive")

            prefs.edit().apply {
                putString(KEY_CUSTOM_URI, uri.toString())
                putString(KEY_FOLDER_NAME, folderName)
                putBoolean(KEY_IS_GDRIVE, isDrive)
                apply()
            }
            Log.d(TAG, "Custom folder set: $folderName (Drive: $isDrive)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save custom folder URI", e)
            false
        }
    }

    /**
     * Reset to internal default storage.
     */
    fun resetToDefault() {
        prefs.edit().apply {
            remove(KEY_CUSTOM_URI)
            remove(KEY_FOLDER_NAME)
            remove(KEY_IS_GDRIVE)
            apply()
        }
    }

    /**
     * Copy a local recording file to the configured custom folder (Google Drive or Local Folder).
     */
    suspend fun saveFileToCustomFolder(sourceFile: File): Result<String> = withContext(Dispatchers.IO) {
        val treeUri = getCustomFolderUri()
            ?: return@withContext Result.failure(IllegalStateException("Kein Zielordner festgelegt."))

        if (!sourceFile.exists() || sourceFile.length() == 0L) {
            return@withContext Result.failure(IllegalArgumentException("Quelldatei existiert nicht oder ist leer."))
        }

        try {
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
                ?: return@withContext Result.failure(IllegalStateException("Zielordner konnte nicht geöffnet werden."))

            if (!rootDoc.canWrite()) {
                return@withContext Result.failure(IllegalStateException("Keine Schreibrechte im Zielordner."))
            }

            val mimeType = when {
                sourceFile.name.endsWith(".wav", true) -> "audio/wav"
                sourceFile.name.endsWith(".m4a", true) -> "audio/mp4"
                else -> "audio/mp4"
            }

            // Create or replace target file
            val existing = rootDoc.findFile(sourceFile.name)
            val targetDoc = existing ?: rootDoc.createFile(mimeType, sourceFile.name)
                ?: return@withContext Result.failure(IllegalStateException("Datei im Zielordner konnte nicht erstellt werden."))

            context.contentResolver.openOutputStream(targetDoc.uri, "wt")?.use { outStream ->
                FileInputStream(sourceFile).use { inStream ->
                    inStream.copyTo(outStream)
                }
            } ?: return@withContext Result.failure(IllegalStateException("OutputStream konnte nicht geöffnet werden."))

            val destName = targetDoc.name ?: sourceFile.name
            Log.d(TAG, "Saved recording to custom folder: $destName (${sourceFile.length()} bytes)")
            Result.success(destName)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving recording to custom folder", e)
            Result.failure(e)
        }
    }

    /**
     * Copies all given files to the custom target folder.
     */
    suspend fun copyAllToCustomFolder(files: List<File>): Pair<Int, List<String>> = withContext(Dispatchers.IO) {
        var successCount = 0
        val errors = mutableListOf<String>()
        files.forEach { file ->
            val res = saveFileToCustomFolder(file)
            if (res.isSuccess) {
                successCount++
            } else {
                errors.add("${file.name}: ${res.exceptionOrNull()?.message ?: "Fehler"}")
            }
        }
        Pair(successCount, errors)
    }

    /**
     * Share or export an audio recording via the Android Share sheet
     * (Allows immediate upload to Google Drive, Telegram, Gmail, WhatsApp, or File Manager).
     */
    fun shareRecording(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val mimeType = when {
                file.name.endsWith(".wav", true) -> "audio/wav"
                file.name.endsWith(".m4a", true) -> "audio/mp4"
                else -> "audio/mp4"
            }

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "SmartCall Aufnahme: ${file.name}")
                putExtra(Intent.EXTRA_TEXT, "Aufnahme vom SmartCall: ${file.name}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(intent, "Aufnahme teilen / In Google Drive speichern").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing recording", e)
        }
    }
}
