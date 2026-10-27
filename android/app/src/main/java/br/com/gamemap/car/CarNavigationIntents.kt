package br.com.gamemap.car

import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import androidx.car.app.CarContext
import br.com.gamemap.navigation.core.GeoMath
import br.com.gamemap.navigation.model.Destination
import br.com.gamemap.navigation.model.GeoPoint

internal sealed interface CarDestinationIntent {
    data class Place(val destination: Destination) : CarDestinationIntent
    data class Search(val query: String) : CarDestinationIntent
}

/** An incoming navigation request selects/previews; it never silently starts guidance. */
internal object CarNavigationIntents {
    fun parse(intent: Intent): CarDestinationIntent? {
        if (intent.action == Intent.ACTION_SEARCH) return intent.getStringExtra(SearchManager.QUERY)
            ?.trim()?.takeIf { it.isNotEmpty() }?.let { CarDestinationIntent.Search(it.take(300)) }
        if (intent.action !in setOf(Intent.ACTION_VIEW, CarContext.ACTION_NAVIGATE)) return null
        val uri = intent.data ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme !in setOf("geo", "google.navigation")) return null
        val specific = uri.encodedSchemeSpecificPart.orEmpty()
        val parameters = if (scheme == "google.navigation") specific else specific.substringAfter('?', "")
        val query = parameters.split('&').firstOrNull { it.substringBefore('=') == "q" }
            ?.substringAfter('=', "")?.let(Uri::decode)?.trim()
        if (!query.isNullOrBlank()) return coordinate(query) ?: CarDestinationIntent.Search(query.take(300))
        return if (scheme == "geo") coordinate(Uri.decode(specific.substringBefore('?'))) else null
    }

    private fun coordinate(value: String): CarDestinationIntent.Place? {
        val match = Regex("^\\s*([-+]?\\d{1,3}(?:\\.\\d+)?)\\s*,\\s*([-+]?\\d{1,3}(?:\\.\\d+)?)\\s*(?:\\((.{1,200})\\))?\\s*$")
            .matchEntire(value) ?: return null
        val latitude = match.groupValues[1].toDoubleOrNull() ?: return null
        val longitude = match.groupValues[2].toDoubleOrNull() ?: return null
        val point = GeoPoint(latitude, longitude)
        if (!GeoMath.isValid(point)) return null
        val label = match.groupValues[3].ifBlank { "Destino no mapa" }
        return CarDestinationIntent.Place(Destination("geo:$latitude,$longitude", label, point,
            "$latitude, $longitude"))
    }
}
