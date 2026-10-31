package br.com.gamemap.car

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.AppManager
import androidx.car.app.navigation.NavigationManager
import androidx.car.app.validation.HostValidator
import androidx.car.app.CarToast
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import br.com.gamemap.GameMapApplication
import br.com.gamemap.BuildConfig
import br.com.gamemap.navigation.NavigationPhase
import br.com.gamemap.render.CarSurfaceRenderer

/** Projected navigation UI; the application runtime owns GPS and the shared trip. */
class GameMapCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator =
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(this)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample).build()
        }

    override fun onCreateSession(): Session = GameMapSession()
}

private class GameMapSession : Session(), DefaultLifecycleObserver {
    private var renderer: CarSurfaceRenderer? = null
    private var rootScreen: CarNavigationScreen? = null
    private var unsubscribes = emptyList<() -> Unit>()
    private var detachClient: (() -> Unit)? = null
    private var bridge: CarNavigationBridge? = null
    private var application: GameMapApplication? = null
    private var autoDriveEnabled = false
    private var previewRouteId: String? = null

    override fun onCreateScreen(intent: Intent): Screen {
        rootScreen?.let { handleIntent(intent); return it }
        val application = carContext.applicationContext as GameMapApplication
        this.application = application
        val preferences = application.preferences
        val maps = application.maps
        val screen = CarNavigationScreen(carContext, application, ::startNavigation)
        val surface = CarSurfaceRenderer(carContext,
            styleJson = maps.styleFor(preferences.snapshot(), carContext.isDarkMode), onStatus = screen::showStatus)
        renderer = surface
        screen.renderer = surface
        rootScreen = screen
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(surface)
        detachClient = application.navigation.attachClient("android-auto-main")
        bridge = CarNavigationBridge(carContext.getCarService(NavigationManager::class.java),
            onStop = { application.navigation.stopNavigation() },
            onAutoDrive = { if (BuildConfig.DEBUG) {
                autoDriveEnabled = true
                CarToast.makeText(carContext, "SIMULAÇÃO GPS: inicie a prévia para reproduzir a rota.", CarToast.LENGTH_LONG).show()
            } })
        unsubscribes = listOf(preferences.addListener { state ->
            surface.applyMapPreferences(state)
            surface.applyStyle(maps.styleFor(state, carContext.isDarkMode))
            refreshNavigation()
            screen.invalidate()
        }, application.navigation.addListener { state ->
            bridge?.update(state)
            refreshNavigation()
            screen.invalidate()
        }, application.mapConnectivity.addListener { screen.invalidate() },
            application.navigationSettings.addListener { refreshNavigation(); screen.invalidate() },
            application.places.addListener { refreshNavigation(); screen.invalidate() })
        lifecycle.addObserver(this)
        // The framework installs the returned root after onCreateScreen completes.
        Handler(Looper.getMainLooper()).post { if (this.application != null) handleIntent(intent) }
        return screen
    }

    override fun onNewIntent(intent: Intent) { handleIntent(intent) }

    private fun handleIntent(intent: Intent) {
        val app = application ?: return
        when (val destination = CarNavigationIntents.parse(intent)) {
            is CarDestinationIntent.Place -> {
                carContext.getCarService(androidx.car.app.ScreenManager::class.java).popToRoot()
                app.navigation.selectDestination(destination.destination)
            }
            is CarDestinationIntent.Search -> carContext.getCarService(androidx.car.app.ScreenManager::class.java)
                .push(CarDestinationSearchScreen(carContext, app, destination.query))
            null -> Unit
        }
    }

    private fun startNavigation() {
        val app = application ?: return
        if (autoDriveEnabled && BuildConfig.DEBUG) {
            val state = app.navigation.snapshot()
            val route = state.route?.takeIf { state.phase == NavigationPhase.PREVIEW } ?: return
            val fixes = CarNavigationReplay.fixes(route)
            if (fixes.isNotEmpty()) app.navigation.startReplay(route, fixes)
            else CarToast.makeText(carContext, "Rota longa demais para reprodução de desenvolvimento.", CarToast.LENGTH_LONG).show()
        } else app.navigation.startNavigation()
    }

    private fun refreshNavigation() {
        val app = application ?: return
        renderer?.updateNavigation(app.navigation.snapshot(), app.navigationSettings.snapshot(),
            app.places.snapshot().savedPlaces, app.preferences.snapshot().theme)
        val state = app.navigation.snapshot()
        if (state.phase == NavigationPhase.PREVIEW && state.route?.id != previewRouteId) {
            previewRouteId = state.route?.id
            renderer?.showRouteOverview()
        }
    }
    override fun onStart(owner: LifecycleOwner) { renderer?.start(); refreshNavigation() }
    override fun onStop(owner: LifecycleOwner) { renderer?.stop() }
    override fun onCarConfigurationChanged(newConfiguration: Configuration) {
        super.onCarConfigurationChanged(newConfiguration)
        val surface = renderer ?: return
        val application = carContext.applicationContext as GameMapApplication
        surface.applyStyle(application.maps.styleFor(application.preferences.snapshot(), carContext.isDarkMode))
        refreshNavigation()
        rootScreen?.invalidate()
    }
    override fun onDestroy(owner: LifecycleOwner) {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(null)
        unsubscribes.forEach { it() }
        unsubscribes = emptyList()
        bridge?.close()
        bridge = null
        if (autoDriveEnabled && application?.navigation?.snapshot()?.simulation == true)
            application?.navigation?.stopReplay()
        detachClient?.invoke()
        detachClient = null
        renderer?.close()
        renderer = null
        rootScreen?.renderer = null
        rootScreen = null
        application = null
        lifecycle.removeObserver(this)
    }
}
