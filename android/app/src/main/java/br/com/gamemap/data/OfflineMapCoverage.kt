package br.com.gamemap.data

/** Bounds of the bundled Goiânia data, independent of the worldwide map. */
object OfflineMapCoverage {
    fun contains(latitude: Double, longitude: Double): Boolean =
        latitude in -16.755..-16.640 && longitude in -49.325..-49.205
}
