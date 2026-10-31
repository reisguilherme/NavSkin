package br.com.gamemap.phone

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import br.com.gamemap.R
import br.com.gamemap.navigation.model.ManeuverKind

/** Stable views: location ticks update content without rebuilding the guidance hierarchy. */
internal class GuidanceCard(context: Context) : LinearLayout(context) {
    private val arrow = ManeuverIcon(context)
    private val distance = context.phoneText("", 36f, true)
    private val instruction = context.phoneText("", 14f, true)
    private val street = context.phoneText("", 12f)
    val menu = PhoneAction(context, R.drawable.phone_menu, "Menu NavSkin").apply { id = R.id.phone_main_menu }
    private var compact = false
    init {
        orientation = HORIZONTAL
        id = R.id.phone_guidance
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = context.dip(102)
        setPadding(context.dip(15), context.dip(16), context.dip(12), context.dip(16))
        addView(arrow, LayoutParams(context.dip(40), context.dip(44)).apply { marginEnd = context.dip(11) })
        addView(LinearLayout(context).apply {
            orientation = VERTICAL
            addView(distance)
            addView(instruction, LayoutParams(-1, -2).apply { topMargin = context.dip(3) })
            addView(street, LayoutParams(-1, -2).apply { topMargin = context.dip(3) })
        }, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        attachMenu()
        instruction.maxLines = 2; instruction.ellipsize = TextUtils.TruncateAt.END
        street.maxLines = 1; street.ellipsize = TextUtils.TruncateAt.END
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        descendantFocusability = FOCUS_AFTER_DESCENDANTS
    }
    fun attachMenu() {
        (menu.parent as? ViewGroup)?.removeView(menu)
        addView(menu, LayoutParams(context.dip(48), context.dip(48)).apply { marginStart = context.dip(8) })
    }
    fun bind(palette: PhonePalette) {
        surface(palette, 23)
        distance.setTextColor(palette.text)
        instruction.setTextColor(palette.text)
        street.setTextColor(palette.muted)
        arrow.color = palette.accent
        menu.bind(palette)
        menu.touchSurface(0x6bffffff, 100, 0xbdffffff.toInt())
    }
    fun compact(value: Boolean) {
        if (compact == value) return
        compact = value
        minimumHeight = context.dip(if (value) 88 else 102)
        distance.textSize = if (value) 30f else 36f
        instruction.maxLines = if (value) 1 else 2
        street.visibility = if (value || street.text.isNullOrBlank()) GONE else VISIBLE
        setPadding(context.dip(15), context.dip(if (value) 10 else 16), context.dip(12), context.dip(if (value) 10 else 16))
    }
    fun update(meters: String?, cue: String, road: String?, kind: ManeuverKind?) {
        distance.updateText(meters.orEmpty())
        distance.visibility = if (meters == null) GONE else VISIBLE
        instruction.updateText(cue)
        street.updateText(road.orEmpty())
        // Providers usually include the road in their instruction. Avoid repeating it.
        street.visibility = if (compact || road.isNullOrBlank() || cue.contains(road, ignoreCase = true)) GONE else VISIBLE
        arrow.kind = kind
        arrow.visibility = if (kind == null) GONE else VISIBLE
        val description = listOfNotNull(meters, cue, road?.takeIf { it.isNotBlank() }).joinToString(". ")
        if (contentDescription != description) contentDescription = description
    }
}

internal fun TextView.updateText(value: String) { if (text.toString() != value) text = value }

/** Vector cues remain crisp at any density and take the active theme's color. */
private class ManeuverIcon(context: Context) : View(context) {
    var kind: ManeuverKind? = null
        set(value) { if (field != value) { field = value; invalidate() } }
    var color = android.graphics.Color.BLACK
        set(value) { if (field != value) { field = value; invalidate() } }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3.5f
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.color = color
        canvas.save()
        val scale = minOf(width, height) / 48f
        canvas.translate((width - scale * 48) / 2, (height - scale * 48) / 2)
        canvas.scale(scale, scale)
        path.reset()
        when (kind) {
            ManeuverKind.LEFT, ManeuverKind.SHARP_LEFT, ManeuverKind.SLIGHT_LEFT,
            ManeuverKind.RIGHT, ManeuverKind.SHARP_RIGHT, ManeuverKind.SLIGHT_RIGHT -> {
                if (kind in listOf(ManeuverKind.RIGHT, ManeuverKind.SHARP_RIGHT, ManeuverKind.SLIGHT_RIGHT))
                    canvas.scale(-1f, 1f, 24f, 24f)
                path.moveTo(29f, 37f); path.lineTo(29f, 24f); path.quadTo(29f, 16f, 21f, 16f)
                path.lineTo(12f, 16f); path.moveTo(18f, 10f); path.lineTo(12f, 16f); path.lineTo(18f, 22f)
            }
            ManeuverKind.UTURN -> {
                path.moveTo(34f, 37f); path.lineTo(34f, 20f); path.cubicTo(34f, 6f, 14f, 6f, 14f, 20f)
                path.lineTo(14f, 30f); path.moveTo(8f, 24f); path.lineTo(14f, 30f); path.lineTo(20f, 24f)
            }
            ManeuverKind.ROUNDABOUT -> {
                path.moveTo(24f, 38f); path.lineTo(24f, 33f)
                path.addCircle(24f, 24f, 9f, Path.Direction.CCW)
                path.moveTo(24f, 15f); path.lineTo(24f, 8f)
                path.moveTo(18f, 14f); path.lineTo(24f, 8f); path.lineTo(30f, 14f)
            }
            ManeuverKind.ARRIVE -> {
                path.moveTo(15f, 38f); path.lineTo(15f, 10f); path.lineTo(34f, 10f)
                path.lineTo(29f, 18f); path.lineTo(34f, 25f); path.lineTo(15f, 25f)
            }
            ManeuverKind.FORK, ManeuverKind.EXIT, ManeuverKind.MERGE, ManeuverKind.UNKNOWN -> {
                // Provider has no side for these values; show an undirected junction.
                path.moveTo(24f, 37f); path.lineTo(24f, 25f); path.lineTo(13f, 13f)
                path.moveTo(24f, 25f); path.lineTo(35f, 13f)
            }
            else -> {
                path.moveTo(24f, 37f); path.lineTo(24f, 10f)
                path.moveTo(16f, 18f); path.lineTo(24f, 10f); path.lineTo(32f, 18f)
            }
        }
        canvas.drawPath(path, paint)
        canvas.restore()
    }
}

internal class TripMetrics(context: Context) : LinearLayout(context) {
    private val values = listOf("TEMPO", "DISTÂNCIA", "CHEGADA").map { caption ->
        val value = context.phoneText("", 23f, true).apply {
            gravity = Gravity.CENTER; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this, 14, 23, 1,
                android.util.TypedValue.COMPLEX_UNIT_SP)
        }
        val label = context.phoneText(caption, 10f).apply { gravity = Gravity.CENTER; maxLines = 1 }
        val column = LinearLayout(context).apply {
            orientation = VERTICAL; gravity = Gravity.CENTER
            addView(value, LayoutParams(-1, -2))
            addView(label, LayoutParams(-1, -2).apply { topMargin = context.dip(5) })
        }
        addView(column, LayoutParams(0, -2, 1f))
        value to label
    }
    init { orientation = HORIZONTAL; gravity = Gravity.CENTER; minimumHeight = context.dip(64); setPadding(0, context.dip(7), 0, context.dip(7)) }
    fun bind(palette: PhonePalette) = values.forEachIndexed { index, pair ->
        pair.first.setTextColor(palette.text)
        pair.second.setTextColor(palette.muted)
    }
    fun update(duration: String, distance: String, arrival: String) {
        listOf(duration, distance, arrival).forEachIndexed { index, text -> values[index].first.updateText(text) }
        contentDescription = "$duration · $distance · chegada $arrival"
    }
}
