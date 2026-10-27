package br.com.gamemap.phone

import br.com.gamemap.navigation.LocationAccess
import br.com.gamemap.navigation.NavigationPhase
import br.com.gamemap.navigation.NavigationSnapshot

/** Initial GPS acquisition is normal progress, separate from an actual provider failure. */
internal data class RoutePreparationUi(val message: String, val action: String? = null, val busy: Boolean = false) {
    companion object {
        fun forState(state: NavigationSnapshot, precisePermission: Boolean): RoutePreparationUi? = when (state.phase) {
            NavigationPhase.WAITING_FOR_LOCATION -> when {
                state.locationAccess == LocationAccess.DISABLED -> RoutePreparationUi("Ative a localização para calcular a rota.", "Ativar localização")
                !precisePermission || state.locationAccess == LocationAccess.DENIED -> RoutePreparationUi("Permita sua localização para calcular a rota.", "Permitir localização")
                state.locationAccess == LocationAccess.NEEDS_PHONE_START -> RoutePreparationUi("Ative o GPS para continuar.", "Ativar GPS")
                state.locationAccess == LocationAccess.WEAK -> RoutePreparationUi("Aguardando uma posição GPS precisa…", busy = true)
                else -> RoutePreparationUi("Buscando sua localização…", busy = true)
            }
            NavigationPhase.CALCULATING -> RoutePreparationUi("Calculando rota…", busy = true)
            NavigationPhase.ERROR -> RoutePreparationUi(state.message ?: "Não foi possível calcular a rota.", "Tentar novamente")
            else -> null
        }
    }
}
