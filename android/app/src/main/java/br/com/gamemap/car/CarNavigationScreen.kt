package br.com.gamemap.car

import android.Manifest
import android.app.ActivityOptions
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.Display
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.car.app.navigation.model.*
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import br.com.gamemap.GameMapApplication
import br.com.gamemap.R
import br.com.gamemap.navigation.*
import br.com.gamemap.phone.MainActivity
import br.com.gamemap.render.CarSurfaceRenderer
import br.com.gamemap.preferences.MapBaseMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

/** Root map, route preview and active navigation share one Screen and one runtime. */
internal class CarNavigationScreen(
    context: CarContext,
    private val app: GameMapApplication,
    private val startTrip: () -> Unit,
) : Screen(context) {
    var renderer: CarSurfaceRenderer? = null
    private var mapError: String? = null

    fun showStatus(message: String) {
        if (message.contains("falha", ignoreCase = true)) mapError = "Não foi possível carregar o mapa. Escolha a cobertura em Aparência."
        else if (message.contains("estilo carregado")) mapError = null
        invalidate()
    }

    override fun onGetTemplate(): Template {
        val state = app.navigation.snapshot()
        return if (state.navigationActive) active(state) else previewOrBrowse(state)
    }

    private fun active(state: NavigationSnapshot): Template {
        val actions = ActionStrip.Builder()
            .addAction(action("Encerrar", R.drawable.phone_close) { app.navigation.stopNavigation() })
            .addAction(action("Destinos", R.drawable.phone_map) { showDestinations() })
            .addAction(action(appearanceTitle(), R.drawable.phone_layers) { showAppearance() })
        if (state.phase == NavigationPhase.REROUTE_FAILED)
            actions.addAction(action("Tentar rota", R.drawable.phone_reset) { app.navigation.retryRoute() })
        val builder = NavigationTemplate.Builder()
            .setNavigationInfo(CarNavigationModels.navigationInfo(state))
            .setActionStrip(actions.build())
            .setMapActionStrip(mapActions())
            .setPanModeListener { invalidate() }
        CarNavigationModels.destinationEstimate(state)?.let(builder::setDestinationTravelEstimate)
        return builder.build()
    }

    private fun previewOrBrowse(state: NavigationSnapshot): Template {
        val pane = Pane.Builder()
        val destination = state.destination
        if (destination != null) {
            pane.addRow(Row.Builder().setTitle(destination.name)
                .addText(destination.address.ifBlank { "${destination.point.latitude}, ${destination.point.longitude}" }).build())
            val route = state.route
            if (state.phase == NavigationPhase.PREVIEW && route != null) {
                val minutes = ceil(route.durationSeconds / 60).toLong()
                val arrival = SimpleDateFormat("HH:mm", Locale.getDefault())
                    .format(Date(System.currentTimeMillis() + (route.durationSeconds * 1_000).toLong()))
                pane.addRow(Row.Builder().setTitle("${formatDistance(route.distanceMeters)} · $minutes min · chegada $arrival")
                    .addText(mapNotice() ?: state.message ?: "Estimativa sem trânsito em tempo real.").build())
                pane.addAction(action("Iniciar", R.drawable.phone_arrow) { startTrip() })
            } else {
                pane.addRow(Row.Builder().setTitle(CarNavigationModels.statusTitle(state))
                    .addText(CarNavigationModels.statusText(state)).build())
                if (state.phase == NavigationPhase.ERROR)
                    pane.addAction(action("Tentar rota", R.drawable.phone_reset) { app.navigation.retryRoute() })
            }
            pane.addAction(action("Cancelar", R.drawable.phone_close) { app.navigation.stopNavigation() })
        } else {
            val title = if (state.phase == NavigationPhase.ARRIVED) "Você chegou ao destino" else "Escolha seu destino"
            pane.addRow(Row.Builder().setTitle(title)
                .addText(mapNotice() ?: state.message ?: "Salvos, recentes e pesquisa de endereços e lugares.").build())
            pane.addAction(action("Destinos", R.drawable.phone_map) { showDestinations() })
            pane.addAction(action("GPS", R.drawable.phone_reset, parked = true) { requestCarLocation(carContext, app) })
        }
        val content = PaneTemplate.Builder(pane.build()).setHeader(Header.Builder()
            .setTitle(if (state.simulation) "SIMULAÇÃO GPS" else "NavSkin · ${app.preferences.snapshot().theme.label}")
            .setStartHeaderAction(Action.APP_ICON).build()).build()
        return MapWithContentTemplate.Builder().setContentTemplate(content)
            .setActionStrip(ActionStrip.Builder()
                .addAction(action("Destinos", R.drawable.phone_map) { showDestinations() })
                .addAction(action(appearanceTitle(), R.drawable.phone_layers) { showAppearance() }).build())
            .setMapController(MapController.Builder().setMapActionStrip(mapActions())
                .setPanModeListener { invalidate() }.build()).build()
    }

    private fun mapActions(): ActionStrip = ActionStrip.Builder()
        .addAction(Action.PAN)
        .addAction(mapAction(R.drawable.ic_reset) { renderer?.recenter() })
        .addAction(mapAction(R.drawable.phone_map) { renderer?.showRouteOverview() })
        .addAction(mapAction(R.drawable.phone_arrow) {
            val current = app.navigationSettings.snapshot().orientation
            app.navigationSettings.setOrientation(if (current == MapOrientation.NORTH_UP)
                MapOrientation.MOVEMENT_UP else MapOrientation.NORTH_UP)
        }).build()

    private fun mapAction(icon: Int, click: () -> Unit) = Action.Builder()
        .setIcon(carIcon(carContext, icon)).setOnClickListener { click() }.build()

    private fun showDestinations() = screenManager.push(CarDestinationsScreen(carContext, app))
    private fun showAppearance() = screenManager.push(CarAppearanceScreen(carContext, app, renderer))

    private fun mapNotice(): String? = when {
        app.preferences.snapshot().baseMapMode == MapBaseMode.WORLD && !app.mapConnectivity.isOnline ->
            "Mapa mundial sem internet. Em Aparência, escolha Goiânia offline."
        mapError != null -> mapError
        app.preferences.snapshot().baseMapMode == MapBaseMode.GOIANIA_OFFLINE ->
            "Mapa offline: somente o recorte incluído de Goiânia."
        else -> null
    }

    private fun appearanceTitle() = if (app.preferences.snapshot().baseMapMode == MapBaseMode.WORLD &&
        !app.mapConnectivity.isOnline) "Mapa sem rede" else "Aparência"

    private fun action(title: String, icon: Int, parked: Boolean = false, click: () -> Unit): Action {
        val listener = OnClickListener { click() }
        return Action.Builder().setTitle(title).setIcon(carIcon(carContext, icon))
            .setOnClickListener(if (parked) ParkedOnlyOnClickListener.create(listener) else listener).build()
    }
}

internal fun carIcon(context: CarContext, resource: Int) =
    CarIcon.Builder(IconCompat.createWithResource(context, resource)).build()

internal fun formatDistance(meters: Double): String = if (meters < 1_000)
    "${meters.toInt()} m" else String.format(Locale.getDefault(), "%.1f km", meters / 1_000)

/** Permission dialogs and phone handoff are restricted to parked interaction. */
internal fun requestCarLocation(context: CarContext, app: GameMapApplication) {
    fun activate() {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        app.navigation.setLocationPermission(fine, coarse)
        if (fine && !app.navigation.activateLocation()) openNavigationPhone(context)
    }
    val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.POST_NOTIFICATIONS
    val needed = permissions.any { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
    if (needed) runCatching { context.requestPermissions(permissions) { _, _ -> activate() } }
        .onFailure { CarToast.makeText(context, "Abra o NavSkin no celular para permitir a localização.", CarToast.LENGTH_LONG).show() }
    else activate()
}

internal fun openNavigationPhone(context: CarContext) {
    val options = ActivityOptions.makeBasic().apply { launchDisplayId = Display.DEFAULT_DISPLAY }
    runCatching { ContextCompat.startActivity(context, Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options.toBundle()) }
        .onFailure { CarToast.makeText(context, "Abra o NavSkin no celular quando estiver estacionado.", CarToast.LENGTH_LONG).show() }
}
