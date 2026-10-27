package br.com.gamemap.car

import androidx.car.app.model.CarText
import androidx.car.app.model.DateTimeWithZone
import androidx.car.app.model.Distance
import androidx.car.app.navigation.model.*
import br.com.gamemap.navigation.LocationAccess
import br.com.gamemap.navigation.NavigationPhase
import br.com.gamemap.navigation.NavigationSnapshot
import br.com.gamemap.navigation.model.LocationQuality
import br.com.gamemap.navigation.model.ManeuverKind
import br.com.gamemap.navigation.model.RouteStep
import java.util.TimeZone
import kotlin.math.ceil

/** CAL 1.7 models only. The host owns maneuvers and estimate controls. */
internal object CarNavigationModels {
    fun hasGuidance(state: NavigationSnapshot): Boolean = state.guidanceAvailable &&
        state.location.quality == LocationQuality.GOOD &&
        state.locationAccess == LocationAccess.AVAILABLE &&
        (state.simulation || state.serviceRunning) &&
        state.route?.steps?.getOrNull(state.progress?.stepIndex ?: -1) != null

    fun destinationEstimate(state: NavigationSnapshot): TravelEstimate? {
        if (!hasGuidance(state)) return null
        val progress = state.progress ?: return null
        return estimate(progress.remainingDistanceMeters, progress.remainingSeconds,
            progress.arrivalEpochMs, state.simulation)
    }

    fun navigationInfo(state: NavigationSnapshot): NavigationTemplate.NavigationInfo {
        if (state.phase == NavigationPhase.RECALCULATING)
            return RoutingInfo.Builder().setLoading(true).build()
        if (!hasGuidance(state)) return MessageInfo.Builder(statusTitle(state))
            .setText(statusText(state)).build()
        val route = state.route!!
        val progress = state.progress!!
        val builder = RoutingInfo.Builder().setCurrentStep(step(route.steps[progress.stepIndex]),
            distance(progress.distanceToManeuverMeters))
        // CAL's second step is useful only for maneuvers in quick succession.
        route.steps.getOrNull(progress.stepIndex + 1)?.takeIf {
            route.steps[progress.stepIndex].distanceMeters <= 300.0
        }?.let { builder.setNextStep(step(it)) }
        return builder.build()
    }

    fun trip(state: NavigationSnapshot): Trip {
        if (!hasGuidance(state)) return Trip.Builder().setLoading(true).build()
        val route = state.route!!
        val progress = state.progress!!
        val remaining = destinationEstimate(state)!!
        val destination = state.destination ?: route.destination
        // stepIndex points to the upcoming instruction, whose duration follows that turn.
        val afterTurnSeconds = route.steps.drop(progress.stepIndex).sumOf { it.durationSeconds }
        val toTurnSeconds = (progress.remainingSeconds - afterTurnSeconds).coerceAtLeast(0.0)
        val now = progress.arrivalEpochMs - (progress.remainingSeconds * 1_000).toLong()
        return Trip.Builder()
            .addDestination(Destination.Builder().setName(destination.name)
                .setAddress(destination.address).build(), remaining)
            .addStep(step(route.steps[progress.stepIndex]), estimate(progress.distanceToManeuverMeters,
                toTurnSeconds, now + (toTurnSeconds * 1_000).toLong(), state.simulation))
            .setCurrentRoad(route.steps[(progress.stepIndex - 1).coerceAtLeast(0)].streetName)
            .build()
    }

    fun statusTitle(state: NavigationSnapshot): String = when {
        state.phase == NavigationPhase.REROUTE_FAILED -> "Recálculo indisponível"
        state.phase == NavigationPhase.RECALCULATING -> "Recalculando rota"
        state.phase == NavigationPhase.ARRIVED -> "Você chegou"
        state.locationAccess == LocationAccess.NEEDS_PHONE_START -> "Ative o GPS no celular"
        state.locationAccess == LocationAccess.DENIED -> "Permissão de localização"
        state.locationAccess == LocationAccess.DISABLED -> "Localização desativada"
        state.locationAccess == LocationAccess.LOST -> "Sinal GPS perdido"
        state.locationAccess == LocationAccess.WEAK -> "Sinal GPS impreciso"
        state.progress?.onRoute == false -> "Verificando trajeto"
        state.navigationActive -> "Aguardando posição GPS"
        state.phase == NavigationPhase.CALCULATING -> "Calculando rota"
        state.phase == NavigationPhase.RESUME_AVAILABLE -> "Retomar destino"
        state.phase == NavigationPhase.ERROR -> "Não foi possível calcular"
        else -> "NavSkin · destinos"
    }

    fun statusText(state: NavigationSnapshot): String = when {
        state.phase == NavigationPhase.REROUTE_FAILED -> state.message ?: "Tente recalcular quando houver conexão."
        state.locationAccess == LocationAccess.LOST -> "Última posição conhecida. Aguarde um novo sinal."
        state.locationAccess == LocationAccess.WEAK -> "Aguardando precisão para atualizar as instruções."
        state.progress?.onRoute == false -> "As instruções serão retomadas após confirmar a posição."
        state.phase == NavigationPhase.RECALCULATING -> "Aguardando uma nova rota."
        else -> state.message ?: "Escolha um destino salvo, recente ou pesquise."
    }

    private fun estimate(meters: Double, seconds: Double, arrival: Long, simulation: Boolean) =
        TravelEstimate.Builder(distance(meters),
            DateTimeWithZone.create(arrival.coerceAtLeast(0), TimeZone.getDefault()))
            .setRemainingTimeSeconds(ceil(seconds.coerceAtLeast(0.0)).toLong())
            .setTripText(CarText.create(if (simulation) "SIMULAÇÃO · sem trânsito" else "Sem trânsito em tempo real"))
            .build()

    private fun distance(meters: Double) = Distance.create(meters.coerceAtLeast(0.0), Distance.UNIT_METERS)

    private fun step(value: RouteStep): Step {
        val kind = when (value.kind) {
            ManeuverKind.DEPART -> Maneuver.TYPE_DEPART
            ManeuverKind.STRAIGHT -> Maneuver.TYPE_STRAIGHT
            ManeuverKind.SLIGHT_LEFT -> Maneuver.TYPE_TURN_SLIGHT_LEFT
            ManeuverKind.LEFT -> Maneuver.TYPE_TURN_NORMAL_LEFT
            ManeuverKind.SHARP_LEFT -> Maneuver.TYPE_TURN_SHARP_LEFT
            ManeuverKind.SLIGHT_RIGHT -> Maneuver.TYPE_TURN_SLIGHT_RIGHT
            ManeuverKind.RIGHT -> Maneuver.TYPE_TURN_NORMAL_RIGHT
            ManeuverKind.SHARP_RIGHT -> Maneuver.TYPE_TURN_SHARP_RIGHT
            ManeuverKind.UTURN -> Maneuver.TYPE_U_TURN_LEFT
            ManeuverKind.ARRIVE -> Maneuver.TYPE_DESTINATION
            ManeuverKind.MERGE -> Maneuver.TYPE_MERGE_SIDE_UNSPECIFIED
            // The provider does not retain side for these generic kinds.
            ManeuverKind.FORK, ManeuverKind.EXIT, ManeuverKind.UNKNOWN -> Maneuver.TYPE_UNKNOWN
            ManeuverKind.ROUNDABOUT -> if ((value.exitNumber ?: 0) > 0)
                Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CCW else Maneuver.TYPE_ROUNDABOUT_ENTER_CCW
        }
        val maneuver = Maneuver.Builder(kind)
        if (value.kind == ManeuverKind.ROUNDABOUT && (value.exitNumber ?: 0) > 0)
            maneuver.setRoundaboutExitNumber(value.exitNumber!!)
        return Step.Builder(value.instruction).setRoad(value.streetName)
            .setManeuver(maneuver.build()).build()
    }
}
