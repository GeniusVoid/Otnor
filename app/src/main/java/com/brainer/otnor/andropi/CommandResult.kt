package com.brainer.otnor.andropi

import com.brainer.otnor.util.JsonHelper

data class CommandResult(
    val success: Boolean,
    val data: Any? = null,
    val error: String? = null
) {
    fun toJson(): String = JsonHelper.toJson(this)

    companion object {
        fun ok(data: Any? = emptyMap<String, Any>()): CommandResult =
            CommandResult(success = true, data = data, error = null)

        fun error(message: String): CommandResult =
            CommandResult(success = false, data = null, error = message)
    }
}
