package com.brainer.otnor.andropi.commands

import android.util.Base64
import android.webkit.MimeTypeMap
import com.brainer.otnor.andropi.CommandResult
import com.brainer.otnor.util.JsonHelper.getBooleanOrDefault
import com.brainer.otnor.util.JsonHelper.getStringOrDefault
import com.brainer.otnor.util.JsonHelper.getStringOrNull
import com.google.gson.JsonObject
import java.io.File
import java.time.Instant

class StorageCommands {

    fun read(body: JsonObject): CommandResult {
        val path = body.getStringOrNull("path") ?: return CommandResult.error("Missing 'path'")
        val encoding = body.getStringOrDefault("encoding", "utf-8").lowercase()
        val file = File(path)
        if (!file.exists()) return CommandResult.error("File does not exist: $path")
        if (file.isDirectory) return CommandResult.error("Path is a directory: $path")

        val content = if (encoding == "base64") {
            Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        } else {
            file.readText(Charsets.UTF_8)
        }

        return CommandResult.ok(
            mapOf(
                "content" to content,
                "size_bytes" to file.length(),
                "last_modified" to Instant.ofEpochMilli(file.lastModified()).toString(),
                "mime_type" to detectMimeType(file)
            )
        )
    }

    fun write(body: JsonObject): CommandResult {
        val path = body.getStringOrNull("path") ?: return CommandResult.error("Missing 'path'")
        val content = body.getStringOrDefault("content", "")
        val encoding = body.getStringOrDefault("encoding", "utf-8").lowercase()
        val createDirs = body.getBooleanOrDefault("create_dirs", true)

        val file = File(path)
        if (createDirs) {
            file.parentFile?.mkdirs()
        }

        if (encoding == "base64") {
            val bytes = Base64.decode(content, Base64.DEFAULT)
            file.writeBytes(bytes)
        } else {
            file.writeText(content, Charsets.UTF_8)
        }

        return CommandResult.ok(
            mapOf(
                "path" to file.absolutePath,
                "size_bytes" to file.length(),
                "written" to true
            )
        )
    }

    fun append(body: JsonObject): CommandResult {
        val path = body.getStringOrNull("path") ?: return CommandResult.error("Missing 'path'")
        val content = body.getStringOrDefault("content", "")
        val file = File(path)
        file.parentFile?.mkdirs()
        file.appendText(content, Charsets.UTF_8)
        return CommandResult.ok(
            mapOf(
                "path" to file.absolutePath,
                "size_bytes" to file.length(),
                "appended" to true
            )
        )
    }

    fun list(body: JsonObject): CommandResult {
        val path = body.getStringOrNull("path") ?: return CommandResult.error("Missing 'path'")
        val recursive = body.getBooleanOrDefault("recursive", false)
        val includeHidden = body.getBooleanOrDefault("include_hidden", false)

        val dir = File(path)
        if (!dir.exists()) return CommandResult.error("Directory does not exist: $path")
        if (!dir.isDirectory) return CommandResult.error("Not a directory: $path")

        val files = if (recursive) {
            dir.walkTopDown().filter { it != dir }.toList()
        } else {
            dir.listFiles()?.toList() ?: emptyList()
        }

        val entries = files
            .filter { includeHidden || !it.name.startsWith(".") }
            .map { f ->
                mapOf(
                    "name" to f.name,
                    "path" to f.absolutePath,
                    "is_directory" to f.isDirectory,
                    "size_bytes" to if (f.isDirectory) 0L else f.length(),
                    "last_modified" to Instant.ofEpochMilli(f.lastModified()).toString(),
                    "mime_type" to detectMimeType(f)
                )
            }

        return CommandResult.ok(
            mapOf(
                "path" to dir.absolutePath,
                "entries" to entries,
                "total_entries" to entries.size
            )
        )
    }

    fun delete(body: JsonObject): CommandResult {
        val path = body.getStringOrNull("path") ?: return CommandResult.error("Missing 'path'")
        val recursive = body.getBooleanOrDefault("recursive", false)
        val file = File(path)
        if (!file.exists()) return CommandResult.error("File not found: $path")

        val deleted = if (recursive) file.deleteRecursively() else file.delete()
        return CommandResult.ok(mapOf("deleted" to deleted, "path" to path))
    }

    fun move(body: JsonObject): CommandResult {
        val from = body.getStringOrNull("from") ?: return CommandResult.error("Missing 'from'")
        val to = body.getStringOrNull("to") ?: return CommandResult.error("Missing 'to'")
        val src = File(from)
        val dst = File(to)
        if (!src.exists()) return CommandResult.error("Source does not exist: $from")
        dst.parentFile?.mkdirs()

        val moved = if (src.renameTo(dst)) {
            true
        } else {
            src.copyRecursively(dst, overwrite = true) && src.deleteRecursively()
        }
        return CommandResult.ok(mapOf("moved" to moved, "from" to from, "to" to to))
    }

    fun copy(body: JsonObject): CommandResult {
        val from = body.getStringOrNull("from") ?: return CommandResult.error("Missing 'from'")
        val to = body.getStringOrNull("to") ?: return CommandResult.error("Missing 'to'")
        val src = File(from)
        val dst = File(to)
        if (!src.exists()) return CommandResult.error("Source does not exist: $from")
        dst.parentFile?.mkdirs()
        val copied = src.copyRecursively(dst, overwrite = true)
        return CommandResult.ok(mapOf("copied" to copied, "from" to from, "to" to to))
    }

    fun exists(body: JsonObject): CommandResult {
        val path = body.getStringOrNull("path") ?: return CommandResult.error("Missing 'path'")
        val file = File(path)
        return CommandResult.ok(
            mapOf(
                "exists" to file.exists(),
                "is_directory" to file.isDirectory
            )
        )
    }

    fun stat(body: JsonObject): CommandResult {
        val path = body.getStringOrNull("path") ?: return CommandResult.error("Missing 'path'")
        val file = File(path)
        if (!file.exists()) return CommandResult.error("Path does not exist: $path")

        return CommandResult.ok(
            mapOf(
                "name" to file.name,
                "path" to file.absolutePath,
                "size" to file.length(),
                "last_modified" to Instant.ofEpochMilli(file.lastModified()).toString(),
                "is_directory" to file.isDirectory,
                "is_readable" to file.canRead(),
                "is_writable" to file.canWrite(),
                "mime_type" to detectMimeType(file)
            )
        )
    }

    fun mkdir(body: JsonObject): CommandResult {
        val path = body.getStringOrNull("path") ?: return CommandResult.error("Missing 'path'")
        val recursive = body.getBooleanOrDefault("recursive", true)
        val dir = File(path)
        val created = if (recursive) dir.mkdirs() || dir.exists() else dir.mkdir() || dir.exists()
        return CommandResult.ok(mapOf("created" to created, "path" to dir.absolutePath))
    }

    private fun detectMimeType(file: File): String {
        if (file.isDirectory) return "inode/directory"
        val ext = file.extension.lowercase()
        return when (ext) {
            "md", "markdown" -> "text/markdown"
            "txt", "log" -> "text/plain"
            "json" -> "application/json"
            "py" -> "text/x-python"
            "js" -> "application/javascript"
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        }
    }
}
