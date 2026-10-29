package br.com.gamemap

import android.app.Application
import org.maplibre.android.MapLibre
import br.com.gamemap.preferences.PreferenceStore
import br.com.gamemap.data.MapStyleRepository
import br.com.gamemap.data.MapConnectivity
import br.com.gamemap.data.GameAssetCatalog
import br.com.gamemap.render.GameGlyphRouter
import br.com.gamemap.render.GameGlyphInterceptor
import org.maplibre.android.module.http.HttpRequestUtil
import okhttp3.OkHttpClient
import br.com.gamemap.navigation.NavigationRuntime
import br.com.gamemap.navigation.provider.GeoapifyProvider
import br.com.gamemap.navigation.storage.IconCatalogRepository
import br.com.gamemap.navigation.storage.LocalPlacesStore
import br.com.gamemap.navigation.storage.NavigationSettings
import br.com.gamemap.traffic.TrafficTileInterceptor
import br.com.gamemap.traffic.TrafficTileAuthenticationInterceptor

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
        navigationSettings = NavigationSettings(this)
        MapLibre.getInstance(this)
        val gameAssets = GameAssetCatalog.load(this)
        val presentGlyphs = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
        val glyphs = GameGlyphRouter(gameAssets.fontStacks) { path ->
            presentGlyphs.computeIfAbsent(path) { runCatching { assets.open(it).use { } }.isSuccess }
        }
        HttpRequestUtil.setOkHttpClient(OkHttpClient.Builder().addInterceptor(
            GameGlyphInterceptor(glyphs) { path -> assets.open(path).use { it.readBytes() } }
        ).addInterceptor(TrafficTileInterceptor(navigationSettings::trafficApiKey, navigationSettings::trafficEnabled))
            .addNetworkInterceptor(TrafficTileAuthenticationInterceptor(navigationSettings::trafficApiKey,
                navigationSettings::trafficEnabled)).build())
        places = LocalPlacesStore(this)
        icons = IconCatalogRepository(this)
        navigation = NavigationRuntime(this, GeoapifyProvider(navigationSettings::apiKey), navigationSettings, places)
    }
}
