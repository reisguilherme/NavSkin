package br.com.gamemap.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import br.com.gamemap.navigation.NavigationPreferences
import br.com.gamemap.navigation.NavigationSnapshot
import br.com.gamemap.navigation.model.GeoPoint
import br.com.gamemap.navigation.model.LocationQuality
import br.com.gamemap.navigation.storage.IconCatalogRepository
import br.com.gamemap.navigation.storage.SavedPlace
import br.com.gamemap.preferences.MapTheme
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Small mutable sources above the base map; a GPS fix never replaces the style. */
internal class NavigationMapOverlay(context: Context) {
    private val assets = context.applicationContext.assets
    private val catalog by lazy { IconCatalogRepository(context.applicationContext) }
    private val bitmaps = LruCache<String, Bitmap>(24)
    private var style: Style? = null
    private var snapshot: NavigationSnapshot? = null
    private var preferences = NavigationPreferences()
    private var savedPlaces = emptyList<SavedPlace>()
    private var theme = MapTheme.GTA_SA
    private var routeGeometry: List<GeoPoint>? = null
    private var observedRouteGeometry: List<GeoPoint>? = null
    private var remainingGeometry: List<GeoPoint>? = null
    private var renderedSaved: List<SavedPlace>? = null
    private var renderedSavedVisible: Boolean? = null
    private var renderedTheme: MapTheme? = null
    private var vehicleImage: String? = null
    private var destinationPoint: GeoPoint? = null

    fun styleLoading() { style = null }

    fun styleLoaded(loaded: Style) {
        style = loaded
        routeGeometry = null
        observedRouteGeometry = null
        remainingGeometry = null
        renderedSaved = null
        renderedSavedVisible = null
        renderedTheme = null
        vehicleImage = null
        destinationPoint = null
        if (snapshot != null) installAndRender()
    }

    fun update(state: NavigationSnapshot, prefs: NavigationPreferences, saved: List<SavedPlace>, mapTheme: MapTheme) {
        snapshot = state
        preferences = prefs
        savedPlaces = saved
        theme = mapTheme
        if (style != null) installAndRender()
    }

    private fun installAndRender() {
        val loaded = style ?: return
        val state = snapshot ?: return
        if (loaded.getSource(ROUTE_SOURCE) == null) install(loaded)
        if (renderedTheme != theme) {
            val palette = palette(theme)
            loaded.getLayer(ROUTE_CASING)?.setProperties(lineColor(palette.casing))
            loaded.getLayer(REMAINING_CASING)?.setProperties(lineColor(palette.casing))
            loaded.getLayer(ROUTE_LAYER)?.setProperties(lineColor(palette.route))
            loaded.getLayer(REMAINING_LAYER)?.setProperties(lineColor(palette.route))
            loaded.getLayer(DESTINATION_LAYER)?.setProperties(circleColor(palette.route), circleStrokeColor(palette.casing))
            loaded.getLayer(ACCURACY_LAYER)?.setProperties(fillColor(palette.route))
        }

        val requestedGeometry = state.route?.geometry ?: emptyList()
        val geometry = if (requestedGeometry === observedRouteGeometry) routeGeometry ?: emptyList() else
            requestedGeometry.takeIf { points -> points.all { it.isValidMapPoint() } }
                ?: requestedGeometry.filter { it.isValidMapPoint() }
        if (routeGeometry != geometry) {
            source(ROUTE_SOURCE)?.setGeoJson(line(geometry))
            routeGeometry = geometry
        }
        observedRouteGeometry = requestedGeometry
        val progress = state.progress?.takeIf {
            state.hasCurrentFix() && state.location.quality == LocationQuality.GOOD && it.onRoute &&
                it.routeId == state.route?.id && it.segmentIndex in 0 until geometry.lastIndex &&
                it.projectedPoint.isValidMapPoint()
        }
        val remaining = if (progress == null) geometry else
            listOf(progress.projectedPoint) + geometry.drop(progress.segmentIndex + 1)
        if (remainingGeometry != remaining) {
            source(REMAINING_SOURCE)?.setGeoJson(line(remaining))
            remainingGeometry = remaining
        }

        val destination = (state.destination ?: state.route?.destination)?.point?.takeIf { it.isValidMapPoint() }
        if (destination != destinationPoint) {
            source(DESTINATION_SOURCE)?.setGeoJson(point(destination))
            destinationPoint = destination
        }

        if (renderedSaved != savedPlaces || renderedSavedVisible != preferences.savedPlacesVisible || renderedTheme != theme) {
            val features = if (!preferences.savedPlacesVisible) emptyList() else savedPlaces.mapNotNull { saved ->
                if (!saved.point.isValidMapPoint()) return@mapNotNull null
                val path = catalog.savedIcon(saved.iconId, theme).image
                ensureImage(loaded, path)
                Feature.fromGeometry(saved.point.toPoint()).apply {
                    addStringProperty("savedId", saved.id)
                    addStringProperty("image", path)
                    addStringProperty("name", saved.name)
                }
            }
            source(SAVED_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(features))
            renderedSaved = savedPlaces.toList()
            renderedSavedVisible = preferences.savedPlacesVisible
        }

        val image = catalog.vehicleIcon(preferences.vehicleId, theme).image
        if (vehicleImage != image) {
            ensureImage(loaded, image)
            loaded.getLayer(VEHICLE_LAYER)?.setProperties(iconImage(image))
            vehicleImage = image
        }
        val vehicle = state.renderPosition()
        source(VEHICLE_SOURCE)?.setGeoJson(point(vehicle))
        loaded.getLayer(VEHICLE_LAYER)?.setProperties(
            iconRotate((state.location.bearingDegrees?.takeIf { it.isFinite() } ?: 0.0).toFloat()),
            iconOpacity(state.vehicleOpacity()),
        )
        // The uncertainty ring is centered on the measured fix, never the snapped route point.
        val fix = state.location.fix
        val accuracy = fix?.accuracyMeters?.takeIf { it.isFinite() && it > 0.0 && it <= 2_000.0 }
        source(ACCURACY_SOURCE)?.setGeoJson(if (state.hasCurrentFix() && fix != null && accuracy != null)
            accuracyCircle(fix.point, accuracy) else EMPTY)
        renderedTheme = theme
    }

    private fun install(loaded: Style) {
        for (id in listOf(ACCURACY_SOURCE, ROUTE_SOURCE, REMAINING_SOURCE, SAVED_SOURCE, DESTINATION_SOURCE, VEHICLE_SOURCE)) {
            loaded.addSource(GeoJsonSource(id, EMPTY))
        }
        loaded.addLayer(FillLayer(ACCURACY_LAYER, ACCURACY_SOURCE).withProperties(fillOpacity(0.12f)))
        loaded.addLayer(LineLayer(ROUTE_CASING, ROUTE_SOURCE).withProperties(
            lineCap("round"), lineJoin("round"), lineWidth(10f), lineOpacity(0.5f),
        ))
        loaded.addLayer(LineLayer(ROUTE_LAYER, ROUTE_SOURCE).withProperties(
            lineCap("round"), lineJoin("round"), lineWidth(6f), lineOpacity(0.4f),
        ))
        loaded.addLayer(LineLayer(REMAINING_CASING, REMAINING_SOURCE).withProperties(
            lineCap("round"), lineJoin("round"), lineWidth(10f), lineOpacity(0.95f),
        ))
        loaded.addLayer(LineLayer(REMAINING_LAYER, REMAINING_SOURCE).withProperties(
            lineCap("round"), lineJoin("round"), lineWidth(6f), lineOpacity(1f),
        ))
        loaded.addLayer(SymbolLayer(SAVED_LAYER, SAVED_SOURCE).withProperties(
            iconImage(get("image")), iconAnchor("center"), iconAllowOverlap(true), iconIgnorePlacement(true),
            iconSize(0.8f), iconRotationAlignment("viewport"),
        ))
        loaded.addLayer(CircleLayer(DESTINATION_LAYER, DESTINATION_SOURCE).withProperties(
            circleRadius(10f), circleStrokeWidth(3f), circleOpacity(1f),
        ))
        loaded.addLayer(CircleLayer("gamemap-nav-destination-center", DESTINATION_SOURCE).withProperties(
            circleRadius(3f), circleColor("#FFFFFF"),
        ))
        loaded.addLayer(SymbolLayer(VEHICLE_LAYER, VEHICLE_SOURCE).withProperties(
            iconAnchor("center"), iconSize(1f), iconAllowOverlap(true), iconIgnorePlacement(true),
            // Artwork points north. Map alignment subtracts camera bearing automatically.
            iconRotationAlignment("map"), iconPitchAlignment("map"),
        ))
    }

    private fun ensureImage(loaded: Style, path: String) {
        if (loaded.getImage(path) != null) return
        val bitmap = bitmaps.get(path) ?: assets.open(path).use {
            checkNotNull(BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inScaled = false })).apply {
                // Catalog PNGs are authored at 48 logical pixels. Asset decoding otherwise
                // inherits the phone DPI, which makes MapLibre shrink them on dense screens.
                density = android.util.DisplayMetrics.DENSITY_DEFAULT
            }
        }.also { bitmaps.put(path, it) }
        loaded.addImage(path, bitmap)
    }

    private fun source(id: String): GeoJsonSource? = style?.getSourceAs(id)

    private data class Palette(val route: String, val casing: String)
    private fun palette(theme: MapTheme) = when (theme) {
        MapTheme.GTA_SA -> Palette("#47D8FF", "#071111")
        MapTheme.ROSA_POP -> Palette("#3557E8", "#FFFFFF")
        MapTheme.RDR2 -> Palette("#E7BF55", "#18100A")
    }

    private fun GeoPoint.toPoint(): Point = Point.fromLngLat(longitude, latitude)
    private fun point(value: GeoPoint?): FeatureCollection = if (value == null) EMPTY else
        FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(value.toPoint())))
    private fun line(points: List<GeoPoint>): FeatureCollection = if (points.size < 2) EMPTY else
        FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(LineString.fromLngLats(points.map { it.toPoint() }))))

    private fun accuracyCircle(center: GeoPoint, radiusMeters: Double): FeatureCollection {
        val latitude = Math.toRadians(center.latitude)
        val longitude = Math.toRadians(center.longitude)
        val distance = radiusMeters / 6_371_008.8
        val ring = (0..40).map { index ->
            val bearing = index * Math.PI * 2.0 / 40
            val lat = asin(sin(latitude) * cos(distance) + cos(latitude) * sin(distance) * cos(bearing))
            val lon = longitude + atan2(sin(bearing) * sin(distance) * cos(latitude), cos(distance) - sin(latitude) * sin(lat))
            Point.fromLngLat(Math.toDegrees(lon), Math.toDegrees(lat))
        }
        return FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(Polygon.fromLngLats(listOf(ring)))))
    }

    companion object {
        internal const val ROUTE_SOURCE = "gamemap-nav-route"
        internal const val REMAINING_SOURCE = "gamemap-nav-remaining"
        internal const val SAVED_SOURCE = "gamemap-nav-saved"
        internal const val DESTINATION_SOURCE = "gamemap-nav-destination"
        internal const val VEHICLE_SOURCE = "gamemap-nav-vehicle"
        internal const val ACCURACY_SOURCE = "gamemap-nav-accuracy"
        internal const val ROUTE_LAYER = "gamemap-nav-route-line"
        internal const val REMAINING_LAYER = "gamemap-nav-remaining-line"
        internal const val ROUTE_CASING = "gamemap-nav-route-casing"
        internal const val REMAINING_CASING = "gamemap-nav-remaining-casing"
        internal const val SAVED_LAYER = "gamemap-nav-saved-symbol"
        internal const val DESTINATION_LAYER = "gamemap-nav-destination-circle"
        internal const val VEHICLE_LAYER = "gamemap-nav-vehicle-symbol"
        internal const val ACCURACY_LAYER = "gamemap-nav-accuracy-fill"
        private val EMPTY = FeatureCollection.fromFeatures(emptyList<Feature>())
    }
}
