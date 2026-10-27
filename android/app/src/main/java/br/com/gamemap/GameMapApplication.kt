package br.com.gamemap

import android.app.Application
import org.maplibre.android.MapLibre
import br.com.gamemap.preferences.PreferenceStore
import br.com.gamemap.data.MapStyleRepository
import br.com.gamemap.data.MapConnectivity
import br.com.gamemap.navigation.NavigationRuntime
import br.com.gamemap.navigation.provider.GeoapifyProvider
import br.com.gamemap.navigation.storage.IconCatalogRepository
import br.com.gamemap.navigation.storage.LocalPlacesStore
import br.com.gamemap.navigation.storage.NavigationSettings

class GameMapApplication : Application() {
    lateinit var preferences: PreferenceStore
        private set
    lateinit var maps: MapStyleRepository
        private set
    lateinit var mapConnectivity: MapConnectivity
        private set
    lateinit var navigationSettings: NavigationSettings
        private set
    lateinit var places: LocalPlacesStore
        private set
    lateinit var icons: IconCatalogRepository
        private set
    lateinit var navigation: NavigationRuntime
        private set
    override fun onCreate() {
        super.onCreate()
        preferences = PreferenceStore(this)
        maps = MapStyleRepository(this)
        mapConnectivity = MapConnectivity(this)
        MapLibre.getInstance(this)
        navigationSettings = NavigationSettings(this)
        places = LocalPlacesStore(this)
        icons = IconCatalogRepository(this)
        navigation = NavigationRuntime(this, GeoapifyProvider(navigationSettings::apiKey), navigationSettings, places)
    }
}
