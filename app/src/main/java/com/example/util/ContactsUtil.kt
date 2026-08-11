package com.example.util

import android.content.Context
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

object ContactsUtil {

    data class CallLogEntry(
        val number: String,
        val name: String?,
        val date: Long,
        val type: Int,
        val duration: Long
    )

    data class SystemContact(
        val name: String,
        val phone: String,
        val tag: String? = null
    )

    fun hasContactsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun hasCallPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CALL_PHONE
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun isDefaultDialer(context: Context): Boolean {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            telecomManager?.defaultDialerPackage == context.packageName
        } else {
            false
        }
    }

    fun requestDefaultDialer(activity: android.app.Activity, requestCode: Int) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val roleManager = activity.getSystemService(Context.ROLE_SERVICE) as? android.app.role.RoleManager
            if (roleManager != null && roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_DIALER)) {
                if (!roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_DIALER)) {
                    val intent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_DIALER)
                    activity.startActivityForResult(intent, requestCode)
                }
            }
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val intent = android.content.Intent(android.telecom.TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                putExtra(android.telecom.TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, activity.packageName)
            }
            activity.startActivityForResult(intent, requestCode)
        }
    }

    fun hasCallLogPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CALL_LOG
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun cleanPhoneNumber(phone: String): String {
        val sb = StringBuilder(phone.length)
        for (i in 0 until phone.length) {
            val c = phone[i]
            if (c in '0'..'9') {
                sb.append(c)
            }
        }
        return sb.toString()
    }

    fun arePhoneNumbersMatching(p1: String, p2: String): Boolean {
        val clean1 = cleanPhoneNumber(p1)
        val clean2 = cleanPhoneNumber(p2)
        if (clean1.isEmpty() || clean2.isEmpty()) return false
        
        if (clean1 == clean2) return true
        
        fun normalize(s: String): String {
            var res = s
            if (res.startsWith("0049")) res = res.substring(4)
            else if (res.startsWith("49")) res = res.substring(2)
            while (res.startsWith("0")) {
                res = res.substring(1)
            }
            return res
        }
        
        val norm1 = normalize(clean1)
        val norm2 = normalize(clean2)
        if (norm1.isNotEmpty() && norm2.isNotEmpty() && norm1 == norm2) return true
        
        if (norm1.length >= 7 && norm2.length >= 7) {
            if (norm1.endsWith(norm2) || norm2.endsWith(norm1)) return true
        }
        return false
    }

    fun lookupContactName(context: Context, phoneNumber: String): String? {
        if (phoneNumber.isBlank()) return null
        if (!hasContactsPermission(context)) return null

        // 1. Try standard lookup with unencoded number (Uri.withAppendedPath does its own encoding)
        try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                phoneNumber
            )
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (index != -1) {
                        val name = cursor.getString(index)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Try standard lookup with Uri.encode (just in case)
        try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (index != -1) {
                        val name = cursor.getString(index)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Fallback: Query system contacts using CommonDataKinds.Phone and find matching using arePhoneNumbersMatching
        try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val phoneIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext()) {
                    val name = if (nameIdx != -1) cursor.getString(nameIdx) else null
                    val number = if (phoneIdx != -1) cursor.getString(phoneIdx) else null
                    if (!name.isNullOrBlank() && !number.isNullOrBlank()) {
                        if (arePhoneNumbersMatching(number, phoneNumber)) {
                            return name
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }

    fun readRecentCalls(context: Context): List<CallLogEntry> {
        val list = mutableListOf<CallLogEntry>()
        if (!hasCallLogPermission(context)) return list
        try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.DATE,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DURATION
                ),
                null,
                null,
                "${CallLog.Calls.DATE} DESC LIMIT 50"
            )
            cursor?.use { c ->
                val numIdx = c.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIdx = c.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val dateIdx = c.getColumnIndex(CallLog.Calls.DATE)
                val typeIdx = c.getColumnIndex(CallLog.Calls.TYPE)
                val durIdx = c.getColumnIndex(CallLog.Calls.DURATION)
                while (c.moveToNext()) {
                    val number = if (numIdx != -1) c.getString(numIdx) ?: "" else ""
                    val name = if (nameIdx != -1) c.getString(nameIdx) else null
                    val date = if (dateIdx != -1) c.getLong(dateIdx) else 0L
                    val type = if (typeIdx != -1) c.getInt(typeIdx) else 0
                    val duration = if (durIdx != -1) c.getLong(durIdx) else 0L
                    list.add(CallLogEntry(number, name, date, type, duration))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun getContactNotesMap(context: Context, contactIds: Set<Long>): Map<Long, String> {
        val map = mutableMapOf<Long, String>()
        if (contactIds.isEmpty() || !hasContactsPermission(context)) return map
        try {
            val uri = ContactsContract.Data.CONTENT_URI
            val selection = "${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.Data.CONTACT_ID} IN (${contactIds.joinToString(",")})"
            val selectionArgs = arrayOf(ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE)
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.Data.CONTACT_ID, ContactsContract.Data.DATA1),
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.Data.CONTACT_ID)
                val noteIdx = cursor.getColumnIndex(ContactsContract.Data.DATA1)
                if (idIdx != -1 && noteIdx != -1) {
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idIdx)
                        val note = cursor.getString(noteIdx) ?: ""
                        if (note.isNotBlank()) {
                            map[id] = note
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return map
    }

    fun searchSystemContacts(context: Context, query: String): List<SystemContact> {
        val list = mutableListOf<SystemContact>()
        if (!hasContactsPermission(context)) return list
        try {
            val matchedContactIdsByTag = mutableSetOf<Long>()
            if (query.isNotBlank()) {
                try {
                    val dataUri = ContactsContract.Data.CONTENT_URI
                    val selection = "(${ContactsContract.Data.MIMETYPE} = ? OR ${ContactsContract.Data.MIMETYPE} = ?) AND ${ContactsContract.Data.DATA1} LIKE ?"
                    val selectionArgs = arrayOf(
                        ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE,
                        "vnd.android.cursor.item/nickname",
                        "%$query%"
                    )
                    context.contentResolver.query(
                        dataUri,
                        arrayOf(ContactsContract.Data.CONTACT_ID),
                        selection,
                        selectionArgs,
                        null
                    )?.use { cursor ->
                        val idIdx = cursor.getColumnIndex(ContactsContract.Data.CONTACT_ID)
                        if (idIdx != -1) {
                            while (cursor.moveToNext()) {
                                matchedContactIdsByTag.add(cursor.getLong(idIdx))
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID
            )
            
            val selection = if (query.isNotBlank()) {
                val sb = java.lang.StringBuilder("(${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR ${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?")
                if (matchedContactIdsByTag.isNotEmpty()) {
                    sb.append(" OR ${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} IN (${matchedContactIdsByTag.joinToString(",")})")
                }
                sb.append(")")
                sb.toString()
            } else {
                null
            }
            
            val selectionArgs = if (query.isNotBlank()) {
                arrayOf("%$query%", "%$query%")
            } else {
                null
            }
            
            val cursor = context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )
            
            val tempContacts = mutableListOf<Triple<String, String, Long>>()
            val contactIds = mutableSetOf<Long>()
            
            cursor?.use { c ->
                val nameIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val phoneIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val idIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                while (c.moveToNext()) {
                    val name = if (nameIdx != -1) c.getString(nameIdx) ?: "" else ""
                    val phone = if (phoneIdx != -1) c.getString(phoneIdx) ?: "" else ""
                    val contactId = if (idIdx != -1) c.getLong(idIdx) else -1L
                    if (name.isNotBlank() && phone.isNotBlank()) {
                        tempContacts.add(Triple(name, phone, contactId))
                        if (contactId != -1L) {
                            contactIds.add(contactId)
                        }
                    }
                }
            }
            
            // Get notes mapping
            val notesMap = if (contactIds.isNotEmpty()) getContactNotesMap(context, contactIds) else emptyMap()
            
            for (item in tempContacts) {
                val name = item.first
                val phone = item.second
                val contactId = item.third
                val note = notesMap[contactId]
                list.add(SystemContact(name, phone, note))
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list.distinctBy { it.phone.replace(" ", "").replace("-", "") }
    }

    fun hasWriteContactsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.WRITE_CONTACTS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun saveContactToSystemDirectly(context: Context, name: String, phone: String): Boolean {
        if (!hasWriteContactsPermission(context)) return false
        try {
            val ops = ArrayList<android.content.ContentProviderOperation>()

            val rawContactIndex = ops.size
            ops.add(android.content.ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .build())

            // Name
            ops.add(android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                .build())

            // Phone
            ops.add(android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                .build())

            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun saveContactViaIntent(context: Context, name: String, phone: String) {
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_INSERT).apply {
                type = ContactsContract.Contacts.CONTENT_TYPE
                putExtra(ContactsContract.Intents.Insert.NAME, name)
                putExtra(ContactsContract.Intents.Insert.PHONE, phone)
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun formatMinutesToTimeString(minutes: Int?): String {
        if (minutes == null) return ""
        val totalMins = if (minutes in 0..24) minutes * 60 else minutes
        val h = totalMins / 60
        val m = totalMins % 60
        return String.format(java.util.Locale.GERMANY, "%02d:%02d", h, m)
    }

    fun parseTimeStringToMinutes(timeStr: String): Int? {
        if (timeStr.isBlank()) return null
        val cleaned = timeStr.trim()
        if (cleaned.contains(":")) {
            val parts = cleaned.split(":")
            val h = parts.getOrNull(0)?.toIntOrNull() ?: return null
            val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
            if (h in 0..23 && m in 0..59) {
                return h * 60 + m
            }
        } else {
            val h = cleaned.toIntOrNull() ?: return null
            if (h in 0..23) {
                return h * 60
            }
        }
        return null
    }
}
