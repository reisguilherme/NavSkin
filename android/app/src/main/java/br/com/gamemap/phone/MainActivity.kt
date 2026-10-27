package br.com.gamemap.phone

import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.Switch
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.NestedScrollView
import br.com.gamemap.GameMapApplication
import br.com.gamemap.data.OfflineMapCoverage
import br.com.gamemap.R
import br.com.gamemap.preferences.MapPreferences
import br.com.gamemap.preferences.MapBaseMode
import br.com.gamemap.preferences.MapTheme
import br.com.gamemap.preferences.PoiCategory
import br.com.gamemap.render.NativeMapRenderer
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.maplibre.android.maps.MapView

/** Phone and car share preferences; each display retains its own map and camera. */
class MainActivity : ComponentActivity() {
    private val app get() = application as GameMapApplication
    private lateinit var renderer: NativeMapRenderer
    private lateinit var mapView: MapView
    private lateinit var root: FrameLayout
    private lateinit var navigationUi: PhoneNavigationUi
    private val insetProtections = mutableListOf<View>()
    private lateinit var topBar: LinearLayout
    private lateinit var locationPill: LinearLayout
    private lateinit var locationTitle: TextView
    private lateinit var selection: TextView
    private lateinit var logo: ImageView
    private lateinit var info: PhoneAction
    private lateinit var dock: LinearLayout
    private lateinit var themesAction: PhoneAction
    private lateinit var filtersAction: PhoneAction
    private lateinit var renderStatus: TextView
    private val themeCards = mutableMapOf<MapTheme, ThemeCard>()
    private val categorySwitches = mutableMapOf<PoiCategory, Switch>()
    private val mapModeChoices = mutableMapOf<MapBaseMode, RadioButton>()
    private val categoryDetails = mutableMapOf<PoiCategory, TextView>()
    private val sheetBindings = mutableListOf<(PhonePalette) -> Unit>()
    private var filterSummary: TextView? = null
    private var offlineRegionAction: TextView? = null
    private var sheet: BottomSheetDialog? = null
    private var sheetMode = SheetMode.THEMES
    private var removeListener: (() -> Unit)? = null
    private var removeNetworkListener: (() -> Unit)? = null
    private var removeTypography: (() -> Unit)? = null
    private var lastMapStatus = "Carregando mapa…"
    private var navigationGuidanceVisible = false
    private var bindingPreferences = false
    private var destroying = false
    private var bottomInset = 0
    private var insetsApplied = false
    private var palette = PhonePalette.forTheme(MapTheme.GTA_SA)
    private val counts by lazy { app.maps.categoryCounts() }
    private val offlineMissing by lazy { app.maps.offlineMissingCategories() }
    private val landscape get() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    private enum class SheetMode { NONE, THEMES, FILTERS }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        sheetMode = savedInstanceState?.getString(SHEET_STATE)?.let {
            runCatching { SheetMode.valueOf(it) }.getOrNull()
        } ?: if (app.navigation.snapshot().navigationActive || app.navigation.snapshot().resumeDestination != null) SheetMode.NONE else SheetMode.THEMES
        root = FrameLayout(this)
        mapView = NativeMapRenderer.createMapView(this).apply {
            contentDescription = "Mapa NavSkin. Carregando mapa"
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        root.addView(mapView, FrameLayout.LayoutParams(MATCH, MATCH))
        // Opaque system-bar insets give dark system icons a stable light surface in night maps too.
        listOf(Gravity.TOP, Gravity.BOTTOM, Gravity.START, Gravity.END).forEach { gravity ->
            val protection = View(this).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
            insetProtections.add(protection)
            root.addView(protection, FrameLayout.LayoutParams(
                if (gravity == Gravity.TOP || gravity == Gravity.BOTTOM) MATCH else 0,
                if (gravity == Gravity.TOP || gravity == Gravity.BOTTOM) 0 else MATCH, gravity))
        }
        buildMapControls()
        setContentView(root)
        removeTypography = GameTypography.observe(root) { app.preferences.snapshot().theme }
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            bottomInset = bars.bottom
            insetsApplied = true
            insetProtections.forEachIndexed { index, view ->
                view.layoutParams = (view.layoutParams as FrameLayout.LayoutParams).apply {
                    when (index) { 0 -> height = bars.top; 1 -> height = bars.bottom; 2 -> width = bars.left; 3 -> width = bars.right }
                }
            }
            position(topBar, Gravity.TOP, start = bars.left + dip(12), end = bars.right + dip(12), top = bars.top + dip(10))
            position(renderStatus, Gravity.TOP or Gravity.CENTER_HORIZONTAL, top = bars.top + dip(88))
            if (::navigationUi.isInitialized) navigationUi.insets(bars.left, bars.top, bars.right, bars.bottom)
            root.post(::openRestoredSheet)
            insets
        }
        renderer = NativeMapRenderer(this, mapView, currentStyle(app.preferences.snapshot()),
            onStatus = ::updateMapStatus, savedInstanceState = savedInstanceState)
        navigationUi = PhoneNavigationUi(this, root, renderer, restoredState = savedInstanceState,
            closeAppearanceSheet = { sheetMode = SheetMode.NONE; sheet?.dismiss() },
            openMapSettings = { showSheet(SheetMode.THEMES) },
            onGuidanceChanged = { visible ->
                if (navigationGuidanceVisible != visible) {
                    navigationGuidanceVisible = visible
                    refreshMapStatus()
                }
            })
        topBar.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, _ ->
            navigationUi.headerBottom(bottom)
            position(renderStatus, Gravity.TOP or Gravity.CENTER_HORIZONTAL, top = bottom + dip(12))
        }
        applyPreferences(app.preferences.snapshot())
        if (savedInstanceState == null) root.post { navigationUi.handleIntent(intent) }
        ViewCompat.requestApplyInsets(root)
    }

    private fun buildMapControls() {
        topBar = LinearLayout(this).apply {
            id = R.id.phone_header
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        root.addView(topBar, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP))
        locationPill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dip(10), dip(10), dip(10), dip(10))
            minimumHeight = dip(60)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }
        logo = phoneIcon(R.drawable.phone_map).apply { setPadding(dip(5), dip(5), dip(5), dip(5)) }
        locationPill.addView(logo, LinearLayout.LayoutParams(dip(30), dip(30)))
        locationTitle = phoneText("NavSkin", 16f, true).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER }
        selection = phoneText("Mundo · online", 10f).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER }
        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(locationTitle)
            addView(selection, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dip(4) })
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        locationPill.addView(brand, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dip(7) })
        topBar.addView(locationPill, if (landscape) LinearLayout.LayoutParams(dip(214), WRAP)
            else LinearLayout.LayoutParams(0, WRAP, 1f))
        info = PhoneAction(this, R.drawable.phone_menu, "Menu NavSkin").apply {
            id = R.id.phone_main_menu
            setOnClickListener { navigationUi.showOptions() }
        }
        dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            minimumHeight = dip(60)
            setPadding(dip(2), 0, dip(2), 0)
        }
        themesAction = PhoneAction(this, R.drawable.phone_layers, "Temas", true, stacked = true).apply { setOnClickListener { showSheet(SheetMode.THEMES) } }
        filtersAction = PhoneAction(this, R.drawable.phone_filters, "Filtros", true, stacked = true).apply { setOnClickListener { showSheet(SheetMode.FILTERS) } }
        dock.addView(themesAction, LinearLayout.LayoutParams(dip(52), WRAP))
        dock.addView(filtersAction, LinearLayout.LayoutParams(dip(52), WRAP))
        topBar.addView(dock, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dip(8) })
        if (landscape) topBar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        topBar.addView(info, LinearLayout.LayoutParams(dip(48), dip(60)).apply { marginStart = dip(8) })
        renderStatus = phoneText("Carregando mapa…", 12f).apply {
            gravity = Gravity.CENTER
            setPadding(dip(12), dip(9), dip(12), dip(9))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            maxWidth = dip(300)
            isClickable = true
            isFocusable = true
            setOnClickListener { showSheet(SheetMode.THEMES) }
        }
        root.addView(renderStatus, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
    }

    private fun updateMapStatus(status: String) {
        lastMapStatus = status
        refreshMapStatus()
    }

    private fun refreshMapStatus() {
        val status = lastMapStatus
        val mode = app.preferences.snapshot().baseMapMode
        val onlineMap = mode == MapBaseMode.WORLD
        val accessibleStatus = status.substringAfter("MapLibre Native: ").substringBefore(" · estilo ")
        mapView.contentDescription = "Mapa ${mode.label}. $accessibleStatus"
        when {
            onlineMap && !app.mapConnectivity.isOnline -> {
                renderStatus.text = "Mapa mundial sem internet. Toque para escolher Goiânia offline."
                renderStatus.visibility = View.VISIBLE
            }
            status.contains("falha", ignoreCase = true) -> {
                renderStatus.text = if (onlineMap) "Não foi possível carregar o mapa mundial. Toque para escolher Goiânia offline."
                    else "Não foi possível abrir o mapa offline. Toque para escolher outro mapa."
                renderStatus.visibility = View.VISIBLE
            }
            !status.contains("recursos e renderização prontos") -> {
                renderStatus.text = if (onlineMap) "Carregando mapa mundial…" else "Carregando mapa de Goiânia offline…"
                renderStatus.visibility = View.VISIBLE
            }
            else -> renderStatus.visibility = View.GONE
        }
        if (::navigationUi.isInitialized) navigationUi.mapNotice(
            renderStatus.text.toString().takeIf { renderStatus.visibility == View.VISIBLE })
        // During guidance the same notice is carried by the trip summary and menu.
        if (navigationGuidanceVisible) renderStatus.visibility = View.GONE
    }

    private fun applyPreferences(preferences: MapPreferences) {
        GameTypography.apply(root, preferences.theme)
        sheet?.window?.decorView?.let { GameTypography.apply(it, preferences.theme) }
        renderer.applyStyle(currentStyle(preferences))
        palette = PhonePalette.forTheme(preferences.theme)
        root.setBackgroundColor(palette.soft)
        insetProtections.forEach { it.setBackgroundColor(palette.surface) }
        locationPill.surface(palette, 24)
        locationPill.contentDescription = "NavSkin · ${preferences.baseMapMode.label} · ${preferences.theme.label} · ${preferences.enabledCategories.size}/${PoiCategory.entries.size} categorias ativas"
        selection.text = "${if (preferences.baseMapMode == MapBaseMode.WORLD) "Mundo" else "Goiânia offline"} · ${preferences.theme.label}"
        locationTitle.setTextColor(palette.text)
        selection.setTextColor(palette.muted)
        logo.background = rounded(palette.soft, 13)
        logo.imageTintList = ColorStateList.valueOf(palette.accent)
        info.bind(palette)
        info.touchSurface(palette.surface, 18)
        info.elevation = dip(6).toFloat()
        dock.surface(palette, 24)
        themesAction.bind(palette)
        filtersAction.bind(palette)
        renderStatus.setTextColor(palette.text)
        renderStatus.surface(palette, 14)
        if (::navigationUi.isInitialized) navigationUi.themeChanged()
        sheetBindings.forEach { it(palette) }
        themeCards.forEach { (theme, card) -> card.bind(theme == preferences.theme, palette) }
        bindingPreferences = true
        mapModeChoices.forEach { (mode, choice) ->
            choice.isChecked = preferences.baseMapMode == mode
            choice.setTextColor(palette.text)
            choice.buttonTintList = ColorStateList.valueOf(palette.accent)
        }
        offlineRegionAction?.visibility = if (preferences.baseMapMode == MapBaseMode.GOIANIA_OFFLINE &&
            renderer.cameraState.target?.let { !OfflineMapCoverage.contains(it.latitude, it.longitude) } == true)
            View.VISIBLE else View.GONE
        categoryDetails.forEach { (category, label) ->
            label.text = if (preferences.baseMapMode == MapBaseMode.WORLD) "Locais no mapa mundial"
                else if (category in offlineMissing) "Não incluído neste snapshot"
                else "${counts[category] ?: 0} locais no recorte offline"
        }
        categorySwitches.forEach { (category, toggle) ->
            toggle.isChecked = category in preferences.enabledCategories
            toggle.thumbTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(palette.accent, palette.muted))
            toggle.trackTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(palette.soft, palette.border))
        }
        bindingPreferences = false
        val total = preferences.enabledCategories.sumOf { counts[it] ?: 0 }
        filterSummary?.text = when {
            preferences.enabledCategories.isEmpty() -> "Nenhuma categoria ativa · o desenho do mapa continua visível"
            preferences.baseMapMode == MapBaseMode.WORLD -> "${preferences.enabledCategories.size} de ${PoiCategory.entries.size} categorias · online, varia por área"
            preferences.enabledCategories.size == PoiCategory.entries.size -> "Todas as categorias · $total locais no recorte offline"
            else -> "${preferences.enabledCategories.size} de ${PoiCategory.entries.size} categorias · $total locais no recorte offline"
        }
        refreshMapStatus()
        sheet?.window?.navigationBarColor = palette.surface
    }

    private fun showSheet(mode: SheetMode) {
        if (isFinishing || destroying || mode == SheetMode.NONE) return
        sheet?.setOnDismissListener(null)
        sheet?.dismiss()
        sheetMode = mode
        sheetBindings.clear()
        themeCards.clear()
        categorySwitches.clear()
        mapModeChoices.clear()
        categoryDetails.clear()
        offlineRegionAction = null
        filterSummary = null
        val dialog = BottomSheetDialog(this)
        sheet = dialog
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dip(20), dip(8), dip(20), dip(20))
        }
        sheetBindings.add { content.background = rounded(it.surface, 28) }
        content.addView(View(this).also { handle -> sheetBindings.add { handle.background = rounded(it.border, 2) } },
            LinearLayout.LayoutParams(dip(36), dip(4)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dip(14) })
        val titleRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(sheetLabel(if (mode == SheetMode.THEMES) "SEU MAPA, SEU ESTILO" else "EXPLORE DO SEU JEITO", 10f, true, muted = true))
        titles.addView(sheetLabel(if (mode == SheetMode.THEMES) "Escolha seu universo" else "O que você quer ver?", 22f, true),
            LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dip(5) })
        titleRow.addView(titles, LinearLayout.LayoutParams(0, WRAP, 1f))
        val close = PhoneAction(this, R.drawable.phone_close, "Fechar menu").apply { setOnClickListener { dialog.dismiss() } }
        sheetBindings.add { close.bind(it) }
        titleRow.addView(close, LinearLayout.LayoutParams(dip(48), dip(48)))
        content.addView(titleRow)
        buildMapModes(content)
        if (mode == SheetMode.THEMES) buildThemes(content) else buildFilters(content)
        val explore = PhoneAction(this, R.drawable.phone_arrow, "Explorar mapa", true).apply { setOnClickListener { dialog.dismiss() } }
        sheetBindings.add { explore.bind(it, filled = true) }
        // Let Material distinguish scrolling to earlier rows from dragging the sheet.
        val scroll = NestedScrollView(this).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            contentDescription = if (mode == SheetMode.FILTERS) "Categorias do mapa" else "Escolha do tema"
            addView(content, ViewGroup.LayoutParams(MATCH, WRAP))
        }
        // Keep the primary action reachable while long category/theme content scrolls.
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dip(20), dip(12), dip(20), dip(12) + bottomInset)
            addView(explore, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        sheetBindings.add { footer.setBackgroundColor(it.surface) }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipToOutline = true
            addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))
            addView(footer, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        sheetBindings.add { panel.background = rounded(it.surface, 28) }
        ViewCompat.setOnApplyWindowInsetsListener(panel) { _, insets ->
            val safeInset = maxOf(bottomInset, insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom)
            footer.setPadding(dip(20), dip(12), dip(20), dip(12) + safeInset)
            insets
        }
        val available = resources.displayMetrics.heightPixels - bottomInset - dip(24)
        // Compact on launch; large fonts and landscape can scroll the complete menu.
        val desired = (if (mode == SheetMode.THEMES) dip(556) else dip(830)) + bottomInset
        val height = desired.coerceAtMost((available * .88f).toInt()).coerceAtLeast(dip(150))
        dialog.setContentView(panel, ViewGroup.LayoutParams(MATCH, height))
        dialog.setOnShowListener {
            ViewCompat.requestApplyInsets(panel)
            dialog.window?.decorView?.let { GameTypography.observe(it) { app.preferences.snapshot().theme } }
            dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.let {
                it.setBackgroundColor(Color.TRANSPARENT)
                it.layoutParams.height = height
                BottomSheetBehavior.from(it).apply { peekHeight = height; skipCollapsed = true; state = BottomSheetBehavior.STATE_EXPANDED }
            }
            dialog.window?.let {
                it.navigationBarColor = palette.surface
                WindowInsetsControllerCompat(it, it.decorView).isAppearanceLightNavigationBars = true
            }
        }
        dialog.setOnDismissListener {
            if (sheet === dialog && !destroying) {
                sheet = null
                sheetMode = SheetMode.NONE
                sheetBindings.clear()
                themeCards.clear()
                categorySwitches.clear()
                mapModeChoices.clear()
                categoryDetails.clear()
                offlineRegionAction = null
                filterSummary = null
            }
        }
        applyPreferences(app.preferences.snapshot())
        dialog.show()
    }

    private fun buildThemes(content: LinearLayout) {
        content.addView(sheetLabel("Três estilos para explorar o mundo.", 13f, muted = true),
            LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dip(8); bottomMargin = dip(16) })
        val carousel = ThemeCarousel(this)
        val cards = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        MapTheme.entries.forEachIndexed { index, theme ->
            val card = ThemeCard(theme).apply {
                setOnClickListener {
                    app.preferences.setTheme(theme)
                    carousel.smoothScrollTo(index * carousel.cardStep, 0)
                }
            }
            themeCards[theme] = card
            cards.addView(card, LinearLayout.LayoutParams(dip(212), WRAP).apply { marginEnd = dip(12) })
        }
        carousel.addView(cards, ViewGroup.LayoutParams(WRAP, WRAP))
        content.addView(carousel, LinearLayout.LayoutParams(MATCH, WRAP))
        carousel.post { carousel.scrollTo(MapTheme.entries.indexOf(app.preferences.snapshot().theme) * carousel.cardStep, 0) }
    }

    private fun buildFilters(content: LinearLayout) {
        filterSummary = sheetLabel("", 12f, muted = true)
        content.addView(filterSummary, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dip(8); bottomMargin = dip(12) })
        val bulk = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Todos" to true, "Nenhum" to false).forEach { (label, enabled) ->
            val chip = sheetLabel(label, 12f, true).apply {
                gravity = Gravity.CENTER
                setPadding(dip(18), dip(9), dip(18), dip(9))
                minimumHeight = dip(48)
                isClickable = true; isFocusable = true
                contentDescription = if (enabled) "Ativar todos os filtros" else "Desativar todos os filtros"
                setOnClickListener {
                    PoiCategory.entries.forEach { category ->
                        if ((category in app.preferences.snapshot().enabledCategories) != enabled)
                            app.preferences.setCategoryEnabled(category, enabled)
                    }
                }
            }
            sheetBindings.add { chip.touchSurface(it.soft, 16) }
            bulk.addView(chip, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = dip(8) })
        }
        content.addView(bulk, LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dip(8) })
        PoiCategory.entries.forEach { category ->
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dip(68)
                setPadding(dip(10), dip(9), dip(8), dip(9))
            }
            val icon = phoneIcon(categoryIcon(category)).apply { setPadding(dip(9), dip(9), dip(9), dip(9)) }
            sheetBindings.add {
                icon.imageTintList = ColorStateList.valueOf(it.accent)
                icon.background = rounded(it.soft, 13)
                row.background = rounded(it.surface, 16, it.border)
            }
            row.addView(icon, LinearLayout.LayoutParams(dip(40), dip(40)))
            val words = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            words.addView(sheetLabel(category.label, 14f, true))
            val detail = sheetLabel("", 11f, muted = true)
            categoryDetails[category] = detail
            words.addView(detail,
                LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dip(4) })
            row.addView(words, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dip(12); marginEnd = dip(8) })
            val toggle = Switch(this).apply {
                showText = false
                contentDescription = "Filtro ${category.label}"
                minimumHeight = dip(48)
                minimumWidth = dip(48)
                setOnCheckedChangeListener { _, enabled ->
                    if (!bindingPreferences) app.preferences.setCategoryEnabled(category, enabled)
                }
            }
            categorySwitches[category] = toggle
            row.setOnClickListener { toggle.isChecked = !toggle.isChecked }
            row.addView(toggle, LinearLayout.LayoutParams(WRAP, WRAP))
            content.addView(row, LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dip(7) })
        }
    }

    private fun buildMapModes(content: LinearLayout) {
        content.addView(sheetLabel("Cobertura do mapa", 13f, true),
            LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dip(16); bottomMargin = dip(4) })
        MapBaseMode.entries.forEach { mode ->
            val choice = RadioButton(this).apply {
                text = mode.label
                textSize = 14f
                minimumHeight = dip(48)
                contentDescription = "Cobertura ${mode.label}"
                setOnClickListener { app.preferences.setBaseMapMode(mode) }
            }
            mapModeChoices[mode] = choice
            content.addView(choice, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        content.addView(sheetLabel("Mundo usa internet. Goiânia offline cobre somente o recorte incluído.", 11f, muted = true),
            LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dip(2); bottomMargin = dip(4) })
        offlineRegionAction = sheetLabel("Ver região offline", 14f, true).apply {
            minimumHeight = dip(48)
            gravity = Gravity.CENTER
            isClickable = true; isFocusable = true
            contentDescription = "Ver região offline de Goiânia"
            setOnClickListener { renderer.reset(); sheet?.dismiss() }
            sheetBindings.add { touchSurface(it.soft, 14) }
            content.addView(this, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dip(8) })
        }
    }

    private fun sheetLabel(value: String, size: Float, bold: Boolean = false, muted: Boolean = false): TextView =
        phoneText(value, size, bold).also { label -> sheetBindings.add { label.setTextColor(if (muted) it.muted else it.text) } }

    private inner class ThemeCard(val theme: MapTheme) : LinearLayout(this@MainActivity) {
        private val title = phoneText(theme.label, 16f, true)
        private val description = phoneText(when (theme.assetName) {
            "rosa" -> "Cores doces, cidade viva"
            "rdr2" -> "Papel, terra e horizonte"
            else -> "O clássico das ruas"
        }, 11f)
        private val selectedIcon = phoneIcon(R.drawable.phone_check)
        init {
            orientation = VERTICAL
            setPadding(dip(2), dip(2), dip(2), dip(2))
            isClickable = true; isFocusable = true
            contentDescription = "Tema ${theme.label}"
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            val preview = FrameLayout(context)
            val image = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                runCatching { assets.open("maps/previews/${theme.assetName}.webp").use { BitmapFactory.decodeStream(it) } }
                    .getOrNull()?.let { setImageBitmap(it) }
                setBackgroundColor(PhonePalette.forTheme(theme).soft)
            }
            preview.addView(image, FrameLayout.LayoutParams(MATCH, MATCH))
            selectedIcon.setPadding(dip(4), dip(4), dip(4), dip(4))
            preview.addView(selectedIcon, FrameLayout.LayoutParams(dip(27), dip(27), Gravity.TOP or Gravity.END).apply {
                topMargin = dip(9); marginEnd = dip(9)
            })
            addView(preview, LinearLayout.LayoutParams(MATCH, dip(102)))
            val words = LinearLayout(context).apply {
                orientation = VERTICAL
                setPadding(dip(13), dip(11), dip(13), dip(12))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                addView(title)
                addView(description, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dip(5) })
            }
            addView(words, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        fun bind(selected: Boolean, colors: PhonePalette) {
            isSelected = selected
            ViewCompat.setStateDescription(this, if (selected) "Selecionado" else "Disponível")
            touchSurface(colors.surface, 18, if (selected) colors.accent else colors.border, if (selected) 2 else 1)
            title.setTextColor(colors.text)
            description.setTextColor(colors.muted)
            selectedIcon.visibility = if (selected) View.VISIBLE else View.GONE
            selectedIcon.background = rounded(colors.accent, 20)
            selectedIcon.imageTintList = ColorStateList.valueOf(Color.WHITE)
        }
    }

    private fun position(view: View, gravity: Int, start: Int = 0, top: Int = 0, end: Int = 0, bottom: Int = 0) {
        (view.layoutParams as FrameLayout.LayoutParams).let {
            it.gravity = gravity; it.marginStart = start; it.topMargin = top; it.marginEnd = end; it.bottomMargin = bottom
            view.layoutParams = it
        }
    }
    private fun currentStyle(preferences: MapPreferences): String = app.maps.styleFor(preferences,
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
    override fun onStart() {
        super.onStart()
        renderer.start()
        navigationUi.start()
        removeListener = app.preferences.addListener(::applyPreferences)
        removeNetworkListener = app.mapConnectivity.addListener {
            refreshMapStatus()
            navigationUi.mapAvailabilityChanged()
        }
    }
    override fun onPostResume() {
        super.onPostResume()
        openRestoredSheet()
    }
    private fun openRestoredSheet() {
        if (insetsApplied && sheet == null && sheetMode != SheetMode.NONE &&
            lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) showSheet(sheetMode)
    }
    override fun onResume() { super.onResume(); renderer.resume() }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        navigationUi.handleIntent(intent)
    }
    override fun onPause() { renderer.pause(); super.onPause() }
    override fun onStop() {
        navigationUi.stop()
        removeListener?.invoke(); removeListener = null
        removeNetworkListener?.invoke(); removeNetworkListener = null
        renderer.stop(); super.onStop()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(SHEET_STATE, sheetMode.name)
        navigationUi.saveState(outState)
        renderer.saveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }
    override fun onLowMemory() { super.onLowMemory(); renderer.onLowMemory() }
    override fun onDestroy() {
        removeTypography?.invoke(); removeTypography = null
        destroying = true
        sheet?.setOnDismissListener(null)
        sheet?.dismiss()
        sheet = null
        navigationUi.close()
        renderer.close()
        super.onDestroy()
    }
    companion object {
        private const val SHEET_STATE = "gamemap.phone.sheet"
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
