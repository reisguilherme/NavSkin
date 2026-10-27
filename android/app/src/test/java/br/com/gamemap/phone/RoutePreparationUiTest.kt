package br.com.gamemap.phone

import br.com.gamemap.navigation.*
import org.junit.Assert.*
import org.junit.Test

class RoutePreparationUiTest {
    @Test fun firstGpsFixShowsProgressWithoutRetry() {
        val ui = RoutePreparationUi.forState(NavigationSnapshot(phase = NavigationPhase.WAITING_FOR_LOCATION,
            locationAccess = LocationAccess.WAITING), precisePermission = true)!!
        assertTrue(ui.busy)
        assertNull(ui.action)
        assertFalse(ui.message.contains("erro", ignoreCase = true))
        assertTrue(ui.message.contains("localização"))
    }
    @Test fun actualProviderRequestShowsCalculationWithoutRetry() {
        val ui = RoutePreparationUi.forState(NavigationSnapshot(phase = NavigationPhase.CALCULATING), true)!!
        assertTrue(ui.busy)
        assertNull(ui.action)
        assertTrue(ui.message.contains("Calculando"))
    }
    @Test fun locationPermissionAndDisabledProviderHaveSpecificActions() {
        val disabled = RoutePreparationUi.forState(NavigationSnapshot(phase = NavigationPhase.WAITING_FOR_LOCATION,
            locationAccess = LocationAccess.DISABLED), true)!!
        assertEquals("Ativar localização", disabled.action)
        assertFalse(disabled.busy)
        val denied = RoutePreparationUi.forState(NavigationSnapshot(phase = NavigationPhase.WAITING_FOR_LOCATION,
            locationAccess = LocationAccess.DENIED), false)!!
        assertEquals("Permitir localização", denied.action)
        assertFalse(denied.busy)
    }
    @Test fun realErrorKeepsItsExplanationAndExplicitRetry() {
        val ui = RoutePreparationUi.forState(NavigationSnapshot(phase = NavigationPhase.ERROR,
            message = "Sem conexão para calcular a rota."), true)!!
        assertEquals("Sem conexão para calcular a rota.", ui.message)
        assertEquals("Tentar novamente", ui.action)
        assertFalse(ui.busy)
    }
    @Test fun validTripEstimatesKeepTheirOwnCleanCard() {
        assertNull(RoutePreparationUi.forState(NavigationSnapshot(phase = NavigationPhase.PREVIEW), true))
        assertNull(RoutePreparationUi.forState(NavigationSnapshot(phase = NavigationPhase.NAVIGATING), true))
    }
}
