package com.jarvis.mobile.vision

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.util.Base64
import android.util.Log
import com.jarvis.mobile.automation.JarvisAccessibilityService
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object ScreenCapture {

    suspend fun takeScreenshotBase64(): String? = suspendCoroutine { cont ->
        val service = JarvisAccessibilityService.instance
        if (service == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            cont.resume(null)
            return@suspendCoroutine
        }

        try {
            service.takeScreenshot(
                android.view.Display.DEFAULT_DISPLAY,
                service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        try {
                            val hardwareBuffer = screenshot.hardwareBuffer
                            val colorSpace = screenshot.colorSpace
                            val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                            hardwareBuffer.close()

                            if (bitmap == null) {
                                cont.resume(null)
                                return
                            }

                            val scaled = Bitmap.createScaledBitmap(
                                bitmap,
                                (bitmap.width * 0.5f).toInt().coerceAtLeast(720),
                                (bitmap.height * 0.5f).toInt().coerceAtLeast(1280),
                                true
                            )
                            bitmap.recycle()

                            val stream = ByteArrayOutputStream()
                            scaled.compress(Bitmap.CompressFormat.JPEG, 75, stream)
                            scaled.recycle()

                            val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                            cont.resume(base64)
                        } catch (e: Exception) {
                            Log.e("ScreenCapture", "Error processing screenshot", e)
                            cont.resume(null)
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.e("ScreenCapture", "Screenshot failed: $errorCode")
                        cont.resume(null)
                    }
                }
            )
        } catch (e: Exception) {
            Log.e("ScreenCapture", "takeScreenshot exception", e)
            cont.resume(null)
        }
    }
}
