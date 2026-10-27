package br.com.gamemap.render

import android.content.Context
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.Build
import android.provider.MediaStore
import androidx.car.app.SurfaceContainer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.camera.CameraPosition

/** Exercises the real GL/TextureView/VirtualDisplay path. This is not a DHU host test. */
@RunWith(AndroidJUnit4::class)
class NativeSurfaceProbeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun nativeMapProducesPixelsAndSurvivesResizeAndReconnect() {
        val statuses = LinkedBlockingQueue<String>()
        var renderer: CarSurfaceRenderer? = null
        val first = PixelSink(instrumentation.targetContext, 800, 480, "native-surface-probe.png")
        val resized = PixelSink(instrumentation.targetContext, 1024, 600, "native-surface-probe-resized.png")
        val reconnected = PixelSink(instrumentation.targetContext, 800, 480, "native-surface-probe-reconnected.png")
        try {
            instrumentation.runOnMainSync {
                renderer = CarSurfaceRenderer(instrumentation.targetContext, onStatus = { statuses.offer(it) })
                renderer!!.onSurfaceAvailable(first.container(160))
            }
            awaitFullFrame(statuses)
            first.awaitMapPixels()

            var beforeResize: CameraPosition? = null
            instrumentation.runOnMainSync {
                renderer!!.onVisibleAreaChanged(Rect(80, 40, 720, 420))
                renderer!!.onStableAreaChanged(Rect(60, 30, 740, 440))
                val padding = renderer!!.cameraState.padding
                assertNotNull(padding)
                assertEquals(80.0, padding!![0], 0.01)
                assertEquals(40.0, padding[1], 0.01)
                assertEquals(80.0, padding[2], 0.01)
                assertEquals(60.0, padding[3], 0.01)
                renderer!!.zoomBy(0.5)
                renderer!!.panBy(40f, -20f)
                beforeResize = renderer!!.cameraState
                statuses.clear()
                // Repeated availability is legal even when the Surface wrapper is unchanged.
                renderer!!.onSurfaceAvailable(first.container(240))
            }
            awaitFullFrame(statuses)
            instrumentation.runOnMainSync { assertSameCamera(beforeResize!!, renderer!!.cameraState) }

            instrumentation.runOnMainSync {
                statuses.clear()
                // No destroyed callback: the host can replace size/density directly.
                renderer!!.onSurfaceAvailable(resized.container(200))
            }
            awaitFullFrame(statuses)
            resized.awaitMapPixels()
            instrumentation.runOnMainSync { assertSameCamera(beforeResize!!, renderer!!.cameraState) }

            instrumentation.runOnMainSync {
                renderer!!.onSurfaceDestroyed(resized.container(200))
                statuses.clear()
                renderer!!.onSurfaceAvailable(reconnected.container(160))
            }
            awaitFullFrame(statuses)
            reconnected.awaitMapPixels()
            instrumentation.runOnMainSync {
                assertSameCamera(beforeResize!!, renderer!!.cameraState)
                renderer!!.close()
                renderer!!.close() // Session teardown is idempotent.
            }
        } finally {
            instrumentation.runOnMainSync { renderer?.close() }
            first.close()
            resized.close()
            reconnected.close()
        }
    }

    private fun awaitFullFrame(statuses: LinkedBlockingQueue<String>) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        val messages = mutableListOf<String>()
        while (System.nanoTime() < deadline) {
            val status = statuses.poll(250, TimeUnit.MILLISECONDS) ?: continue
            messages += status
            assertTrue("Native surface error: $status", !status.contains("falha", ignoreCase = true))
            if (status.contains("primeiro quadro completo")) return
        }
        throw AssertionError("No complete native map frame in 30 s. Status: $messages")
    }

    private fun assertSameCamera(expected: CameraPosition, actual: CameraPosition) {
        assertEquals(expected.zoom, actual.zoom, 0.001)
        assertEquals(expected.target!!.latitude, actual.target!!.latitude, 0.00001)
        assertEquals(expected.target!!.longitude, actual.target!!.longitude, 0.00001)
    }

    private class PixelSink(
        private val context: Context,
        private val width: Int,
        private val height: Int,
        private val screenshotName: String,
    ) : AutoCloseable {
        private val thread = HandlerThread("GameMapProbePixels").apply { start() }
        private val handler = Handler(thread.looper)
        private val colorfulFrames = LinkedBlockingQueue<Boolean>()
        private val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)
        private var screenshotSaved = false

        init {
            reader.setOnImageAvailableListener({ source ->
                source.acquireLatestImage()?.use { frame ->
                    if (hasMapPixels(frame)) {
                        if (!screenshotSaved) {
                            saveScreenshot(frame)
                            screenshotSaved = true
                        }
                        colorfulFrames.offer(true)
                    }
                }
            }, handler)
        }

        fun container(dpi: Int) = SurfaceContainer(reader.surface, width, height, dpi)

        fun awaitMapPixels() {
            assertEquals("The native map did not show the real Praça Cívica park fill and roads", true,
                colorfulFrames.poll(30, TimeUnit.SECONDS))
        }

        private fun hasMapPixels(frame: Image): Boolean {
            val plane = frame.planes.first()
            val pixels = plane.buffer
            val colors = mutableSetOf<Int>()
            var parkPixels = 0
            var roadPixels = 0
            // Keep the sample above y=360, including the resized case's inset footer at y=420.
            // Attribution text must never make an otherwise blank native map pass.
            for (y in height / 5 until height * 3 / 5 step 5) {
                for (x in width / 5 until width * 4 / 5 step 5) {
                    val offset = y * plane.rowStride + x * plane.pixelStride
                    if (offset + 3 >= pixels.limit()) continue
                    val alpha = pixels.get(offset + 3).toInt() and 0xff
                    if (alpha < 200) continue
                    val red = pixels.get(offset).toInt() and 0xff
                    val green = pixels.get(offset + 1).toInt() and 0xff
                    val blue = pixels.get(offset + 2).toInt() and 0xff
                    colors += ((red / 16) shl 8) or ((green / 16) shl 4) or (blue / 16)
                    if (green > red + 10 && green > blue + 20) parkPixels++
                    if (red < 24 && green < 24 && blue < 24) roadPixels++
                    if (colors.size >= 6 && parkPixels >= 5 && roadPixels >= 20) return true
                }
            }
            return false
        }

        private fun saveScreenshot(frame: Image) {
            val plane = frame.planes.first()
            val pixels = plane.buffer
            val argb = IntArray(width * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val offset = y * plane.rowStride + x * plane.pixelStride
                    val red = pixels.get(offset).toInt() and 0xff
                    val green = pixels.get(offset + 1).toInt() and 0xff
                    val blue = pixels.get(offset + 2).toInt() and 0xff
                    val alpha = pixels.get(offset + 3).toInt() and 0xff
                    argb[y * width + x] = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
                }
            }
            val bitmap = Bitmap.createBitmap(argb, width, height, Bitmap.Config.ARGB_8888)
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, screenshotName)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GameMapSurfaceProbe")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                    val uri = checkNotNull(context.contentResolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values,
                    ))
                    try {
                        checkNotNull(context.contentResolver.openOutputStream(uri)).use {
                            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                        }
                        values.clear()
                        values.put(MediaStore.Images.Media.IS_PENDING, 0)
                        context.contentResolver.update(uri, values, null, null)
                    } catch (error: Exception) {
                        context.contentResolver.delete(uri, null, null)
                        throw error
                    }
                } else {
                    context.openFileOutput(screenshotName, Context.MODE_PRIVATE).use {
                        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                    }
                }
            } finally {
                bitmap.recycle()
            }
        }

        override fun close() {
            reader.setOnImageAvailableListener(null, null)
            val closed = CountDownLatch(1)
            handler.post {
                try {
                    reader.close()
                } finally {
                    closed.countDown()
                }
            }
            assertTrue("Pixel reader did not close", closed.await(5, TimeUnit.SECONDS))
            thread.quitSafely()
            thread.join(2000)
        }
    }
}
