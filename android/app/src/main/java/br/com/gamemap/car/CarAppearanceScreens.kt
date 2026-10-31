package br.com.gamemap.car

import androidx.car.app.CarContext
import androidx.car.app.model.*
import br.com.gamemap.GameMapApplication
import br.com.gamemap.navigation.MapOrientation
import br.com.gamemap.preferences.MapBaseMode
import br.com.gamemap.data.OfflineMapCoverage
import br.com.gamemap.render.CarSurfaceRenderer

internal class CarAppearanceScreen(context: CarContext, app: GameMapApplication,
    private val renderer: CarSurfaceRenderer? = null,
) :
    PagedCarListScreen(context, app, "Aparência") {
    override fun rows(): List<Row> {
        val settings = app.navigationSettings.snapshot()
        val navigationActive = app.navigation.snapshot().navigationActive
        return listOf(
            Row.Builder().setTitle("Mapa 3D")
                .addText(if (navigationActive) "Durante a navegação" else "Ao explorar; prévia da rota em 2D")
                .setToggle(Toggle.Builder { enabled ->
                    app.preferences.setMap3D(app.navigation.snapshot().navigationActive, enabled)
                }.setChecked(app.preferences.snapshot().is3D(navigationActive)).build()).build(),
            Row.Builder().setTitle("Cobertura do mapa").addText(app.preferences.snapshot().baseMapMode.let {
                if (it == MapBaseMode.WORLD && !app.mapConnectivity.isOnline) "Mundo sem internet · escolha Goiânia offline"
                else it.label
            }).setBrowsable(true).setOnClickListener {
                screenManager.push(MapBaseModeScreen(carContext, app.preferences,
                    outsideOfflineRegion = { renderer?.cameraState?.target?.let {
                        !OfflineMapCoverage.contains(it.latitude, it.longitude)
                    } == true }, showOfflineRegion = { renderer?.reset() }))
            }.build(),
            Row.Builder().setTitle("Tema do mapa").addText(app.preferences.snapshot().theme.label)
                .setBrowsable(true).setOnClickListener {
                    screenManager.push(ThemeScreen(carContext, app.preferences))
                }.build(),
            Row.Builder().setTitle("Filtros de pontos de interesse").setBrowsable(true).setOnClickListener {
                screenManager.push(CategoryScreen(carContext, app.preferences, app.maps))
            }.build(),
            Row.Builder().setTitle("Marcador do veículo").addText("Escolha visual; rota de automóvel.")
                .setBrowsable(true).setOnClickListener {
                    screenManager.push(CarVehicleScreen(carContext, app))
                }.build(),
            Row.Builder().setTitle("Mostrar locais salvos")
                .addText("Independente dos filtros de pontos de interesse.")
                .setToggle(Toggle.Builder { app.navigationSettings.setSavedPlacesVisible(it) }
                    .setChecked(settings.savedPlacesVisible).build()).build(),
            Row.Builder().setTitle("Norte para cima").setToggle(Toggle.Builder { north ->
                app.navigationSettings.setOrientation(if (north) MapOrientation.NORTH_UP else MapOrientation.MOVEMENT_UP)
            }.setChecked(settings.orientation == MapOrientation.NORTH_UP).build()).build(),
        )
    }
}

internal class CarVehicleScreen(context: CarContext, app: GameMapApplication) :
    ObservedNavigationScreen(context, app) {
    private var page = 0
    override fun onGetTemplate(): Template {
        val icons = app.icons.vehicleIcons()
        val limit = hostListLimit(carContext)
        val pages = ((icons.size + limit - 1) / limit).coerceAtLeast(1)
        page = page.coerceIn(0, pages - 1)
        val choices = icons.drop(page * limit).take(limit)
        val current = app.navigationSettings.snapshot().vehicleIdFor(app.preferences.snapshot().theme)
        val selected = choices.indexOfFirst { it.id == current }
        val items = ItemList.Builder()
        // ItemList radio mode requires a selected row within this page. When the current
        // marker is on another page, plain rows avoid falsely selecting a different marker.
        if (selected >= 0) items.setSelectedIndex(selected).setOnSelectedListener { index ->
            app.navigationSettings.setVehicleId(choices[index].id)
        }
        choices.forEach { icon ->
            val row = Row.Builder().setTitle(icon.label)
                .setImage(catalogCarIcon(carContext, app,
                    app.icons.vehicleIcon(icon.id, app.preferences.snapshot().theme).preview))
            if (selected < 0) row.setOnClickListener { app.navigationSettings.setVehicleId(icon.id) }
            items.addItem(row.build())
        }
        return ListTemplate.Builder().setSingleList(items.build())
            .setHeader(pagedHeader("Marcador do veículo", page, pages) { page = it; invalidate() }).build()
    }
}
