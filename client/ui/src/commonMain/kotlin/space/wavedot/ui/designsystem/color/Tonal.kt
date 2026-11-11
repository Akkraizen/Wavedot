package space.wavedot.ui.designsystem.color

import androidx.compose.ui.graphics.Color
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class Lch(val lightness: Double, val chroma: Double, val hue: Double)

object ColorSpace {
    private const val XN = 0.95047
    private const val YN = 1.0
    private const val ZN = 1.08883
    private const val EPSILON = 216.0 / 24389.0
    private const val KAPPA = 24389.0 / 27.0

    fun toLch(color: Color): Lch {
        val r = linearize(color.red.toDouble())
        val g = linearize(color.green.toDouble())
        val b = linearize(color.blue.toDouble())
        val x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / XN
        val y = (0.2126729 * r + 0.7151522 * g + 0.0721750 * b) / YN
        val z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / ZN
        val fx = labF(x)
        val fy = labF(y)
        val fz = labF(z)
        val l = 116 * fy - 16
        val a = 500 * (fx - fy)
        val bb = 200 * (fy - fz)
        val hue = (atan2(bb, a) * 180 / PI_DOUBLE).let { if (it < 0) it + 360 else it }
        return Lch(l, sqrt(a * a + bb * bb), hue)
    }

    fun fromLch(lch: Lch): Color? {
        val radians = lch.hue * PI_DOUBLE / 180
        val a = lch.chroma * cos(radians)
        val b = lch.chroma * sin(radians)
        val fy = (lch.lightness + 16) / 116
        val fx = fy + a / 500
        val fz = fy - b / 200
        val x = labFInverse(fx) * XN
        val y = labFInverse(fy) * YN
        val z = labFInverse(fz) * ZN
        val r = 3.2404542 * x - 1.5371385 * y - 0.4985314 * z
        val g = -0.9692660 * x + 1.8760108 * y + 0.0415560 * z
        val bl = 0.0556434 * x - 0.2040259 * y + 1.0572252 * z
        val tolerance = 1e-4
        if (listOf(r, g, bl).any { it < -tolerance || it > 1 + tolerance }) return null
        return Color(
            red = delinearize(r.coerceIn(0.0, 1.0)).toFloat(),
            green = delinearize(g.coerceIn(0.0, 1.0)).toFloat(),
            blue = delinearize(bl.coerceIn(0.0, 1.0)).toFloat(),
        )
    }

    fun inGamut(lightness: Double, chroma: Double, hue: Double): Color {
        val tone = lightness.coerceIn(0.0, 100.0)
        if (tone <= 0.0) return Color.Black
        if (tone >= 100.0) return Color.White
        fromLch(Lch(tone, chroma, hue))?.let { return it }
        var low = 0.0
        var high = chroma
        var best = fromLch(Lch(tone, 0.0, hue)) ?: grey(tone)
        repeat(24) {
            val mid = (low + high) / 2
            val candidate = fromLch(Lch(tone, mid, hue))
            if (candidate != null) {
                best = candidate
                low = mid
            } else {
                high = mid
            }
        }
        return best
    }

    fun relativeLuminance(color: Color): Double =
        0.2126 * linearize(color.red.toDouble()) +
            0.7152 * linearize(color.green.toDouble()) +
            0.0722 * linearize(color.blue.toDouble())

    fun contrast(first: Color, second: Color): Double {
        val a = relativeLuminance(first)
        val b = relativeLuminance(second)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    fun toHex(color: Color): String {
        fun channel(value: Float) = (value * 255).roundToInt().toString(16).padStart(2, '0')
        return "#" + channel(color.red) + channel(color.green) + channel(color.blue)
    }

    private fun grey(tone: Double): Color {
        val y = labFInverse((tone + 16) / 116)
        val value = delinearize(y).toFloat()
        return Color(value, value, value)
    }

    private fun linearize(channel: Double): Double =
        if (channel <= 0.04045) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)

    private fun delinearize(channel: Double): Double =
        if (channel <= 0.0031308) channel * 12.92 else 1.055 * channel.pow(1 / 2.4) - 0.055

    private fun labF(t: Double): Double = if (t > EPSILON) cbrt(t) else (KAPPA * t + 16) / 116

    private fun labFInverse(f: Double): Double {
        val cube = f * f * f
        return if (cube > EPSILON) cube else (116 * f - 16) / KAPPA
    }

    private const val PI_DOUBLE = kotlin.math.PI
}

class TonalPalette(private val hue: Double, private val chroma: Double) {
    private val cache = mutableMapOf<Int, Color>()

    fun tone(tone: Int): Color = cache.getOrPut(tone) { ColorSpace.inGamut(tone.toDouble(), chroma, hue) }
}
