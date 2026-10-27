package br.com.gamemap.phone

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import br.com.gamemap.R
import br.com.gamemap.preferences.MapTheme
import br.com.gamemap.preferences.PoiCategory
import kotlin.math.abs

/** Phone chrome stays light; the cartography independently follows the display's night mode. */
internal data class PhonePalette(
    val surface: Int, val text: Int, val muted: Int, val accent: Int,
    val soft: Int, val border: Int,
) {
    companion object {
        fun forTheme(theme: MapTheme) = when (theme.assetName) {
            "rosa" -> PhonePalette(color("#FFF7FA"), color("#542840"), color("#93647A"),
                color("#D92376"), color("#FFE2EE"), color("#F7CADF"))
            "rdr2" -> PhonePalette(color("#FFF9EA"), color("#483722"), color("#7D654C"),
                color("#A13228"), color("#F0E3C4"), color("#DED0B0"))
            else -> PhonePalette(color("#FFFCF4"), color("#252A1E"), color("#6A705D"),
                color("#4C692B"), color("#E8EEDB"), color("#E0E4D6"))
        }
        private fun color(value: String) = Color.parseColor(value)
    }
}

internal fun Context.dip(value: Int): Int = (value * resources.displayMetrics.density + .5f).toInt()

internal fun Context.rounded(color: Int, radius: Int = 20, stroke: Int? = null, strokeWidth: Int = 1) =
    GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dip(radius).toFloat()
        setColor(color)
        stroke?.let { setStroke(dip(strokeWidth), it) }
    }

internal fun View.surface(palette: PhonePalette, radius: Int = 20, stroke: Boolean = false) {
    background = context.rounded(palette.surface, radius, if (stroke) palette.border else null)
    elevation = context.dip(6).toFloat()
    outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
    clipToOutline = true
}

internal fun View.touchSurface(color: Int, radius: Int = 16, stroke: Int? = null, strokeWidth: Int = 1) {
    val shape = context.rounded(color, radius, stroke, strokeWidth)
    background = RippleDrawable(ColorStateList.valueOf(0x18000000), shape, context.rounded(Color.WHITE, radius))
    outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
    clipToOutline = true
}

internal fun Context.phoneText(value: String, size: Float, bold: Boolean = false) = TextView(this).apply {
    text = value
    textSize = size
    includeFontPadding = false
    if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
}

internal fun Context.phoneIcon(resource: Int, description: String? = null) = ImageView(this).apply {
    setImageResource(resource)
    scaleType = ImageView.ScaleType.CENTER_INSIDE
    contentDescription = description
    if (description == null) importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
}

/** A small native action with a generous touch target and no platform button bezel. */
internal class PhoneAction(context: Context, iconResource: Int, label: String, showLabel: Boolean = false) :
    LinearLayout(context) {
    private val icon = context.phoneIcon(iconResource)
    private val caption = context.phoneText(label, 14f, true)
    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        minimumHeight = context.dip(48)
        minimumWidth = context.dip(48)
        isClickable = true
        isFocusable = true
        contentDescription = label
        setPadding(context.dip(12), context.dip(8), context.dip(12), context.dip(8))
        addView(icon, LayoutParams(context.dip(22), context.dip(22)))
        if (showLabel) addView(caption, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = context.dip(9) })
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        caption.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    fun bind(palette: PhonePalette, filled: Boolean = false) {
        val foreground = if (filled) Color.WHITE else palette.text
        icon.imageTintList = ColorStateList.valueOf(foreground)
        caption.setTextColor(foreground)
        touchSurface(if (filled) palette.accent else Color.TRANSPARENT, 16)
    }
}

/** Native scrolling with a settled card edge; no additional list framework is needed. */
internal class ThemeCarousel(context: Context) : HorizontalScrollView(context) {
    var cardStep = context.dip(224)
    private var touching = false
    private val snap = Runnable {
        val maximum = ((getChildAt(0)?.width ?: 0) - width + paddingLeft + paddingRight).coerceAtLeast(0)
        // The final card's fully visible position is usually between regular card edges.
        val candidates = (0..(maximum / cardStep)).map { it * cardStep } + maximum
        val target = candidates.minByOrNull { abs(scrollX - it) } ?: 0
        if (abs(scrollX - target) > 2) smoothScrollTo(target, 0)
    }
    init {
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        clipToPadding = false
        contentDescription = "Prévia dos temas"
    }
    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        removeCallbacks(snap)
        if (!touching) postDelayed(snap, 180)
    }
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) { touching = true; removeCallbacks(snap) }
        val handled = super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            touching = false
            postDelayed(snap, 180)
        }
        return handled
    }
    override fun onDetachedFromWindow() { removeCallbacks(snap); super.onDetachedFromWindow() }
}

internal fun categoryIcon(category: PoiCategory) = when (category) {
    PoiCategory.PARKS -> R.drawable.phone_park
    PoiCategory.FUEL -> R.drawable.phone_fuel
    PoiCategory.FOOD -> R.drawable.phone_food
    PoiCategory.PARKING -> R.drawable.phone_parking
    PoiCategory.HEALTH -> R.drawable.phone_health
    PoiCategory.SHOPS -> R.drawable.phone_shop
}
