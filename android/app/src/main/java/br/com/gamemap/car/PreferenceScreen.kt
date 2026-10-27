package br.com.gamemap.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import br.com.gamemap.preferences.PreferenceStore

/** Refreshes a visible settings screen when the phone or car changes shared preferences. */
abstract class PreferenceScreen(
    context: CarContext,
    protected val preferences: PreferenceStore,
) : Screen(context) {
    private var unsubscribe: (() -> Unit)? = null

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                if (unsubscribe == null) unsubscribe = preferences.addListener { invalidate() }
            }

            override fun onStop(owner: LifecycleOwner) { releaseListener() }
            override fun onDestroy(owner: LifecycleOwner) { releaseListener() }
        })
    }

    private fun releaseListener() {
        unsubscribe?.invoke()
        unsubscribe = null
    }
}

internal fun hostListLimit(context: CarContext): Int = try {
    context.getCarService(ConstraintManager::class.java)
        .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST).coerceAtLeast(1)
} catch (_: RuntimeException) {
    // The library's minimum list capacity is safe while the host constraint service reconnects.
    6
}
