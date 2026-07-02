package dev.mlg.quedalle.model

/**
 * Pure-JVM color math (no android.graphics dependency, unit-testable).
 * Colors are ARGB packed ints.
 */

/** WCAG relative luminance of an ARGB color, ignoring alpha. In [0, 1]. */
fun relativeLuminance(argb: Int): Double {
    fun channel(v: Int): Double {
        val c = v / 255.0
        return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }
    val r = channel((argb shr 16) and 0xFF)
    val g = channel((argb shr 8) and 0xFF)
    val b = channel(argb and 0xFF)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

fun alphaOf(argb: Int): Int = (argb ushr 24) and 0xFF

/** True when dark text reads better than light text on this background. */
fun prefersDarkText(background: Int): Boolean = relativeLuminance(background) > 0.4

/** Blends [a] towards [b] by [fraction] (0 = a, 1 = b), per ARGB channel. */
fun blendArgb(a: Int, b: Int, fraction: Float): Int {
    val f = fraction.coerceIn(0f, 1f)
    fun mix(shift: Int): Int {
        val ca = (a shr shift) and 0xFF
        val cb = (b shr shift) and 0xFF
        return (ca + ((cb - ca) * f)).toInt() and 0xFF
    }
    return (mix(24) shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
}

fun darken(argb: Int, fraction: Float): Int =
    blendArgb(argb, (argb and 0xFF000000.toInt()), fraction) // towards black, keep alpha

fun lighten(argb: Int, fraction: Float): Int =
    blendArgb(argb, (argb and 0xFF000000.toInt()) or 0x00FFFFFF, fraction)

/** HSL (h in [0, 360), s and l in [0, 1]) to opaque ARGB. */
fun hslToArgb(h: Float, s: Float, l: Float): Int {
    val hue = ((h % 360f) + 360f) % 360f
    val sat = s.coerceIn(0f, 1f)
    val lum = l.coerceIn(0f, 1f)
    val c = (1f - Math.abs(2f * lum - 1f)) * sat
    val x = c * (1f - Math.abs((hue / 60f) % 2f - 1f))
    val m = lum - c / 2f
    val (r1, g1, b1) = when {
        hue < 60f  -> Triple(c, x, 0f)
        hue < 120f -> Triple(x, c, 0f)
        hue < 180f -> Triple(0f, c, x)
        hue < 240f -> Triple(0f, x, c)
        hue < 300f -> Triple(x, 0f, c)
        else       -> Triple(c, 0f, x)
    }
    fun toByte(v: Float): Int = Math.round((v + m).coerceIn(0f, 1f) * 255f)
    return (0xFF shl 24) or (toByte(r1) shl 16) or (toByte(g1) shl 8) or toByte(b1)
}

/** Opaque ARGB to HSL triple (h in [0, 360), s and l in [0, 1]). */
fun argbToHsl(argb: Int): Triple<Float, Float, Float> {
    val r = ((argb shr 16) and 0xFF) / 255f
    val g = ((argb shr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val l = (max + min) / 2f
    if (delta == 0f) return Triple(0f, 0f, l)
    val s = delta / (1f - Math.abs(2f * l - 1f))
    val h = when (max) {
        r    -> 60f * (((g - b) / delta) % 6f)
        g    -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }
    return Triple(((h % 360f) + 360f) % 360f, s.coerceIn(0f, 1f), l)
}
