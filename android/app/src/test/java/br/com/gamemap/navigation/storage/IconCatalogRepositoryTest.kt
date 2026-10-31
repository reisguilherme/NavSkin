package br.com.gamemap.navigation.storage

import android.app.Application
import android.graphics.BitmapFactory
import br.com.gamemap.preferences.MapTheme
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class IconCatalogRepositoryTest {
    @Test fun everyChoiceUsesItsRealThemeAssetAndUnknownChoicesResolveWithoutMutatingTheId() {
        val context = RuntimeEnvironment.getApplication()
        val catalog = IconCatalogRepository(context)
        assertEquals(12, catalog.savedIcons().size)
        assertEquals(6, catalog.vehicleIcons().size)
        val activeThemes = MapTheme.entries.map { it.assetName }.toSet()
        for (item in catalog.savedIcons() + catalog.vehicleIcons()) {
            assertEquals("Only active themes should ship representations for ${item.id}", activeThemes, item.themeAssets.keys)
        }
        for (theme in MapTheme.entries) {
            for (item in catalog.savedIcons()) {
                val asset = catalog.savedIcon(item.id, theme)
                assertTrue(asset.image.startsWith("navigation-icons/${theme.assetName}/saved/${item.id}"))
                context.assets.open(asset.preview).use { assertNotNull(BitmapFactory.decodeStream(it)) }
            }
            for (item in catalog.vehicleIcons()) {
                context.assets.open(catalog.vehicleIcon(item.id, theme).image).use { assertNotNull(BitmapFactory.decodeStream(it)) }
            }
            val preference = "unknown-id-preserved-in-store"
            assertEquals(catalog.savedIcon("special", theme), catalog.savedIcon(preference, theme))
            assertEquals(catalog.vehicleIcon("arrow", theme), catalog.vehicleIcon(preference, theme))
        }
    }
}
