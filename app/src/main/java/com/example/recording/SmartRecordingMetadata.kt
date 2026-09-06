package com.example.recording

import android.content.Context
import com.example.database.AppDatabase
import org.json.JSONObject
import java.io.File

/** Customer identifiers come from stored records, never from an AI guess. */
object SmartRecordingMetadata {
    private fun normalized(phone: String): String {
        val digits = phone.filter(Char::isDigit)
        return when {
            digits.startsWith("0049") -> "0" + digits.drop(4)
            digits.startsWith("49") -> "0" + digits.drop(2)
            else -> digits
        }
    }

    suspend fun resolve(context: Context, phone: String, name: String?): JSONObject {
        val dao = AppDatabase.getDatabase(context).stromrufDao()
        val key = normalized(phone)
        val contact = dao.getAllContactsList().filter { normalized(it.phone) == key }.singleOrNull()
        val numbers = buildList {
            addAll(dao.getAllNeukundenList().filter { normalized(it.phone) == key }.map { it.customerNumber })
            addAll(dao.getAllHeisseAngeboteList().filter { normalized(it.phone) == key }.map { it.customerNumber })
            addAll(dao.getPromisedAnnahmenList().filter { normalized(it.phone) == key }.map { it.customerNumber })
        }.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        return JSONObject().put("phone", phone).put("contactName", contact?.name ?: name.orEmpty())
            .put("contactId", contact?.id.orEmpty())
            .put("customerNumber", numbers.singleOrNull().orEmpty())
    }

    fun rename(file: File, metadata: JSONObject): File {
        val customer = metadata.optString("customerNumber").replace(Regex("[^A-Za-z0-9-]"), "_").take(50)
        if (customer.isBlank()) return file
        val target = File(file.parentFile, file.name.replaceFirst("Call_", "Call_KD_${customer}_Tel_"))
        return if (!target.exists() && file.renameTo(target)) target else file
    }

    fun context(job: JSONObject): String = buildString {
        append("Verifizierte Zuordnung: Telefonnummer ").append(job.optString("phone", "unbekannt"))
        append("; Kundennummer ").append(job.optString("customerNumber").ifBlank { "nicht zugeordnet" })
        append("; Kontakt ").append(job.optString("contactName"))
    }

    fun phone(job: JSONObject, name: String): String = job.optString("phone").ifBlank {
        name.removePrefix("Call_").substringAfter("_Tel_")
            .replace(Regex("_\\d{8}_\\d{6}\\.wav$"), "")
    }
}
