package com.brainer.otnor.webview

import android.content.Context
import android.util.Log
import com.brainer.otnor.util.Constants
import com.brainer.otnor.util.JsonHelper
import java.io.File
import java.security.MessageDigest
import java.time.Instant

data class SnapshotEntry(
    val fileName: String,
    val url: String,
    val timestamp: String,
    val sizeBytes: Long
)

class UIVersionManager(context: Context) {

    private val cacheDir = File(context.filesDir, "ui_cache").apply { mkdirs() }
    private val indexFile = File(cacheDir, "version_index.json")
    private val lock = Any()

    fun saveSnapshot(url: String, rawEvaluateJsHtml: String?) {
        if (rawEvaluateJsHtml.isNullOrBlank() || rawEvaluateJsHtml == "null") return
        val unescaped = unescapeEvaluateJsString(rawEvaluateJsHtml)
        if (unescaped.length < 32) return
        // Do not overwrite user's Termux backend cache with offline fallback marker
        if (unescaped.contains("<!-- OTNOR_BUILTIN_OFFLINE_CONSOLE -->")) return

        synchronized(lock) {
            try {
                val index = loadIndexInternal().toMutableMap()
                val pathKey = normalizeUrlKey(url)
                val list = index[pathKey]?.toMutableList() ?: mutableListOf()

                val ts = System.currentTimeMillis()
                val fileName = "${hashKey(pathKey)}_$ts.html"
                val snapFile = File(cacheDir, fileName)
                snapFile.writeText(unescaped, Charsets.UTF_8)

                list.add(
                    0,
                    SnapshotEntry(
                        fileName = fileName,
                        url = url,
                        timestamp = Instant.ofEpochMilli(ts).toString(),
                        sizeBytes = snapFile.length()
                    )
                )

                // Keep last 10 versions per URL path
                while (list.size > 10) {
                    val removed = list.removeAt(list.lastIndex)
                    File(cacheDir, removed.fileName).delete()
                }

                index[pathKey] = list
                saveIndexInternal(index)
            } catch (e: Exception) {
                Log.e(Constants.TAG_OTNOR, "Failed saving UI snapshot for $url: ${e.message}", e)
            }
        }
    }

    fun getLatestSnapshotHtml(url: String = Constants.BACKEND_URL): String? {
        synchronized(lock) {
            val index = loadIndexInternal()
            val pathKey = normalizeUrlKey(url)
            val entries = index[pathKey] ?: index.values.firstOrNull() ?: return null
            for (entry in entries) {
                val f = File(cacheDir, entry.fileName)
                if (f.exists() && f.canRead()) {
                    return f.readText(Charsets.UTF_8)
                }
            }
            return null
        }
    }

    fun getSnapshotCount(): Int {
        synchronized(lock) {
            return loadIndexInternal().values.sumOf { it.size }
        }
    }

    fun getAllSnapshots(): Map<String, List<SnapshotEntry>> {
        synchronized(lock) {
            return loadIndexInternal()
        }
    }

    private fun loadIndexInternal(): Map<String, List<SnapshotEntry>> {
        if (!indexFile.exists()) return emptyMap()
        return try {
            JsonHelper.fromJson<Map<String, List<SnapshotEntry>>>(indexFile.readText(Charsets.UTF_8))
                ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun saveIndexInternal(index: Map<String, List<SnapshotEntry>>) {
        indexFile.writeText(JsonHelper.toJson(index), Charsets.UTF_8)
    }

    private fun normalizeUrlKey(url: String): String {
        return url.trim().removeSuffix("/").ifEmpty { Constants.BACKEND_URL }
    }

    private fun hashKey(key: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
        return digest.take(6).joinToString("") { "%02x".format(it) }
    }

    private fun unescapeEvaluateJsString(raw: String): String {
        return try {
            JsonHelper.gson.fromJson(raw, String::class.java) ?: raw
        } catch (_: Exception) {
            raw.trim('"')
                .replace("\\u003C", "<")
                .replace("\\u003E", ">")
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
        }
    }

    fun buildOfflineFallbackHtml(cachedHtml: String?): String {
        if (!cachedHtml.isNullOrBlank()) {
            return cachedHtml
        }
        return """
            <!DOCTYPE html>
            <!-- OTNOR_BUILTIN_OFFLINE_CONSOLE -->
            <html lang="en">
            <head>
                <meta charset="UTF-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                <title>Otnor • Brainer Shell</title>
                <style>
                    :root {
                        --bg: #0A0E17;
                        --card: #111827;
                        --card-elev: #1E293B;
                        --cyan: #00E5FF;
                        --violet: #7C4DFF;
                        --emerald: #10B981;
                        --amber: #F59E0B;
                        --text: #F8FAFC;
                        --muted: #94A3B8;
                        --border: #334155;
                    }
                    * { box-sizing: border-box; margin: 0; padding: 0; }
                    body {
                        background: radial-gradient(circle at top right, #162238 0%, var(--bg) 55%);
                        color: var(--text);
                        font-family: system-ui, -apple-system, sans-serif;
                        padding: 18px;
                        min-height: 100vh;
                    }
                    .hero {
                        background: linear-gradient(135deg, rgba(0,229,255,0.12), rgba(124,77,255,0.12));
                        border: 1px solid rgba(0,229,255,0.35);
                        border-radius: 16px;
                        padding: 18px;
                        margin-bottom: 16px;
                    }
                    .badge {
                        display: inline-block;
                        font-size: 11px;
                        font-family: monospace;
                        text-transform: uppercase;
                        letter-spacing: 1px;
                        padding: 4px 10px;
                        border-radius: 99px;
                        background: rgba(245, 158, 11, 0.18);
                        color: var(--amber);
                        border: 1px solid rgba(245, 158, 11, 0.4);
                        margin-bottom: 10px;
                    }
                    h1 { font-size: 22px; margin-bottom: 6px; letter-spacing: -0.3px; }
                    p.sub { color: var(--muted); font-size: 13px; line-height: 1.5; }
                    .grid {
                        display: grid;
                        grid-template-columns: repeat(2, 1fr);
                        gap: 10px;
                        margin-bottom: 16px;
                    }
                    button.action {
                        background: var(--card-elev);
                        color: var(--text);
                        border: 1px solid var(--border);
                        border-radius: 12px;
                        padding: 13px 12px;
                        font-size: 13px;
                        font-weight: 600;
                        text-align: left;
                        cursor: pointer;
                        transition: all 0.15s ease;
                    }
                    button.action:active {
                        transform: scale(0.98);
                        border-color: var(--cyan);
                    }
                    button.action span.hint {
                        display: block;
                        font-size: 11px;
                        font-family: monospace;
                        color: var(--cyan);
                        margin-top: 4px;
                        font-weight: 400;
                    }
                    .console-card {
                        background: var(--card);
                        border: 1px solid var(--border);
                        border-radius: 14px;
                        padding: 14px;
                        margin-bottom: 16px;
                    }
                    .console-header {
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        margin-bottom: 10px;
                        font-size: 12px;
                        font-family: monospace;
                        color: var(--cyan);
                    }
                    pre#output {
                        background: #06090F;
                        border: 1px solid #1E293B;
                        border-radius: 10px;
                        padding: 12px;
                        font-family: monospace;
                        font-size: 11.5px;
                        color: #A7F3D0;
                        overflow-x: auto;
                        max-height: 280px;
                        white-space: pre-wrap;
                        word-break: break-word;
                    }
                    .curl-box {
                        background: #06090F;
                        border: 1px dashed var(--border);
                        border-radius: 10px;
                        padding: 12px;
                        font-family: monospace;
                        font-size: 11px;
                        color: var(--muted);
                        line-height: 1.6;
                    }
                </style>
            </head>
            <body>
                <div class="hero">
                    <div class="badge">Termux :8080 Offline • Built-in Brainer Shell</div>
                    <h1>Otnor Native Engine Ready</h1>
                    <p class="sub">
                        Waiting for your Termux Python server at <code>http://127.0.0.1:8080</code>.
                        Meanwhile, both <code>window.Andropi</code> JS Bridge and the Andropi REST Server on
                        <code>http://127.0.0.1:8081</code> are live and ready below.
                    </p>
                </div>

                <div class="grid">
                    <button class="action" onclick="callApi('GET', '/andropi/v1/system/status')">
                        ⚡ System Status
                        <span class="hint">GET /system/status</span>
                    </button>
                    <button class="action" onclick="callApi('GET', '/andropi/v1/device/info')">
                        📱 Device Info
                        <span class="hint">GET /device/info</span>
                    </button>
                    <button class="action" onclick="callApi('GET', '/andropi/v1/device/battery')">
                        🔋 Battery Telemetry
                        <span class="hint">GET /device/battery</span>
                    </button>
                    <button class="action" onclick="callApi('GET', '/andropi/v1/accessibility/status')">
                        👁️ Accessibility
                        <span class="hint">GET /accessibility/status</span>
                    </button>
                    <button class="action" onclick="testToast()">
                        💬 Native Toast
                        <span class="hint">Andropi.showToast()</span>
                    </button>
                    <button class="action" onclick="testVibrate()">
                        📳 Vibrate Pulse
                        <span class="hint">POST /screen/vibrate</span>
                    </button>
                    <button class="action" onclick="callApi('GET', '/andropi/v1/clipboard/get')">
                        📋 Read Clipboard
                        <span class="hint">GET /clipboard/get</span>
                    </button>
                    <button class="action" onclick="callApi('GET', '/andropi/v1/system/commands')">
                        📚 All 60+ Endpoints
                        <span class="hint">GET /system/commands</span>
                    </button>
                </div>

                <div class="console-card">
                    <div class="console-header">
                        <span>LIVE RESPONSE INSPECTOR (:8081)</span>
                        <span id="latency">Ready</span>
                    </div>
                    <pre id="output">Tap any command card above to query http://127.0.0.1:8081 or window.Andropi...</pre>
                </div>

                <div class="curl-box">
                    <strong style="color:#F8FAFC;">Termux CLI Quick Test:</strong><br/>
                    curl http://127.0.0.1:8081/andropi/v1/system/status<br/>
                    curl http://127.0.0.1:8081/andropi/v1/device/info<br/>
                    curl -X POST http://127.0.0.1:8081/andropi/v1/screen/toast -d '{"message":"Hello!"}'
                </div>

                <script>
                    async function callApi(method, path, bodyObj) {
                        const out = document.getElementById('output');
                        const lat = document.getElementById('latency');
                        const t0 = performance.now();
                        out.textContent = 'Requesting ' + method + ' ' + path + '...';
                        try {
                            const opts = { method: method, headers: { 'Content-Type': 'application/json' } };
                            if (bodyObj) opts.body = JSON.stringify(bodyObj);
                            const res = await fetch('http://127.0.0.1:8081' + path, opts);
                            const json = await res.json();
                            const dt = Math.round(performance.now() - t0);
                            lat.textContent = dt + ' ms';
                            out.textContent = JSON.stringify(json, null, 2);
                        } catch (e) {
                            out.textContent = 'Error: ' + e.message;
                        }
                    }
                    function testToast() {
                        if (window.Andropi) {
                            window.Andropi.showToast('Hello from Otnor Andropi Bridge!');
                            document.getElementById('output').textContent = 'window.Andropi.showToast() executed.\nDevice Info: ' + window.Andropi.getDeviceInfo();
                        } else {
                            callApi('POST', '/andropi/v1/screen/toast', { message: 'Hello!', duration: 'short' });
                        }
                    }
                    function testVibrate() {
                        callApi('POST', '/andropi/v1/screen/vibrate', { duration_ms: 200 });
                    }
                    window.addEventListener('load', () => {
                        callApi('GET', '/andropi/v1/system/status');
                    });
                </script>
            </body>
            </html>
        """.trimIndent()
    }
}
