package br.com.gamemap.navigation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.car.app.notification.CarAppExtender
import br.com.gamemap.GameMapApplication
import br.com.gamemap.navigation.location.AndroidLocationSource
import br.com.gamemap.navigation.model.LocationQuality
import br.com.gamemap.phone.MainActivity

/** Foreground lifecycle belongs to the shared session, independent of the phone/car views. */
class NavigationService : Service() {
    private lateinit var runtime: NavigationRuntime
    private var source: AndroidLocationSource? = null
    private var removeListener: (() -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        runtime = (application as GameMapApplication).navigation
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Localização e navegação",
            NotificationManager.IMPORTANCE_LOW).apply {
            description = "Mantém o GPS ativo durante a navegação. Sem instruções de voz."
            setSound(null, null)
        })
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(runtime.snapshot()),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0)
            runtime.onServiceStarted()
            source = AndroidLocationSource(this, runtime::acceptFix,
                runtime::onLocationProvidersChanged, runtime::onLocationFailure)
            if (source?.start() != true) {
                stopSelf()
                return
            }
            removeListener = runtime.addListener {
                manager.notify(NOTIFICATION_ID, notification(it))
            }
        } catch (_: Exception) {
            runtime.onLocationFailure("Abra o NavSkin no celular e ative a localização para iniciar o GPS.")
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            runtime.stopNavigation()
            stopSelf()
        }
        // A killed process offers a persisted destination for explicit recalculation, never stale guidance.
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        source?.stop()
        source = null
        removeListener?.invoke()
        removeListener = null
        if (::runtime.isInitialized) runtime.onServiceStopped()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun notification(snapshot: NavigationSnapshot): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, NavigationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val guidance = snapshot.progress?.takeIf {
            snapshot.guidanceAvailable && snapshot.location.quality == LocationQuality.GOOD
        }
        val instruction = guidance?.let { snapshot.route?.steps?.getOrNull(it.stepIndex)?.instruction }
        val title = when {
            snapshot.simulation -> "SIMULAÇÃO GPS DE DESENVOLVIMENTO"
            snapshot.locationAccess == LocationAccess.DISABLED -> "Localização desativada"
            snapshot.locationAccess == LocationAccess.LOST -> "Sinal GPS perdido"
            snapshot.locationAccess == LocationAccess.WEAK -> "Sinal GPS fraco"
            snapshot.phase == NavigationPhase.RECALCULATING -> "Recalculando rota…"
            snapshot.phase == NavigationPhase.REROUTE_FAILED -> "Falha no recálculo; tente novamente"
            !instruction.isNullOrBlank() -> instruction
            snapshot.navigationActive -> "Aguardando posição na rota"
            else -> "GPS ativo para escolher um destino"
        }
        val text = if (guidance != null) {
            val distance = guidance.distanceToManeuverMeters
            val distanceText = if (distance >= 1_000) String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.1f km", distance / 1_000)
                else "${distance.toInt()} m"
            listOf(distanceText, snapshot.destination?.name.orEmpty()).filter(String::isNotBlank).joinToString(" • ")
        } else snapshot.destination?.name ?: "Navegação visual, sem instruções de voz"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("NavSkin • $title")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .extend(CarAppExtender.Builder().setContentTitle(title).setContentText(text)
                .setImportance(NotificationManagerCompat.IMPORTANCE_LOW).build())
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Encerrar", stop)
            .build()
    }

    companion object {
        const val ACTION_STOP = "br.com.gamemap.STOP_NAVIGATION"
        private const val CHANNEL_ID = "gamemap_navigation"
        private const val NOTIFICATION_ID = 4701
    }
}
