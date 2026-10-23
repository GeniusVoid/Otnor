package com.brainer.otnor.andropi

import android.content.Context
import android.util.Log
import com.brainer.otnor.util.Constants
import com.brainer.otnor.util.JsonHelper
import fi.iki.elonen.NanoHTTPD

class AndropiServer(context: Context) : NanoHTTPD(Constants.ANDROPI_HOST, Constants.ANDROPI_PORT) {

    private val appContext = context.applicationContext
    private val router = AndropiRouter(appContext)

    override fun serve(session: IHTTPSession): Response {
        val method = session.method
        val uri = session.uri ?: "/"
        Log.d(Constants.TAG_ANDROPI, "Incoming request: ${method.name} $uri")

        // Handle CORS Preflight OPTIONS
        if (method == Method.OPTIONS) {
            return withCorsHeaders(newFixedLengthResponse(Response.Status.OK, "application/json", ""))
        }

        // Only allow localhost connections (127.0.0.1 / ::1)
        val remoteIp = session.remoteIpAddress ?: "127.0.0.1"
        if (remoteIp != "127.0.0.1" && remoteIp != "::1" && remoteIp != "0:0:0:0:0:0:0:1") {
            val forbidden = CommandResult.error("Connections only allowed from 127.0.0.1").toJson()
            return withCorsHeaders(
                newFixedLengthResponse(Response.Status.FORBIDDEN, "application/json", forbidden)
            )
        }

        return try {
            val bodyString = if (method == Method.POST || method == Method.PUT) {
                val files = HashMap<String, String>()
                session.parseBody(files)
                files["postData"] ?: ""
            } else {
                ""
            }

            val bodyJson = JsonHelper.parseObject(bodyString)
            val result = router.route(method.name, uri.trimEnd('/'), bodyJson)
            val status = if (result.success) Response.Status.OK else Response.Status.BAD_REQUEST
            withCorsHeaders(
                newFixedLengthResponse(status, "application/json; charset=utf-8", result.toJson())
            )
        } catch (e: Exception) {
            Log.e(Constants.TAG_ANDROPI, "Unhandled exception serving $uri: ${e.message}", e)
            val errJson = CommandResult.error(e.message ?: "Internal server error").toJson()
            withCorsHeaders(
                newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json; charset=utf-8", errJson)
            )
        }
    }

    private fun withCorsHeaders(response: Response): Response {
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "Content-Type")
        return response
    }
}
