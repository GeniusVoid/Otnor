package com.brainer.otnor.andropi.commands

import android.content.Context
import android.graphics.BitmapFactory
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.graphics.ImageFormat
import android.media.ImageReader
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.speech.tts.TextToSpeech
import android.util.Log
import com.brainer.otnor.andropi.CommandResult
import com.brainer.otnor.util.Constants
import com.brainer.otnor.util.JsonHelper.getFloatOrDefault
import com.brainer.otnor.util.JsonHelper.getIntOrDefault
import com.brainer.otnor.util.JsonHelper.getStringOrDefault
import com.brainer.otnor.util.JsonHelper.getStringOrNull
import com.google.gson.JsonObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class MediaCommands(private val context: Context) {

    private var tts: TextToSpeech? = null
    @Volatile
    private var ttsReady = false

    private var mediaRecorder: MediaRecorder? = null
    private var currentRecordPath: String? = null
    private var recordStartTimeMs: Long = 0L

    private var mediaPlayer: MediaPlayer? = null

    init {
        initTts()
    }

    private fun initTts() {
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                ttsReady = (status == TextToSpeech.SUCCESS)
            }
        } catch (e: Exception) {
            Log.w(Constants.TAG_ANDROPI, "TTS initialization failed: ${e.message}")
        }
    }

    fun ttsSpeak(body: JsonObject): CommandResult {
        val text = body.getStringOrNull("text") ?: return CommandResult.error("Missing 'text'")
        val lang = body.getStringOrDefault("language", "en")
        val pitch = body.getFloatOrDefault("pitch", 1.0f)
        val speed = body.getFloatOrDefault("speed", 1.0f)

        val engine = tts ?: return CommandResult.error("TTS engine unavailable")
        if (!ttsReady) {
            initTts()
        }

        engine.language = Locale.forLanguageTag(lang)
        engine.setPitch(pitch)
        engine.setSpeechRate(speed)
        val res = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "otnor_utt_${System.currentTimeMillis()}")
        return CommandResult.ok(mapOf("spoken" to (res == TextToSpeech.SUCCESS), "text" to text))
    }

    fun ttsStop(): CommandResult {
        tts?.stop()
        return CommandResult.ok(mapOf("stopped" to true))
    }

    fun ttsVoices(): CommandResult {
        val voices = tts?.voices?.map { v ->
            mapOf(
                "name" to v.name,
                "locale" to v.locale.toLanguageTag(),
                "quality" to v.quality,
                "network_required" to v.isNetworkConnectionRequired
            )
        } ?: emptyList()
        return CommandResult.ok(mapOf("voices" to voices, "count" to voices.size))
    }

    @Synchronized
    fun audioRecordStart(body: JsonObject): CommandResult {
        val outputPath = body.getStringOrNull("output_path")
            ?: File(context.filesDir, "recording_${System.currentTimeMillis()}.m4a").absolutePath
        val maxDurationSec = body.getIntOrDefault("max_duration_seconds", 60)

        stopRecorderSafely()
        val outFile = File(outputPath)
        outFile.parentFile?.mkdirs()

        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        recorder.setAudioEncodingBitRate(128_000)
        recorder.setAudioSamplingRate(44_100)
        recorder.setOutputFile(outFile.absolutePath)
        recorder.setMaxDuration(maxDurationSec * 1000)
        recorder.prepare()
        recorder.start()

        mediaRecorder = recorder
        currentRecordPath = outFile.absolutePath
        recordStartTimeMs = System.currentTimeMillis()

        return CommandResult.ok(
            mapOf(
                "recording" to true,
                "output_path" to outFile.absolutePath,
                "max_duration_seconds" to maxDurationSec
            )
        )
    }

    @Synchronized
    fun audioRecordStop(): CommandResult {
        val path = currentRecordPath ?: return CommandResult.error("No active recording")
        val durationSec = ((System.currentTimeMillis() - recordStartTimeMs) / 100.0).roundToInt() / 10.0
        stopRecorderSafely()
        val file = File(path)
        return CommandResult.ok(
            mapOf(
                "path" to file.absolutePath,
                "duration_seconds" to durationSec,
                "size_bytes" to if (file.exists()) file.length() else 0L
            )
        )
    }

    private fun stopRecorderSafely() {
        try {
            mediaRecorder?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaRecorder?.release()
        } catch (_: Exception) {
        }
        mediaRecorder = null
    }

    @Synchronized
    fun audioPlay(body: JsonObject): CommandResult {
        val path = body.getStringOrNull("path") ?: return CommandResult.error("Missing 'path'")
        val file = File(path)
        if (!file.exists()) return CommandResult.error("Audio file does not exist: $path")

        audioStop()
        val player = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            prepare()
            start()
        }
        mediaPlayer = player
        return CommandResult.ok(
            mapOf(
                "playing" to true,
                "path" to file.absolutePath,
                "duration_ms" to player.duration
            )
        )
    }

    @Synchronized
    fun audioStop(): CommandResult {
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
        return CommandResult.ok(mapOf("stopped" to true))
    }

    @Suppress("MissingPermission")
    fun cameraPhoto(body: JsonObject): CommandResult {
        val lensFacingPref = body.getStringOrDefault("camera", "back").lowercase()
        val outputPath = body.getStringOrNull("output_path")
            ?: File(context.filesDir, "brainer_photo_${System.currentTimeMillis()}.jpg").absolutePath

        val outFile = File(outputPath)
        outFile.parentFile?.mkdirs()

        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val targetFacing = if (lensFacingPref == "front") {
            CameraCharacteristics.LENS_FACING_FRONT
        } else {
            CameraCharacteristics.LENS_FACING_BACK
        }

        val cameraId = cm.cameraIdList.firstOrNull { id ->
            val chars = cm.getCameraCharacteristics(id)
            chars.get(CameraCharacteristics.LENS_FACING) == targetFacing
        } ?: cm.cameraIdList.firstOrNull()
            ?: return CommandResult.error("No camera available on device")

        val thread = HandlerThread("OtnorCameraThread").apply { start() }
        val handler = Handler(thread.looper)
        val latch = CountDownLatch(1)
        var captureError: String? = null
        val imageReader = ImageReader.newInstance(1920, 1080, ImageFormat.JPEG, 1)

        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            if (image != null) {
                try {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    FileOutputStream(outFile).use { it.write(bytes) }
                } catch (e: Exception) {
                    captureError = e.message
                } finally {
                    image.close()
                    latch.countDown()
                }
            }
        }, handler)

        var openedCamera: CameraDevice? = null
        try {
            cm.openCamera(
                cameraId,
                object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        openedCamera = camera
                        try {
                            val surface = imageReader.surface
                            val requestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                                addTarget(surface)
                                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                            }
                            @Suppress("DEPRECATION")
                            camera.createCaptureSession(
                                listOf(surface),
                                object : CameraCaptureSession.StateCallback() {
                                    override fun onConfigured(session: CameraCaptureSession) {
                                        try {
                                            session.capture(requestBuilder.build(), null, handler)
                                        } catch (e: Exception) {
                                            captureError = e.message
                                            latch.countDown()
                                        }
                                    }

                                    override fun onConfigureFailed(session: CameraCaptureSession) {
                                        captureError = "Camera capture session configuration failed"
                                        latch.countDown()
                                    }
                                },
                                handler
                            )
                        } catch (e: Exception) {
                            captureError = e.message
                            latch.countDown()
                        }
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        camera.close()
                        latch.countDown()
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        captureError = "Camera error code: $error"
                        camera.close()
                        latch.countDown()
                    }
                },
                handler
            )

            latch.await(8, TimeUnit.SECONDS)
        } catch (e: Exception) {
            captureError = e.message
        } finally {
            try {
                openedCamera?.close()
            } catch (_: Exception) {
            }
            try {
                imageReader.close()
            } catch (_: Exception) {
            }
            thread.quitSafely()
        }

        if (captureError != null || !outFile.exists()) {
            return CommandResult.error(captureError ?: "Failed capturing photo")
        }

        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(outFile.absolutePath, opts)

        return CommandResult.ok(
            mapOf(
                "path" to outFile.absolutePath,
                "width" to opts.outWidth,
                "height" to opts.outHeight,
                "size_bytes" to outFile.length()
            )
        )
    }
}
