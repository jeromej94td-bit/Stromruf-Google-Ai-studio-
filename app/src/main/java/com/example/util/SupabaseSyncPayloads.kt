package com.example.util

import com.example.database.AnnahmeEntity
import com.example.database.PromisedAnnahmeEntity
import com.example.database.NeukundeEntity
import com.example.database.HeissAngebotEntity
import org.json.JSONObject

object SupabaseSyncPayloads {

    fun annahmeToJson(item: AnnahmeEntity, userId: String?): JSONObject {
        return JSONObject().apply {
            put("id", item.id)
            put("type", item.type)
            put("customer_type", item.customerType)
            put("consumption", item.consumption)
            put("term_years", item.termYears)
            put("customer_number", item.customerNumber)
            put("timestamp", item.timestamp)
            if (userId != null) {
                put("user_id", userId)
            }
        }
    }

    fun annahmeFromJson(json: JSONObject): AnnahmeEntity {
        return AnnahmeEntity(
            id = json.getString("id"),
            type = json.getString("type"),
            customerType = json.getString("customer_type"),
            consumption = json.getLong("consumption"),
            termYears = json.getInt("term_years"),
            customerNumber = json.getString("customer_number"),
            timestamp = json.getLong("timestamp")
        )
    }

    fun promisedAnnahmeToJson(item: PromisedAnnahmeEntity, userId: String?): JSONObject {
        return JSONObject().apply {
            put("id", item.id)
            put("customer_number", item.customerNumber)
            put("name", item.name)
            put("phone", item.phone)
            put("timestamp", item.timestamp)
            put("is_called", item.isCalled)
            if (userId != null) {
                put("user_id", userId)
            }
        }
    }

    fun promisedAnnahmeFromJson(json: JSONObject): PromisedAnnahmeEntity {
        return PromisedAnnahmeEntity(
            id = json.getString("id"),
            customerNumber = json.getString("customer_number"),
            name = json.getString("name"),
            phone = json.getString("phone"),
            timestamp = json.getLong("timestamp"),
            isCalled = json.optBoolean("is_called", false)
        )
    }

    fun neukundeToJson(item: NeukundeEntity, userId: String?): JSONObject {
        return JSONObject().apply {
            put("id", item.id)
            put("date_created", item.dateCreated)
            put("customer_number", item.customerNumber)
            put("phone", item.phone)
            put("call_attempts", item.callAttempts)
            put("status", item.status)
            put("customer_name", item.customerName ?: JSONObject.NULL)
            put("company", item.company ?: JSONObject.NULL)
            put("email", item.email ?: JSONObject.NULL)
            put("delivery_address", item.deliveryAddress ?: JSONObject.NULL)
            put("meter_number", item.meterNumber ?: JSONObject.NULL)
            put("consumption", item.consumption ?: JSONObject.NULL)
            put("energy_type", item.energyType ?: JSONObject.NULL)
            put("next_action_at", item.nextActionAt ?: JSONObject.NULL)
            put("offer_sent_at", item.offerSentAt ?: JSONObject.NULL)
            put("completed_at", item.completedAt ?: JSONObject.NULL)
            put("archived_at", item.archivedAt ?: JSONObject.NULL)
            put("updated_at_ms", item.updatedAt)
            if (userId != null) {
                put("user_id", userId)
            }
        }
    }

    fun neukundeFromJson(json: JSONObject): NeukundeEntity {
        return NeukundeEntity(
            id = json.getString("id"),
            dateCreated = json.getLong("date_created"),
            customerNumber = json.getString("customer_number"),
            phone = json.getString("phone"),
            callAttempts = json.getInt("call_attempts"),
            status = json.getString("status"),
            customerName = json.optNullableString("customer_name"),
            company = json.optNullableString("company"),
            email = json.optNullableString("email"),
            deliveryAddress = json.optNullableString("delivery_address"),
            meterNumber = json.optNullableString("meter_number"),
            consumption = json.optNullableLong("consumption"),
            energyType = json.optNullableString("energy_type"),
            nextActionAt = json.optNullableLong("next_action_at"),
            offerSentAt = json.optNullableLong("offer_sent_at"),
            completedAt = json.optNullableLong("completed_at"),
            archivedAt = json.optNullableLong("archived_at"),
            updatedAt = json.optLong("updated_at_ms", json.optLong("date_created"))
        )
    }

    fun heissAngebotToJson(item: HeissAngebotEntity, userId: String?): JSONObject {
        return JSONObject().apply {
            put("id", item.id)
            put("date_created", item.dateCreated)
            put("customer_number", item.customerNumber)
            put("phone", item.phone)
            put("call_attempts", item.callAttempts)
            put("notes", item.notes)
            if (userId != null) {
                put("user_id", userId)
            }
        }
    }

    fun heissAngebotFromJson(json: JSONObject): HeissAngebotEntity {
        return HeissAngebotEntity(
            id = json.getString("id"),
            dateCreated = json.getLong("date_created"),
            customerNumber = json.getString("customer_number"),
            phone = json.getString("phone"),
            callAttempts = json.getInt("call_attempts"),
            notes = json.optString("notes", "")
        )
    }
}

private fun JSONObject.optNullableString(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).trim().takeIf { it.isNotEmpty() && it != "null" }

private fun JSONObject.optNullableLong(key: String): Long? =
    if (!has(key) || isNull(key)) null else optLong(key)
