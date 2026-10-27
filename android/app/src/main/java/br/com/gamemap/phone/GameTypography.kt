package br.com.gamemap.phone

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.TextView
import br.com.gamemap.data.GameAssetCatalog
import br.com.gamemap.preferences.MapTheme
import java.util.WeakHashMap

/** Theme fonts affect text only; image/vector controls keep their own artwork. */
internal object GameTypography {
    private val catalogs = WeakHashMap<Context, GameAssetCatalog>()
    private val fonts = mutableMapOf<String, Typeface?>()
    private val originals = WeakHashMap<TextView, Typeface?>()

    private fun font(context: Context, theme: MapTheme): Typeface? {
        val app = context.applicationContext
        val catalog = catalogs.getOrPut(app) { GameAssetCatalog.load(app) }
        val file = catalog.themes[theme.assetName]?.ui?.file ?: return null
        if (!fonts.containsKey(file)) fonts[file] = runCatching { Typeface.createFromAsset(app.assets, file) }.getOrNull()
        return fonts[file]
    }

    fun apply(root: View, theme: MapTheme) = applyTypeface(root, font(root.context, theme))

    internal fun applyTypeface(root: View, font: Typeface?) {
        if (root is TextView) {
            if (!originals.containsKey(root)) originals[root] = root.typeface
            val target = font ?: originals[root]
            if (root.typeface !== target) root.typeface = target
        }
        if (root is ViewGroup) for (index in 0 until root.childCount) applyTypeface(root.getChildAt(index), font)
    }

    /** Also styles later-created result rows and dialog controls; detach releases the listener. */
    fun observe(root: View, theme: () -> MapTheme): () -> Unit {
        val listener = ViewTreeObserver.OnGlobalLayoutListener { apply(root, theme()) }
        var observing = false
        fun start() {
            if (!observing && root.viewTreeObserver.isAlive) {
                root.viewTreeObserver.addOnGlobalLayoutListener(listener)
                observing = true
                apply(root, theme())
            }
        }
        fun stop() {
            if (observing && root.viewTreeObserver.isAlive) root.viewTreeObserver.removeOnGlobalLayoutListener(listener)
            observing = false
        }
        val attached = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = start()
            override fun onViewDetachedFromWindow(view: View) = stop()
        }
        root.addOnAttachStateChangeListener(attached)
        start()
        return { stop(); root.removeOnAttachStateChangeListener(attached) }
    }
}
