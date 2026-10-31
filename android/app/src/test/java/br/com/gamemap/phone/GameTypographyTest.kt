package br.com.gamemap.phone

import android.app.Application
import android.graphics.Typeface
import android.widget.LinearLayout
import android.widget.TextView
import br.com.gamemap.data.GameAssetCatalog
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class GameTypographyTest {
    @Test fun fontChangesPreserveTextSizeAndRestoreOriginalFont() {
        val context = RuntimeEnvironment.getApplication()
        val root = LinearLayout(context)
        val label = TextView(context).apply { text = "São Paulo 123"; textSize = 19f; typeface = Typeface.MONOSPACE }
        root.addView(label)
        val original = label.typeface
        val pixels = label.textSize
        val custom = Typeface.create("serif", Typeface.BOLD)
        GameTypography.applyTypeface(root, custom)
        assertSame(custom, label.typeface)
        assertEquals(pixels, label.textSize, 0f)
        assertEquals("São Paulo 123", label.text.toString())
        GameTypography.applyTypeface(root, null)
        assertSame(original, label.typeface)
        assertEquals(pixels, label.textSize, 0f)
    }

    @Test fun catalogOnlyRegistersSuppliedThemesAndSafeFontAssets() {
        val catalog = GameAssetCatalog.fromJson("""{"schemaVersion":1,"themes":{"gta":{"ui":{"file":"game-assets/gta/fonts/pricedow.ttf","postScriptName":"Pricedown"},"map":{"road":"NavSkinGtaRoad","poi":"NavSkinGtaDisplay","place":"NavSkinGtaDisplay"}}}}""")
        assertEquals(setOf("NavSkinGtaRoad", "NavSkinGtaDisplay"), catalog.fontStacks)
        assertNull(catalog.themes["rosa"])
        assertEquals("Pricedown", catalog.themes["gta"]?.ui?.postScriptName)
        for (path in listOf("../../font.ttf", "game-assets/gta/fonts/../font.ttf", "file:///font.ttf")) {
            assertThrows(IllegalArgumentException::class.java) {
                GameAssetCatalog.fromJson("""{"schemaVersion":1,"themes":{"gta":{"ui":{"file":"$path"}}}}""")
            }
        }
    }

    @Test fun newThemeFontsRegisterDeclaredRolesWithoutInventingMissingAssets() {
        for ((id, stack) in listOf("smw" to "NavSkinSmw", "cyberpunk" to "NavSkinCyberpunk",
            "pipboy" to "NavSkinCyberpunk", "hollow" to "NavSkinRdrMap", "crossy" to "GameMapSans")) {
            val catalog = GameAssetCatalog.fromJson("""{"schemaVersion":1,"themes":{"$id":{"map":{"road":"$stack","poi":"$stack"}}}}""")
            assertEquals(setOf(stack), catalog.fontStacks)
            assertNull("No supplied UI font must retain the native font", catalog.themes[id]?.ui)
            assertFalse(catalog.themes.getValue(id).map.containsKey("place"))
        }
    }

    @Test fun retiredThemesDoNotRegisterFontProfiles() {
        val catalog = GameAssetCatalog.fromJson("""{"schemaVersion":1,"themes":{"minecraft":{"map":{"road":"NavSkinMinecraft"}},"gta5":{"map":{"road":"NavSkinGta5"}}}}""")
        assertTrue(catalog.themes.isEmpty())
        assertTrue(catalog.fontStacks.isEmpty())
    }
}
