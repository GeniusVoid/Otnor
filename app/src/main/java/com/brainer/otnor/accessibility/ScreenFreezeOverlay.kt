package com.brainer.otnor.accessibility

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.brainer.otnor.util.Constants
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

data class SelectedNodeEntry(
    val text: String,
    val source_package: String,
    val bounds: BoundsRect
)

data class FreezeResult(
    val frozen: Boolean,
    val node_count: Int,
    val error: String? = null
)

data class UnfreezeResult(
    val unfrozen: Boolean,
    val selected_words: List<String>,
    val selected_nodes: List<SelectedNodeEntry>
)

data class PopupActionItem(
    val label: String,
    val id: String
)

object ScreenFreezeOverlay {

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    var isFreezeActive: Boolean = false
        private set

    @Volatile
    var isPopupActive: Boolean = false
        private set

    @Volatile
    var currentPopupType: String? = null
        private set

    private var freezeOverlayView: View? = null
    private var popupOverlayView: View? = null

    private val selectedWords = CopyOnWriteArrayList<String>()
    private val selectedNodes = CopyOnWriteArrayList<SelectedNodeEntry>()
    private val highlightedRects = CopyOnWriteArrayList<RectF>()
    private var cachedNodes: List<CapturedNode> = emptyList()
    private var sourcePackage: String = "unknown"

    fun freezeCurrentScreen(service: BrainerAccessibilityService): FreezeResult {
        if (!Settings.canDrawOverlays(service)) {
            return FreezeResult(
                frozen = false,
                node_count = 0,
                error = "SYSTEM_ALERT_WINDOW overlay permission not granted"
            )
        }

        // 1. Snapshot the accessibility node tree before taking screenshot
        val snapshot = ScreenTextCapture.captureCurrentWindow(service)
        val sortedNodes = snapshot?.texts?.sortedBy { it.bounds.top } ?: emptyList()
        cachedNodes = sortedNodes
        sourcePackage = snapshot?.package_name ?: "unknown"
        selectedWords.clear()
        selectedNodes.clear()
        highlightedRects.clear()

        // 2. Capture screenshot on API 30+ or create node-map overlay on API 26-29
        var capturedBitmap: Bitmap? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val latch = CountDownLatch(1)
            try {
                service.takeScreenshot(
                    android.view.Display.DEFAULT_DISPLAY,
                    service.mainExecutor,
                    object : AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                            try {
                                val hwBuffer = screenshot.hardwareBuffer
                                val colorSpace = screenshot.colorSpace
                                val hwBitmap = Bitmap.wrapHardwareBuffer(hwBuffer, colorSpace)
                                capturedBitmap = hwBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                                hwBuffer.close()
                            } catch (e: Exception) {
                                Log.e(Constants.TAG_OTNOR, "Failed wrapping screenshot buffer", e)
                            } finally {
                                latch.countDown()
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            Log.w(Constants.TAG_OTNOR, "takeScreenshot failed with code $errorCode")
                            latch.countDown()
                        }
                    }
                )
                latch.await(3, TimeUnit.SECONDS)
            } catch (e: Exception) {
                Log.e(Constants.TAG_OTNOR, "takeScreenshot exception", e)
            }
        }

        // 3. Attach full-screen TYPE_APPLICATION_OVERLAY on Main Thread
        val attachLatch = CountDownLatch(1)
        mainHandler.post {
            try {
                removeFreezeViewInternal(service)
                val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val overlay = FreezeCanvasView(service, capturedBitmap)
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                )
                wm.addView(overlay, params)
                freezeOverlayView = overlay
                isFreezeActive = true
            } catch (e: Exception) {
                Log.e(Constants.TAG_OTNOR, "Error adding freeze overlay view", e)
            } finally {
                attachLatch.countDown()
            }
        }
        attachLatch.await(2, TimeUnit.SECONDS)

        return FreezeResult(
            frozen = isFreezeActive,
            node_count = cachedNodes.size
        )
    }

    fun unfreezeScreen(context: Context): UnfreezeResult {
        val latch = CountDownLatch(1)
        mainHandler.post {
            removeFreezeViewInternal(context)
            latch.countDown()
        }
        latch.await(2, TimeUnit.SECONDS)
        return UnfreezeResult(
            unfrozen = true,
            selected_words = selectedWords.toList(),
            selected_nodes = selectedNodes.toList()
        )
    }

    private fun removeFreezeViewInternal(context: Context) {
        val view = freezeOverlayView ?: return
        try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeView(view)
        } catch (e: Exception) {
            Log.w(Constants.TAG_OTNOR, "Error removing freeze view: ${e.message}")
        }
        freezeOverlayView = null
        isFreezeActive = false
    }

    fun showQuickActionsPopup(
        context: Context,
        position: String = "bottom_right",
        widthDp: Int = 220,
        heightDp: Int = 300,
        actions: List<PopupActionItem> = listOf(
            PopupActionItem("📝 Quick Capture", "capture"),
            PopupActionItem("🧊 Freeze & Select", "freeze"),
            PopupActionItem("📚 Review Cards", "review"),
            PopupActionItem("🔍 Search Brain", "search"),
            PopupActionItem("❌ Close", "close")
        )
    ): Boolean {
        if (!Settings.canDrawOverlays(context)) return false
        mainHandler.post {
            dismissPopupInternal(context)
            try {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val container = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(context, 14), dp(context, 14), dp(context, 14), dp(context, 14))
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#EE111827"))
                        cornerRadius = dp(context, 16).toFloat()
                        setStroke(dp(context, 1), Color.parseColor("#00E5FF"))
                    }
                }

                val header = TextView(context).apply {
                    text = "OTNOR • BRAINER"
                    setTextColor(Color.parseColor("#00E5FF"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setPadding(0, 0, 0, dp(context, 8))
                }
                container.addView(header)

                actions.forEach { item ->
                    val btn = TextView(context).apply {
                        text = item.label
                        setTextColor(Color.WHITE)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                        setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10))
                        background = GradientDrawable().apply {
                            setColor(Color.parseColor("#1E293B"))
                            cornerRadius = dp(context, 10).toFloat()
                        }
                        val lp = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            bottomMargin = dp(context, 6)
                        }
                        layoutParams = lp
                        setOnClickListener {
                            CaptureBuffer.recordQuickAction(item.id)
                            if (item.id == "freeze") {
                                dismissPopupInternal(context)
                                ServiceHolder.accessibilityService?.let { svc ->
                                    Thread { freezeCurrentScreen(svc) }.start()
                                }
                            } else if (item.id == "close") {
                                dismissPopupInternal(context)
                            } else {
                                Toast.makeText(context, "Brainer Action: ${item.label}", Toast.LENGTH_SHORT).show()
                                dismissPopupInternal(context)
                            }
                        }
                    }
                    container.addView(btn)
                }

                val params = createOverlayLayoutParams(context, position, widthDp, heightDp)
                wm.addView(container, params)
                popupOverlayView = container
                isPopupActive = true
                currentPopupType = "quick_actions"
            } catch (e: Exception) {
                Log.e(Constants.TAG_OTNOR, "Failed showing quick actions popup", e)
            }
        }
        return true
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun showWebViewPopup(
        context: Context,
        url: String,
        position: String = "center",
        widthDp: Int = 350,
        heightDp: Int = 500
    ): Boolean {
        if (!Settings.canDrawOverlays(context)) return false
        mainHandler.post {
            dismissPopupInternal(context)
            try {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val container = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8))
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#F5111827"))
                        cornerRadius = dp(context, 16).toFloat()
                        setStroke(dp(context, 1), Color.parseColor("#00E5FF"))
                    }
                }

                val closeBtn = TextView(context).apply {
                    text = "✕ Close Overlay"
                    setTextColor(Color.parseColor("#00E5FF"))
                    gravity = Gravity.END
                    setPadding(dp(context, 8), dp(context, 4), dp(context, 8), dp(context, 8))
                    setOnClickListener { dismissPopupInternal(context) }
                }
                container.addView(closeBtn)

                val webView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = WebViewClient()
                    loadUrl(url)
                }
                container.addView(
                    webView,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                    )
                )

                val params = createOverlayLayoutParams(context, position, widthDp, heightDp)
                wm.addView(container, params)
                popupOverlayView = container
                isPopupActive = true
                currentPopupType = "webview"
            } catch (e: Exception) {
                Log.e(Constants.TAG_OTNOR, "Failed showing webview popup", e)
            }
        }
        return true
    }

    fun dismissPopup(context: Context) {
        mainHandler.post {
            dismissPopupInternal(context)
        }
    }

    private fun dismissPopupInternal(context: Context) {
        val view = popupOverlayView ?: return
        try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeView(view)
        } catch (e: Exception) {
            Log.w(Constants.TAG_OTNOR, "Error dismissing popup: ${e.message}")
        }
        popupOverlayView = null
        isPopupActive = false
        currentPopupType = null
    }

    private fun createOverlayLayoutParams(
        context: Context,
        position: String,
        widthDp: Int,
        heightDp: Int
    ): WindowManager.LayoutParams {
        val gravity = when (position.lowercase()) {
            "bottom_right" -> Gravity.BOTTOM or Gravity.END
            "bottom_left" -> Gravity.BOTTOM or Gravity.START
            "top_right" -> Gravity.TOP or Gravity.END
            "top_left" -> Gravity.TOP or Gravity.START
            else -> Gravity.CENTER
        }
        return WindowManager.LayoutParams(
            dp(context, widthDp),
            dp(context, heightDp),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity
            x = dp(context, 16)
            y = dp(context, 48)
        }
    }

    private fun dp(context: Context, value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }

    private class FreezeCanvasView(
        context: Context,
        private val screenshotBitmap: Bitmap?
    ) : View(context) {

        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00E5FF")
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }

        private val nodeBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#3300E5FF")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        private val highlightFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#5500E5FF")
            style = Paint.Style.FILL
        }

        private val highlightStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00E5FF")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }

        private val bannerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#DD0A0E17")
            style = Paint.Style.FILL
        }

        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 36f
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            screenshotBitmap?.let { bmp ->
                canvas.drawBitmap(bmp, null, Rect(0, 0, width, height), null)
            } ?: run {
                canvas.drawColor(Color.parseColor("#880A0E17"))
            }

            // Draw subtle bounding boxes for detected text nodes
            for (node in cachedNodes) {
                canvas.drawRect(
                    node.bounds.left.toFloat(),
                    node.bounds.top.toFloat(),
                    node.bounds.right.toFloat(),
                    node.bounds.bottom.toFloat(),
                    nodeBoxPaint
                )
            }

            // Draw selected highlights
            for (rect in highlightedRects) {
                canvas.drawRoundRect(rect, 8f, 8f, highlightFillPaint)
                canvas.drawRoundRect(rect, 8f, 8f, highlightStrokePaint)
            }

            // Top status bar banner (tapping top-right unfreezes)
            canvas.drawRect(0f, 0f, width.toFloat(), 120f, bannerPaint)
            canvas.drawText(
                "FROZEN (${selectedWords.size} words selected) • Tap text to select • Tap here to Done",
                32f,
                76f,
                textPaint
            )

            // Cyan screen border indicating freeze mode
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), borderPaint)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_DOWN) {
                val tx = event.rawX.toInt()
                val ty = event.rawY.toInt()

                if (ty < 120) {
                    unfreezeScreen(context)
                    return true
                }

                // 1. Check cached accessibility nodes containing tap point
                val matchedNode = cachedNodes.firstOrNull { it.bounds.contains(tx, ty) }
                if (matchedNode != null) {
                    val words = matchedNode.text.split(Regex("\\s+")).filter { it.isNotBlank() }
                    selectedWords.addAll(words)
                    selectedNodes.add(
                        SelectedNodeEntry(
                            text = matchedNode.text,
                            source_package = sourcePackage,
                            bounds = matchedNode.bounds
                        )
                    )
                    highlightedRects.add(
                        RectF(
                            matchedNode.bounds.left.toFloat(),
                            matchedNode.bounds.top.toFloat(),
                            matchedNode.bounds.right.toFloat(),
                            matchedNode.bounds.bottom.toFloat()
                        )
                    )
                    invalidate()
                } else if (screenshotBitmap != null) {
                    // 2. Fallback to ML Kit OCR around the tapped coordinate
                    runMlKitOcrFallback(screenshotBitmap, tx, ty)
                }
                return true
            }
            return super.onTouchEvent(event)
        }

        private fun runMlKitOcrFallback(bmp: Bitmap, tx: Int, ty: Int) {
            try {
                val cropRadius = 180
                val left = (tx - cropRadius).coerceAtLeast(0)
                val top = (ty - cropRadius).coerceAtLeast(0)
                val right = (tx + cropRadius).coerceAtMost(bmp.width)
                val bottom = (ty + cropRadius).coerceAtMost(bmp.height)
                if (right <= left || bottom <= top) return

                val cropped = Bitmap.createBitmap(bmp, left, top, right - left, bottom - top)
                val image = InputImage.fromBitmap(cropped, 0)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val extracted = visionText.text.trim()
                        if (extracted.isNotEmpty()) {
                            val words = extracted.split(Regex("\\s+")).filter { it.isNotBlank() }
                            selectedWords.addAll(words)
                            val bounds = BoundsRect(left, top, right, bottom)
                            selectedNodes.add(
                                SelectedNodeEntry(
                                    text = extracted,
                                    source_package = sourcePackage,
                                    bounds = bounds
                                )
                            )
                            highlightedRects.add(RectF(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat()))
                            invalidate()
                        }
                    }
            } catch (e: Exception) {
                Log.e(Constants.TAG_OTNOR, "ML Kit OCR fallback error", e)
            }
        }
    }
}
