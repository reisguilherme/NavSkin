package br.com.gamemap.render

import android.app.UiModeManager
import android.content.ContentValues
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import androidx.car.app.SurfaceContainer
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import br.com.gamemap.GameMapApplication
import br.com.gamemap.data.MapStyleRepository
import br.com.gamemap.phone.MainActivity
import br.com.gamemap.preferences.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.runner.RunWith
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.maps.MapView

/** Real emulator GPU/phone checks. It does not emulate the Android Auto host or a vehicle. */
@RunWith(AndroidJUnit4::class)
class NativeMvpRenderingTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private var previousCoverage = MapBaseMode.WORLD

    @Before fun useDeterministicOfflineMap() {
        instrumentation.runOnMainSync {
            val preferences = (context.applicationContext as GameMapApplication).preferences
            previousCoverage = preferences.snapshot().baseMapMode
            preferences.setBaseMapMode(MapBaseMode.GOIANIA_OFFLINE)
        }
    }

    @After fun restoreMapCoverage() {
        instrumentation.runOnMainSync {
            (context.applicationContext as GameMapApplication).preferences.setBaseMapMode(previousCoverage)
        }
    }

    @Test fun completeThemesRetainCameraAndParkCartographyAcrossStyleAndFilterChanges() {
        val repository = MapStyleRepository(context)
        val capture = SurfaceCapture(context, 800, 480)
        var renderer: CarSurfaceRenderer? = null
        try {
            capture.expect(Palette.GTA)
            instrumentation.runOnMainSync {
                renderer = CarSurfaceRenderer(context, repository.styleFor(MapPreferences(baseMapMode = MapBaseMode.GOIANIA_OFFLINE)))
                renderer!!.onSurfaceAvailable(SurfaceContainer(capture.reader.surface, 800, 480, 160))
            }
            var generation = 0
            var camera: CameraPosition? = null
            for (theme in listOf(MapTheme.GTA_SA, MapTheme.ROSA_POP, MapTheme.RDR2)) {
                capture.expect(Palette.forTheme(theme))
                if (generation > 0) {
                    // Changing theme must preserve the existing all-disabled filter selection.
                    instrumentation.runOnMainSync {
                        renderer!!.applyStyle(repository.styleFor(MapPreferences(theme, emptySet(), MapBaseMode.GOIANIA_OFFLINE)))
                    }
                    generation = awaitSurfaceStyle(renderer!!, emptySet(), generation)
                    instrumentation.runOnMainSync { sameCamera(camera!!, renderer!!.cameraState) }
                    instrumentation.runOnMainSync {
                        renderer!!.reset()
                        renderer!!.applyStyle(repository.styleFor(MapPreferences(theme, baseMapMode = MapBaseMode.GOIANIA_OFFLINE)))
                    }
                }
                generation = awaitSurfaceStyle(renderer!!, PoiCategory.entries.toSet(), generation)
                instrumentation.runOnMainSync {
                    sameCamera(NativeMapRenderer.defaultCamera(), renderer!!.cameraState)
                }
                // These actual native frames are also the phone carousel's source previews.
                capture.awaitAndSave("native-v3-${theme.assetName}.png")
                instrumentation.runOnMainSync {
                    // The following transitions must preserve a camera moved by the user.
                    renderer!!.zoomBy(.5)
                    renderer!!.panBy(40f, -20f)
                    camera = renderer!!.cameraState
                }
                capture.expect(Palette.forTheme(theme, night = true))
                instrumentation.runOnMainSync {
                    renderer!!.applyStyle(repository.styleFor(MapPreferences(theme, emptySet(), MapBaseMode.GOIANIA_OFFLINE), true))
                }
                generation = awaitSurfaceStyle(renderer!!, emptySet(), generation)
                // Parks, roads, water and buildings remain even with every POI icon disabled.
                capture.awaitAndSave("native-${theme.assetName}-night-icons-hidden.png")
                instrumentation.runOnMainSync { sameCamera(camera!!, renderer!!.cameraState) }
            }
        } finally {
            instrumentation.runOnMainSync { renderer?.close() }
            capture.close()
        }
    }

    private fun awaitSurfaceStyle(renderer: CarSurfaceRenderer, enabled: Set<PoiCategory>, after: Int): Int {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45)
        while (System.nanoTime() < deadline) {
            var ready: Int? = null
            instrumentation.runOnMainSync {
                renderer.styleState?.let { state ->
                    if (state.requestedGeneration > after && state.loadedGeneration == state.requestedGeneration &&
                        state.idleGeneration == state.requestedGeneration) {
                        assertTrue(renderer.inspectLoadedStyle { style ->
                            for (category in PoiCategory.entries) {
                                val layer = checkNotNull(style.getLayer("poi-${category.id}"))
                                assertEquals(if (category in enabled) "visible" else "none", layer.visibility.value)
                            }
                            assertEquals("visible", checkNotNull(style.getLayer("vegetation-parks")).visibility.value)
                            assertNotNull(style.getSource("pois"))
                            // Native sprite loading is lazy when a category's layer is hidden.
                            for (category in enabled) {
                                assertNotNull("Native sprite must resolve locally: ${category.id}",
                                    style.getImage("poi-${category.id}"))
                            }
                        })
                        ready = state.requestedGeneration
                    }
                }
            }
            if (ready != null) {
                // Allow the Android compositor to deliver the native idle frame to ImageReader.
                Thread.sleep(250)
                return ready!!
            }
            Thread.sleep(50)
        }
        throw AssertionError("Native style did not become idle after generation $after")
    }

    @Test fun phoneThemeAndCategoryControlsPersistWithOptionalLocationPermission() {
        val permissions = context.packageManager.getPackageInfo(context.packageName,
            android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertTrue(permissions.any { it.endsWith("ACCESS_FINE_LOCATION") })
        assertTrue(permissions.any { it.endsWith("ACCESS_COARSE_LOCATION") })
        assertFalse(permissions.any { it.endsWith("ACCESS_BACKGROUND_LOCATION") })
        val application = context.applicationContext as GameMapApplication
        val original = application.preferences.snapshot()
        val nightMode = context.getSystemService(UiModeManager::class.java)
        val originalNightMode = if (context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
            UiModeManager.MODE_NIGHT_YES else UiModeManager.MODE_NIGHT_NO
        instrumentation.runOnMainSync {
            nightMode.setApplicationNightMode(UiModeManager.MODE_NIGHT_NO)
            application.preferences.setTheme(MapTheme.GTA_SA)
            PoiCategory.entries.forEach { application.preferences.setCategoryEnabled(it, true) }
        }
        val device = UiDevice.getInstance(instrumentation)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                waitForDescription(device, "Explorar mapa")
                var safeBottom = device.displayHeight
                scenario.onActivity { activity ->
                    safeBottom -= androidx.core.view.ViewCompat.getRootWindowInsets(activity.window.decorView)
                        ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())?.bottom ?: 0
                }
                val footerDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                while (waitForDescription(device, "Explorar mapa").visibleBounds.bottom > safeBottom &&
                    System.nanoTime() < footerDeadline) Thread.sleep(100)
                assertTrue("Opening carousel CTA overlaps system navigation",
                    waitForDescription(device, "Explorar mapa").visibleBounds.bottom <= safeBottom)
                savePhoneScreenshot("phone-theme-carousel.png")
                chooseTheme(device, MapTheme.GTA_SA)
                val fullThemeCardWidth = waitForDescription(device, "Tema GTA SA").visibleBounds.width()
                collapsePanel(device)
                scenario.onActivity { activity ->
                    val mapView = checkNotNull(findMapView(activity.window.decorView))
                    assertTrue("The collapsed phone UI should leave the map across the screen width",
                        mapView.width >= activity.window.decorView.width * .95)
                    assertTrue("The collapsed phone UI should leave most of the screen to the map",
                        mapView.height >= activity.window.decorView.height * .85)
                }
                awaitPhonePixels(Palette.GTA).also { saveImage(context, "phone-gta.png", it); it.recycle() }
                val beforeZoom = readPhoneCamera(scenario)
                waitForDescription(device, "Aumentar zoom").click()
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
                var afterZoom = readPhoneCamera(scenario)
                while (afterZoom.zoom < beforeZoom.zoom + .9 && System.nanoTime() < deadline) {
                    Thread.sleep(100)
                    afterZoom = readPhoneCamera(scenario)
                }
                assertTrue("The zoom control did not zoom the real native map", afterZoom.zoom >= beforeZoom.zoom + .9)
                assertEquals(beforeZoom.target!!.latitude, afterZoom.target!!.latitude, 0.00001)
                assertEquals(beforeZoom.target!!.longitude, afterZoom.target!!.longitude, 0.00001)
                waitForDescription(device, "Diminuir zoom").click()
                awaitPhoneCamera(scenario, beforeZoom)
                waitForDescription(device, "Voltar ao enquadramento inicial").click()
                awaitPhoneCamera(scenario, NativeMapRenderer.defaultCamera())
                val camera = readPhoneCamera(scenario)

                for (theme in listOf(MapTheme.ROSA_POP, MapTheme.RDR2)) {
                    waitForDescription(device, "Temas").click()
                    chooseTheme(device, theme)
                    if (theme == MapTheme.RDR2) awaitFullThemeCard(device, theme, fullThemeCardWidth)
                    assertEquals(theme, application.preferences.snapshot().theme)
                    assertNotNull("Selecting a card must keep the carousel open",
                        device.findObject(By.desc("Explorar mapa")))
                    savePhoneScreenshot("phone-${theme.assetName}-selected.png")
                    collapsePanel(device)
                    awaitPhonePixels(Palette.forTheme(theme)).also {
                        saveImage(context, "phone-${theme.assetName}.png", it); it.recycle()
                    }
                    sameCamera(camera, readPhoneCamera(scenario))
                }

                waitForDescription(device, "Filtros").click()
                for (category in PoiCategory.entries) {
                    clickScrollableControl(device, "Filtro ${category.label}", "Categorias do mapa")
                    awaitPreferenceCategories(PoiCategory.entries.toSet() - category)
                    awaitPhoneLayers(scenario, PoiCategory.entries.toSet() - category)
                    clickScrollableControl(device, "Filtro ${category.label}", "Categorias do mapa")
                    awaitPreferenceCategories(PoiCategory.entries.toSet())
                }
                clickScrollableControl(device, "Filtro ${PoiCategory.PARKS.label}", "Categorias do mapa")
                val selected = PoiCategory.entries.toSet() - PoiCategory.PARKS
                awaitPreferenceCategories(selected)
                awaitPhoneLayers(scenario, selected)
                savePhoneScreenshot("phone-filters.png")
                collapsePanel(device)
                waitForDescription(device, "RDR2 · 5/6 categorias ativas")

                // A different theme must not reset the user's independent POI filters.
                for (theme in listOf(MapTheme.ROSA_POP, MapTheme.RDR2)) {
                    waitForDescription(device, "Temas").click()
                    chooseTheme(device, theme)
                    if (theme == MapTheme.RDR2) awaitFullThemeCard(device, theme, fullThemeCardWidth)
                    collapsePanel(device)
                    awaitPhonePixels(Palette.forTheme(theme)).also { it.recycle() }
                    awaitPhoneLayers(scenario, selected)
                    assertEquals(selected, application.preferences.snapshot().enabledCategories)
                    sameCamera(camera, readPhoneCamera(scenario))
                }
                scenario.recreate()
                waitForDescription(device, "RDR2 · 5/6 categorias ativas")
                assertFalse("A collapsed panel reopened after Activity recreation",
                    device.hasObject(By.desc("Explorar mapa")))
                PreferenceStore(context).use { reopened ->
                    assertEquals(MapTheme.RDR2, reopened.snapshot().theme)
                    assertEquals(selected, reopened.snapshot().enabledCategories)
                }
                awaitPhoneLayers(scenario, selected)
                awaitPhonePixels(Palette.RDR2).also {
                    saveImage(context, "phone-rdr2-parks-hidden.png", it); it.recycle()
                }
            }
        } finally {
            instrumentation.runOnMainSync {
                application.preferences.setTheme(original.theme)
                PoiCategory.entries.forEach {
                    application.preferences.setCategoryEnabled(it, it in original.enabledCategories)
                }
                nightMode.setApplicationNightMode(originalNightMode)
            }
        }
    }

    @Test fun phoneRosaNightKeepsLightChromeAndVibrantMap() {
        val application = context.applicationContext as GameMapApplication
        val original = application.preferences.snapshot()
        val nightMode = context.getSystemService(UiModeManager::class.java)
        val originalNightMode = if (context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
            UiModeManager.MODE_NIGHT_YES else UiModeManager.MODE_NIGHT_NO
        instrumentation.runOnMainSync {
            nightMode.setApplicationNightMode(UiModeManager.MODE_NIGHT_YES)
            application.preferences.setTheme(MapTheme.ROSA_POP)
            PoiCategory.entries.forEach { application.preferences.setCategoryEnabled(it, true) }
        }
        val device = UiDevice.getInstance(instrumentation)
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                waitForDescription(device, "Explorar mapa")
                collapsePanel(device)
                awaitPhonePixels(Palette.ROSA_NIGHT).also { bitmap ->
                    try {
                        saveImage(context, "phone-rosa-night.png", bitmap)
                        val pixel = bitmap.getPixel(bitmap.width / 2,
                            (4 * context.resources.displayMetrics.density).toInt().coerceAtLeast(1))
                        assertTrue("Rosa's status-bar background should remain light in night mode",
                            android.graphics.Color.red(pixel) > 240 &&
                            android.graphics.Color.green(pixel) > 225 &&
                            android.graphics.Color.blue(pixel) > 230)
                    } finally { bitmap.recycle() }
                }
                waitForDescription(device, "Rosa Pop · 6/6 categorias ativas")
                waitForDescription(device, "Temas")
                waitForDescription(device, "Filtros")
            }
        } finally {
            instrumentation.runOnMainSync {
                application.preferences.setTheme(original.theme)
                PoiCategory.entries.forEach {
                    application.preferences.setCategoryEnabled(it, it in original.enabledCategories)
                }
                nightMode.setApplicationNightMode(originalNightMode)
            }
        }
    }

    private fun waitForDescription(device: UiDevice, description: String) =
        checkNotNull(device.wait(Until.findObject(By.desc(description)), 15000)) {
            "Missing accessible control: $description"
        }

    private fun chooseTheme(device: UiDevice, theme: MapTheme) {
        clickScrollableControl(device, "Tema ${theme.label}", "Prévia dos temas", horizontal = true)
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        val preferences = (context.applicationContext as GameMapApplication).preferences
        while (preferences.snapshot().theme != theme && System.nanoTime() < deadline) Thread.sleep(50)
        assertEquals("The theme card did not reach persisted preferences", theme, preferences.snapshot().theme)
    }

    private fun clickScrollableControl(device: UiDevice, description: String, container: String,
        horizontal: Boolean = false) {
        if (horizontal) {
            waitForDescription(device, container)
            assertTrue("Cannot reach $description in $container",
                UiScrollable(UiSelector().description(container)).setAsHorizontalList()
                    .scrollIntoView(UiSelector().description(description)))
            waitForDescription(device, description).click()
        } else revealSheetControl(device, description, container).click()
    }

    private fun collapsePanel(device: UiDevice) {
        val container = if (device.hasObject(By.desc("Categorias do mapa"))) "Categorias do mapa" else "Escolha do tema"
        revealSheetControl(device, "Explorar mapa", container).click()
        assertTrue("The panel did not retract", device.wait(Until.gone(By.desc("Explorar mapa")), 5000))
        waitForDescription(device, "Aumentar zoom")
    }

    private fun revealSheetControl(device: UiDevice, description: String, container: String): UiObject2 {
        waitForDescription(device, container)
        val order = PoiCategory.entries.map { "Filtro ${it.label}" } + "Explorar mapa"
        val targetIndex = order.indexOf(description)
        check(targetIndex >= 0) { "Unknown sheet control: $description" }
        val density = context.resources.displayMetrics.density
        val minimumVisibleHeight = (48 * density).toInt() - 2
        repeat(12) {
            val panel = device.findObject(By.desc(container))
                ?: failSheetScroll(device, description, "The panel disappeared while looking for $description")
            val viewport = panel.visibleBounds
            val target = device.findObject(By.desc(description))
            val targetBounds = target?.visibleBounds
            if (target != null && targetBounds != null && targetBounds.width() > 0 &&
                targetBounds.height() >= minimumVisibleHeight && viewport.contains(targetBounds)) return target

            val visible = order.mapIndexedNotNull { index, label ->
                val bounds = device.findObject(By.desc(label))?.visibleBounds
                if (bounds != null && !bounds.isEmpty && android.graphics.Rect.intersects(viewport, bounds)) index else null
            }
            // Search from the current position. Resetting a bottom sheet to its beginning
            // can transfer a downward drag to the sheet itself and dismiss it.
            val direction = when {
                targetBounds != null && !targetBounds.isEmpty && targetBounds.top <= viewport.top -> Direction.UP
                visible.isNotEmpty() && targetIndex < visible.min() -> Direction.UP
                else -> Direction.DOWN
            }
            panel.setGestureMarginPercentage(.12f)
            // A short, slow drag stays inside the scrollable content and avoids a hide fling.
            panel.scroll(direction, .18f, (density * 280).toInt())
            device.waitForIdle(500)
            if (!device.hasObject(By.desc(container)))
                failSheetScroll(device, description, "The $direction scroll dismissed $container while seeking $description")
        }
        failSheetScroll(device, description, "Cannot reach $description in $container after 12 bounded scrolls")
    }

    private fun failSheetScroll(device: UiDevice, description: String, message: String): Nothing {
        val name = "failed-scroll-${description.lowercase().replace(Regex("[^a-z0-9]+"), "-")}"
        runCatching { savePhoneScreenshot("$name.png") }
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "$name.xml")
                put(MediaStore.Downloads.MIME_TYPE, "application/xml")
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/GameMapV3/")
            }
            val uri = checkNotNull(context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
            checkNotNull(context.contentResolver.openOutputStream(uri)).use { device.dumpWindowHierarchy(it) }
        }
        throw AssertionError(message)
    }

    private fun awaitFullThemeCard(device: UiDevice, theme: MapTheme, fullWidth: Int) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8)
        var stableSince = 0L
        var previousBounds: android.graphics.Rect? = null
        while (System.nanoTime() < deadline) {
            val card = waitForDescription(device, "Tema ${theme.label}").visibleBounds
            val viewport = waitForDescription(device, "Prévia dos temas").visibleBounds
            val full = card.width() >= fullWidth - 2 && viewport.contains(card)
            if (full && previousBounds == card) {
                if (stableSince == 0L) stableSince = System.nanoTime()
                // Stay fully visible beyond the carousel's delayed settling animation.
                if (System.nanoTime() - stableSince >= TimeUnit.MILLISECONDS.toNanos(600)) return
            } else stableSince = 0L
            previousBounds = card
            Thread.sleep(100)
        }
        throw AssertionError("The final ${theme.label} card did not remain fully visible after scrolling")
    }

    private fun awaitPreferenceCategories(expected: Set<PoiCategory>) {
        val preferences = (context.applicationContext as GameMapApplication).preferences
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (preferences.snapshot().enabledCategories != expected && System.nanoTime() < deadline) Thread.sleep(50)
        assertEquals("Filter selection was not persisted", expected, preferences.snapshot().enabledCategories)
    }

    private fun awaitPhoneLayers(scenario: ActivityScenario<MainActivity>, expected: Set<PoiCategory>) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        while (System.nanoTime() < deadline) {
            val matched = AtomicBoolean(false)
            val observed = CountDownLatch(1)
            scenario.onActivity { activity ->
                checkNotNull(findMapView(activity.window.decorView)).getMapAsync { map ->
                    map.getStyle { style ->
                        matched.set(PoiCategory.entries.all { category ->
                            style.getLayer("poi-${category.id}")?.visibility?.value ==
                                if (category in expected) "visible" else "none"
                        })
                        observed.countDown()
                    }
                }
            }
            observed.await(1, TimeUnit.SECONDS)
            if (matched.get()) return
            Thread.sleep(50)
        }
        throw AssertionError("The native phone map did not apply filters $expected")
    }

    private fun awaitPhoneCamera(scenario: ActivityScenario<MainActivity>, expected: CameraPosition) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            val actual = readPhoneCamera(scenario)
            if (abs(expected.zoom - actual.zoom) < .001 &&
                abs(expected.target!!.latitude - actual.target!!.latitude) < .00001 &&
                abs(expected.target!!.longitude - actual.target!!.longitude) < .00001) return
            Thread.sleep(100)
        }
        sameCamera(expected, readPhoneCamera(scenario))
    }

    private fun savePhoneScreenshot(name: String) {
        checkNotNull(instrumentation.uiAutomation.takeScreenshot()).also {
            try { saveImage(context, name, it) } finally { it.recycle() }
        }
    }
    private fun readPhoneCamera(scenario: ActivityScenario<MainActivity>): CameraPosition {
        val answer = AtomicReference<CameraPosition>()
        val completed = CountDownLatch(1)
        scenario.onActivity { activity ->
            val view = checkNotNull(findMapView(activity.window.decorView))
            view.getMapAsync { answer.set(it.cameraPosition); completed.countDown() }
        }
        assertTrue(completed.await(5, TimeUnit.SECONDS))
        return answer.get()
    }

    private fun findMapView(view: View): MapView? {
        if (view is MapView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findMapView(view.getChildAt(i))?.let { return it }
        return null
    }

    private fun awaitPhonePixels(palette: Palette): Bitmap {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(40)
        val device = UiDevice.getInstance(instrumentation)
        while (System.nanoTime() < deadline) {
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            if (bitmap != null && device.hasObject(By.descContains("recursos e renderização prontos")) &&
                hasMapColors(bitmap, palette)) return bitmap
            bitmap?.recycle()
            Thread.sleep(200)
        }
        savePhoneScreenshot("failed-phone-${palette.name.lowercase()}.png")
        throw AssertionError("The phone did not render complete ${palette.name} cartography")
    }

    private fun sameCamera(expected: CameraPosition, actual: CameraPosition) {
        assertEquals(expected.target!!.latitude, actual.target!!.latitude, 0.000001)
        assertEquals(expected.target!!.longitude, actual.target!!.longitude, 0.000001)
        assertEquals(expected.zoom, actual.zoom, 0.000001)
        assertEquals(expected.bearing, actual.bearing, 0.000001)
    }

    private enum class Palette(val park: Int, val road: Int, val water: Int, val building: Int) {
        GTA(0xff748837.toInt(),0xff080a08.toInt(),0xff7088ab.toInt(),0xfff7fcf8.toInt()),
        GTA_NIGHT(0xff53662d.toInt(),0xff060805.toInt(),0xff4b6e91.toInt(),0xffb2bbae.toInt()),
        // Sample wide primary-road fills; thin casing colors are blended by antialiasing.
        ROSA(0xff6fe3bd.toInt(),0xffffdf79.toInt(),0xff64d7eb.toInt(),0xfffff3fb.toInt()),
        ROSA_NIGHT(0xff41ab99.toInt(),0xffefc46f.toInt(),0xff388ebc.toInt(),0xffb888bb.toInt()),
        RDR2(0xffb5af88.toInt(),0xff5c4030.toInt(),0xffa8b9ad.toInt(),0xffb6a17a.toInt()),
        RDR2_NIGHT(0xff777d5a.toInt(),0xff32271d.toInt(),0xff718f89.toInt(),0xffb19b75.toInt());

        companion object {
            fun forTheme(theme: MapTheme, night: Boolean = false): Palette = when (theme) {
                MapTheme.GTA_SA -> if (night) GTA_NIGHT else GTA
                MapTheme.ROSA_POP -> if (night) ROSA_NIGHT else ROSA
                MapTheme.RDR2 -> if (night) RDR2_NIGHT else RDR2
            }
        }
    }

    private class SurfaceCapture(private val context: Context, width: Int, height: Int) : AutoCloseable {
        val reader = ImageReader.newInstance(width,height,PixelFormat.RGBA_8888,3)
        private val worker = HandlerThread("MvpSurfaceCapture").apply { start() }
        private val handler = Handler(worker.looper)
        private val wanted = AtomicReference(Palette.GTA)
        private val frames = LinkedBlockingQueue<Bitmap>(1)
        private val latest = AtomicReference<Bitmap?>(null)
        init {
            reader.setOnImageAvailableListener({ source ->
                val frame = source.acquireLatestImage() ?: return@setOnImageAvailableListener
                frame.use {
                    val plane = frame.planes[0]
                    val pixels = plane.buffer
                    val colors = IntArray(width*height)
                    for(y in 0 until height) for(x in 0 until width) {
                        val i=y*plane.rowStride+x*plane.pixelStride
                        colors[y*width+x]=((pixels.get(i+3).toInt() and 255) shl 24) or
                            ((pixels.get(i).toInt() and 255) shl 16) or
                            ((pixels.get(i+1).toInt() and 255) shl 8) or (pixels.get(i+2).toInt() and 255)
                    }
                    val bitmap=Bitmap.createBitmap(colors,width,height,Bitmap.Config.ARGB_8888)
                    latest.getAndSet(bitmap.copy(Bitmap.Config.ARGB_8888, false))?.recycle()
                    if(hasMapColors(bitmap,wanted.get())) {
                        frames.poll()?.recycle()
                        if (!frames.offer(bitmap)) bitmap.recycle()
                    } else bitmap.recycle()
                }
            },handler)
        }
        fun expect(palette: Palette) { wanted.set(palette); while(true)(frames.poll() ?: break).recycle() }
        fun awaitAndSave(name: String) {
            val bitmap=frames.poll(40,TimeUnit.SECONDS) ?: run {
                latest.getAndSet(null)?.let { diagnostic ->
                    try { saveImage(context,"failed-${wanted.get().name.lowercase()}.png",diagnostic) }
                    finally { diagnostic.recycle() }
                }
                throw AssertionError("Surface did not render complete ${wanted.get()} geometry")
            }
            try { saveImage(context,name,bitmap) } finally { bitmap.recycle() }
        }
        override fun close() {
            reader.setOnImageAvailableListener(null,null)
            val done=CountDownLatch(1)
            handler.post {reader.close();done.countDown()}
            check(done.await(5,TimeUnit.SECONDS));worker.quitSafely();worker.join(2000)
            while(true)(frames.poll() ?: break).recycle()
            latest.getAndSet(null)?.recycle()
        }
    }

    companion object {
        private fun hasMapColors(bitmap: Bitmap, palette: Palette): Boolean {
            val colors=listOf(palette.park,palette.road,palette.water,palette.building)
            val counts=IntArray(4)
            for(y in bitmap.height/8 until bitmap.height*3/4 step 5)
                for(x in bitmap.width/10 until bitmap.width*9/10 step 5) {
                    val pixel=bitmap.getPixel(x,y)
                    colors.forEachIndexed { index, wanted ->
                        if(abs(android.graphics.Color.red(pixel)-android.graphics.Color.red(wanted))<=8 &&
                            abs(android.graphics.Color.green(pixel)-android.graphics.Color.green(wanted))<=8 &&
                            abs(android.graphics.Color.blue(pixel)-android.graphics.Color.blue(wanted))<=8)counts[index]++
                    }
                }
            return counts[0]>=6 && counts[1]>=20 && counts[2]>=3 && counts[3]>=15
        }
        private fun saveImage(context: Context,name: String,bitmap: Bitmap) {
            val directory = "Pictures/GameMapV3/"
            // Re-running instrumentation refreshes the same generated evidence and previews.
            context.contentResolver.delete(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                "${MediaStore.Images.Media.DISPLAY_NAME} = ? AND ${MediaStore.Images.Media.RELATIVE_PATH} = ?",
                arrayOf(name, directory))
            val values=ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME,name)
                put(MediaStore.Images.Media.MIME_TYPE,"image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH,directory)
                put(MediaStore.Images.Media.IS_PENDING,1)
            }
            val uri=checkNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values))
            try {
                checkNotNull(context.contentResolver.openOutputStream(uri)).use {
                    check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))
                }
                values.clear();values.put(MediaStore.Images.Media.IS_PENDING,0)
                context.contentResolver.update(uri,values,null,null)
            } catch (error: Exception) {
                context.contentResolver.delete(uri,null,null)
                throw error
            }
        }
    }
}
