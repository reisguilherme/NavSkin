package br.com.gamemap.phone

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import br.com.gamemap.render.NativeMapRenderer

/** Phone rendering is a separate test; this screen is not evidence of Android Auto operation. */
class SurfaceProbeActivity : ComponentActivity() {
    private lateinit var renderer: NativeMapRenderer
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(32, 35, 31))
        }
        val title = TextView(this).apply {
            text = "NavSkin · prova de superfície"
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding(20, 24, 20, 20)
        }
        val status = TextView(this).apply {
            setTextColor(Color.rgb(199, 215, 148))
            textSize = 12f
            setPadding(16, 12, 16, 12)
        }
        val mapView = NativeMapRenderer.createMapView(this, textureMode = true)
        root.addView(title)
        root.addView(mapView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(status)
        root.addView(TextView(this).apply {
            text = "© OpenStreetMap contributors · ODbL\nO teste no celular não comprova Android Auto."
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 11f
            setPadding(12, 8, 12, 18)
        })
        setContentView(root)
        renderer = NativeMapRenderer(this, mapView, onStatus = { status.text = it }, savedInstanceState = savedInstanceState)
    }
    override fun onStart() { super.onStart(); renderer.start() }
    override fun onResume() { super.onResume(); renderer.resume() }
    override fun onPause() { renderer.pause(); super.onPause() }
    override fun onStop() { renderer.stop(); super.onStop() }
    override fun onSaveInstanceState(outState: Bundle) { renderer.saveInstanceState(outState); super.onSaveInstanceState(outState) }
    override fun onLowMemory() { super.onLowMemory(); renderer.onLowMemory() }
    override fun onDestroy() { renderer.close(); super.onDestroy() }
}
