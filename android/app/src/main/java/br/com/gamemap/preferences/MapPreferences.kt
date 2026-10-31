package br.com.gamemap.preferences

enum class MapTheme(val assetName: String, val label: String) {
    GTA_SA("gta", "GTA SA"), ROSA_POP("rosa", "Rosa Pop"), RDR2("rdr2", "RDR2"),
    SUPER_MARIO_WORLD("smw", "Super Mario World"), CYBERPUNK_2077("cyberpunk", "Cyberpunk 2077"),
    PIP_BOY("pipboy", "Pip-Boy"), HOLLOW_KNIGHT("hollow", "Hollow Knight"), CROSSY_ROAD("crossy", "Crossy Road");
    companion object { fun fromId(id: String?) = entries.firstOrNull { it.assetName == id } ?: GTA_SA }
}

enum class MapBaseMode(val id: String, val label: String) {
    WORLD("world", "Mundo · online"), GOIANIA_OFFLINE("goianiaOffline", "Goiânia · offline");
    companion object { fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: WORLD }
}

enum class PoiCategory(val id: String, val label: String) {
    PARKS("parks", "Parques"),
    FUEL("fuel", "Postos de combustível"),
    FOOD("food", "Restaurantes, cafés e bares"),
    PARKING("parking", "Estacionamentos"),
    HEALTH("health", "Hospitais e saúde"),
    SHOPS("shops", "Mercados e conveniência"),
    EDUCATION("education", "Escolas e universidades"),
    CULTURE("culture", "Museus, teatros e cultura"),
    RELIGION("religion", "Locais de culto");
    companion object { fun fromId(id: String) = entries.firstOrNull { it.id == id } }
}

data class MapPreferences(
    val theme: MapTheme = MapTheme.GTA_SA,
    val enabledCategories: Set<PoiCategory> = PoiCategory.entries.toSet(),
    val baseMapMode: MapBaseMode = MapBaseMode.WORLD,
    val browse3D: Boolean = false,
    val navigation3D: Boolean = true,
) {
    fun is3D(navigationActive: Boolean): Boolean = if (navigationActive) navigation3D else browse3D
}
