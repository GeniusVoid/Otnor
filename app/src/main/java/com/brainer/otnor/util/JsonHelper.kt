package com.brainer.otnor.util

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken

object JsonHelper {
    val gson: Gson = GsonBuilder()
        .serializeNulls()
        .disableHtmlEscaping()
        .create()

    fun toJson(src: Any?): String = gson.toJson(src)

    fun parseObject(json: String?): JsonObject {
        if (json.isNullOrBlank()) return JsonObject()
        return try {
            val elem = JsonParser.parseString(json)
            if (elem.isJsonObject) elem.asJsonObject else JsonObject()
        } catch (e: Exception) {
            JsonObject()
        }
    }

    fun JsonObject.getStringOrNull(key: String): String? {
        val el = this.get(key) ?: return null
        return if (el.isJsonNull) null else el.asString
    }

    fun JsonObject.getStringOrDefault(key: String, default: String): String {
        return getStringOrNull(key) ?: default
    }

    fun JsonObject.getIntOrDefault(key: String, default: Int): Int {
        val el = this.get(key) ?: return default
        return try {
            if (el.isJsonNull) default else el.asInt
        } catch (e: Exception) {
            default
        }
    }

    fun JsonObject.getLongOrDefault(key: String, default: Long): Long {
        val el = this.get(key) ?: return default
        return try {
            if (el.isJsonNull) default else el.asLong
        } catch (e: Exception) {
            default
        }
    }

    fun JsonObject.getFloatOrDefault(key: String, default: Float): Float {
        val el = this.get(key) ?: return default
        return try {
            if (el.isJsonNull) default else el.asFloat
        } catch (e: Exception) {
            default
        }
    }

    fun JsonObject.getBooleanOrDefault(key: String, default: Boolean): Boolean {
        val el = this.get(key) ?: return default
        return try {
            if (el.isJsonNull) default else el.asBoolean
        } catch (e: Exception) {
            default
        }
    }

    inline fun <reified T> fromJson(json: String): T? {
        return try {
            gson.fromJson<T>(json, object : TypeToken<T>() {}.type)
        } catch (e: Exception) {
            null
        }
    }
}
