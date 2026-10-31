package br.com.gamemap.phone

import android.app.Application
import android.view.View
import android.view.ViewGroup
import br.com.gamemap.R
import br.com.gamemap.navigation.model.ManeuverKind
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class GlassControlsTest {
    @Test fun guidanceKeepsMenuReachableBesideLongStreetAtLargeFontScale() {
        val app = RuntimeEnvironment.getApplication()
        val configuration = android.content.res.Configuration(app.resources.configuration).apply { fontScale = 1.5f }
        val context = app.createConfigurationContext(configuration)
        val card = GuidanceCard(context)
        card.update("350 m", "Vire à direita na Avenida República do Líbano", "Avenida República do Líbano", ManeuverKind.RIGHT)
        card.measure(View.MeasureSpec.makeMeasureSpec(context.dip(320), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        card.layout(0, 0, card.measuredWidth, card.measuredHeight)
        val menu = card.findViewById<View>(R.id.phone_main_menu)
        assertNotNull("Guidance needs the same menu access as exploration", menu)
        assertTrue(menu!!.width >= context.dip(48))
        assertTrue(menu.height >= context.dip(48))
        assertTrue(menu.right <= card.width - card.paddingRight)
        assertNotEquals("The guidance card must not block its menu from keyboard focus", ViewGroup.FOCUS_BLOCK_DESCENDANTS, card.descendantFocusability)
    }
}
