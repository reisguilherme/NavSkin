package br.com.gamemap.traffic

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.DisplayMetrics

/** Small geometric symbols authored here, independent of glyph servers and map theme fonts. */
internal object TrafficSymbols {
    fun imageId(kind: TrafficIncidentKind) = "gamemap-traffic-${kind.name.lowercase()}"
    fun draw(kind: TrafficIncidentKind): Bitmap {
        val bitmap = Bitmap.createBitmap(36, 36, Bitmap.Config.ARGB_8888).apply {
            density = DisplayMetrics.DENSITY_DEFAULT
        }
        val canvas = Canvas(bitmap)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(kind.color) }
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; strokeWidth = 2.2f; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND
        }
        val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; strokeWidth = 3f; strokeCap = Paint.Cap.ROUND
        }
        when (kind) {
            TrafficIncidentKind.CLOSURE -> {
                canvas.drawCircle(18f, 18f, 14f, fill)
                canvas.drawCircle(18f, 18f, 14f, outline)
                canvas.drawLine(10f, 18f, 26f, 18f, mark)
            }
            TrafficIncidentKind.WORKS -> {
                val diamond = Path().apply { moveTo(18f, 2f); lineTo(34f, 18f); lineTo(18f, 34f); lineTo(2f, 18f); close() }
                canvas.drawPath(diamond, fill)
                canvas.drawPath(diamond, outline)
                canvas.drawLine(11f, 25f, 24f, 12f, mark)
                canvas.drawLine(12f, 11f, 25f, 24f, mark)
                canvas.drawLine(10f, 13f, 15f, 8f, mark)
            }
            TrafficIncidentKind.JAM -> {
                canvas.drawCircle(18f, 18f, 14f, fill)
                canvas.drawCircle(18f, 18f, 14f, outline)
                for (y in listOf(11f, 18f, 25f)) canvas.drawLine(11f, y, 25f, y, mark)
            }
            else -> {
                val triangle = Path().apply { moveTo(18f, 3f); lineTo(34f, 31f); lineTo(2f, 31f); close() }
                canvas.drawPath(triangle, fill)
                canvas.drawPath(triangle, outline)
                canvas.drawLine(18f, 13f, 18f, 21f, mark)
                canvas.drawCircle(18f, 26f, 1.8f, mark)
            }
        }
        return bitmap
    }
}
