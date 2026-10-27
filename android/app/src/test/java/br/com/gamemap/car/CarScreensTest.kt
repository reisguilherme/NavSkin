package br.com.gamemap.car

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.car.app.OnDoneCallback
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.car.app.testing.TestCarContext
import androidx.car.app.testing.ScreenController
import androidx.lifecycle.Lifecycle
import br.com.gamemap.data.MapStyleRepository
import br.com.gamemap.preferences.MapTheme
import br.com.gamemap.preferences.MapBaseMode
import br.com.gamemap.preferences.PoiCategory
import br.com.gamemap.preferences.PreferenceStore
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class CarScreensTest {
    private lateinit var context: Context
    private lateinit var carContext: TestCarContext
    private lateinit var store: PreferenceStore
    private lateinit var maps: MapStyleRepository
    private val completed = object : OnDoneCallback {}

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(PreferenceStore.FILE_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        carContext = TestCarContext.createCarContext(context)
        store = PreferenceStore(context)
        store.setBaseMapMode(MapBaseMode.GOIANIA_OFFLINE)
        maps = MapStyleRepository(context)
    }

    @After fun tearDown() { store.close() }

    @Test fun coverageUsesHostRadioChoicesAndPersistsAcrossPhoneAndCar() {
        val screen = MapBaseModeScreen(carContext, store)
        val template = screen.onGetTemplate() as ListTemplate
        assertEquals(listOf("Mundo · online", "Goiânia · offline"),
            template.singleList!!.items.map { (it as Row).title.toString() })
        assertEquals(1, template.singleList!!.selectedIndex)
        template.singleList!!.onSelectedDelegate!!.sendSelected(0, completed)
        shadowOf(Looper.getMainLooper()).idle()
        PreferenceStore(context).use { assertEquals(MapBaseMode.WORLD, it.snapshot().baseMapMode) }
        assertEquals(0, (screen.onGetTemplate() as ListTemplate).singleList!!.selectedIndex)
    }

    @Test fun hostRadioSelectionPersistsTheThemeSharedWithPhone() {
        val screen = ThemeScreen(carContext, store)
        val before = screen.onGetTemplate() as ListTemplate
        assertEquals(listOf("GTA SA", "Rosa Pop", "RDR2"),
            before.singleList!!.items.map { (it as Row).title.toString() })
        assertEquals(0, before.singleList!!.selectedIndex)
        listOf(MapTheme.GTA_SA, MapTheme.ROSA_POP, MapTheme.RDR2).forEachIndexed { index, theme ->
            before.singleList!!.onSelectedDelegate!!.sendSelected(index, completed)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(theme, store.snapshot().theme)
            PreferenceStore(context).use { assertEquals(theme, it.snapshot().theme) }
            assertEquals(index, (screen.onGetTemplate() as ListTemplate).singleList!!.selectedIndex)
        }
    }

    @Test fun allNineHostSwitchesRemainIndependentAcrossHostLimitedPages() {
        val screen = CategoryScreen(carContext, store, maps)
        val visited = linkedSetOf<PoiCategory>()
        while (true) {
            val template = screen.onGetTemplate() as ListTemplate
            val rows = template.singleList!!.items.map { it as Row }
            assertTrue(rows.all { it.toggle!!.isChecked })
            rows.forEach { row ->
                val category = PoiCategory.entries.single { it.label == row.title.toString() }
                assertTrue("Category repeated across pages", visited.add(category))
                row.toggle!!.onCheckedChangeDelegate.sendCheckedChange(false, completed)
                shadowOf(Looper.getMainLooper()).idle()
                assertEquals(PoiCategory.entries.toSet() - category, store.snapshot().enabledCategories)
                val changed = (screen.onGetTemplate() as ListTemplate).singleList!!.items.map { it as Row }
                assertFalse(changed.single { it.title.toString() == category.label }.toggle!!.isChecked)
                changed.single { it.title.toString() == category.label }.toggle!!.onCheckedChangeDelegate.sendCheckedChange(true, completed)
                shadowOf(Looper.getMainLooper()).idle()
                assertEquals(PoiCategory.entries.toSet(), store.snapshot().enabledCategories)
            }
            val next = template.header!!.endHeaderActions.firstOrNull { it.title?.toString() == "Próximas" } ?: break
            next.onClickDelegate!!.sendClick(completed)
            shadowOf(Looper.getMainLooper()).idle()
        }
        assertEquals(PoiCategory.entries.toSet(), visited)
    }

    @Test fun visibleHostThemeScreenRefreshesAfterPhoneChangesTheTheme() {
        val controller = ScreenController(ThemeScreen(carContext, store))
            .moveToState(Lifecycle.State.RESUMED)
        try {
            controller.reset()
            PreferenceStore(context).use { it.setTheme(MapTheme.RDR2) }
            shadowOf(Looper.getMainLooper()).idle()
            val returned = controller.templatesReturned.filterIsInstance<ListTemplate>()
            assertTrue("The active host screen did not refresh", returned.isNotEmpty())
            assertEquals(2, returned.last().singleList!!.selectedIndex)
        } finally {
            controller.moveToState(Lifecycle.State.DESTROYED)
        }
    }

    @Test fun visibleHostCategoryScreenRefreshesAfterPhoneChangesACategory() {
        val controller = ScreenController(CategoryScreen(carContext, store, maps))
            .moveToState(Lifecycle.State.RESUMED)
        try {
            controller.reset()
            PreferenceStore(context).use { it.setCategoryEnabled(PoiCategory.PARKS, false) }
            shadowOf(Looper.getMainLooper()).idle()
            val returned = controller.templatesReturned.filterIsInstance<ListTemplate>()
            assertTrue("The active host screen did not refresh", returned.isNotEmpty())
            assertFalse((returned.last().singleList!!.items.first() as Row).toggle!!.isChecked)
        } finally {
            controller.moveToState(Lifecycle.State.DESTROYED)
        }
    }

    @Test fun mapUsesFourHostMapActionsAndRealNamedPoiRows() {
        val template = MapScreen(carContext, store, maps).onGetTemplate() as MapWithContentTemplate
        assertEquals(4, template.mapController!!.mapActionStrip!!.actions.size)
        val rows = (template.contentTemplate as ListTemplate).singleList!!.items.map { it as Row }
        assertEquals(listOf("Tema do mapa", "Categorias"), rows.take(2).map { it.title.toString() })
        val expected = maps.nearestTo(br.com.gamemap.render.NativeMapRenderer.defaultCamera(),
            store.snapshot().enabledCategories, rows.size - 2).map { it.displayName }
        assertTrue(expected.isNotEmpty())
        assertEquals(expected, rows.drop(2).map { it.title.toString() })
        assertTrue(rows.drop(2).all { it.onClickDelegate != null })
    }

    @Test fun allDisabledLeavesHostSettingsAvailableWithAnEmptyState() {
        PoiCategory.entries.forEach { store.setCategoryEnabled(it, false) }
        val template = MapScreen(carContext, store, maps).onGetTemplate() as MapWithContentTemplate
        val rows = (template.contentTemplate as ListTemplate).singleList!!.items.map { it as Row }
        assertEquals("Tema do mapa", rows[0].title.toString())
        assertEquals("Categorias", rows[1].title.toString())
        assertEquals("Nenhuma categoria ativa", rows[2].title.toString())
    }
}
