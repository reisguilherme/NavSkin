package br.com.gamemap.car

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.car.app.CarContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class CarNavigationIntentsTest {
    @Test fun opaqueGeoQueryExtractsCoordinatesAndLabelInsteadOfZeroZero() {
        val result = CarNavigationIntents.parse(Intent(CarContext.ACTION_NAVIGATE,
            Uri.parse("geo:0,0?q=-16.67,-49.25(Parque)"))) as CarDestinationIntent.Place
        assertEquals(-16.67, result.destination.point.latitude, 0.00001)
        assertEquals(-49.25, result.destination.point.longitude, 0.00001)
        assertEquals("Parque", result.destination.name)
    }

    @Test fun addressRequestRemainsASearchRequiringUserSelection() {
        val result = CarNavigationIntents.parse(Intent(Intent.ACTION_VIEW,
            Uri.parse("geo:0,0?q=Parque%20Goiania"))) as CarDestinationIntent.Search
        assertEquals("Parque Goiania", result.query)
    }

    @Test fun invalidCoordinatesAndUnrelatedIntentsCannotSelectAPlace() {
        assertNull(CarNavigationIntents.parse(Intent(Intent.ACTION_VIEW, Uri.parse("geo:91,181"))))
        assertNull(CarNavigationIntents.parse(Intent("arbitrary", Uri.parse("geo:-16,-49"))))
        assertNull(CarNavigationIntents.parse(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))))
    }
}
