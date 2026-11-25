package app.vent.domain

import kotlin.math.pow

object EventPalette {
    val seeds: Map<String, Int> =
        linkedMapOf(
            "Lavender" to 0xFFB9A7FF.toInt(),
            "Teal" to 0xFF9ED8C6.toInt(),
            "Sage" to 0xFFBCCBB0.toInt(),
        )

    data class Accents(
        val primary: Int,
        val onPrimary: Int,
        val secondary: Int,
        val onSecondary: Int,
        val tertiary: Int,
    )

    fun accents(
        seed: Int?,
        dark: Boolean,
    ): Accents {
        if (seed == null || seed == seeds["Lavender"]) {
            return if (dark) {
                Accents(0xFFB9A7FF.toInt(), 0xFF1A1334.toInt(), 0xFF9ED8C6.toInt(), 0xFF0D2A23.toInt(), 0xFFF2B872.toInt())
            } else {
                Accents(0xFF5B3FD6.toInt(), -1, 0xFF1F6B5A.toInt(), -1, 0xFF8A5A12.toInt())
            }
        }
        val primary = accessibleAccent(seed, dark)
        val secondarySeed =
            (seed and 0xFF00FF00.toInt()) or
                ((seed and 0x00FF0000) ushr 16) or ((seed and 0x000000FF) shl 16)
        val secondary = accessibleAccent(secondarySeed, dark)
        return Accents(
            primary,
            if (dark) 0xFF14161A.toInt() else -1,
            secondary,
            if (dark) 0xFF14161A.toInt() else -1,
            if (dark) 0xFFF2B872.toInt() else 0xFF8A5A12.toInt(),
        )
    }

    fun contrast(
        first: Int,
        second: Int,
    ): Double {
        val firstLuminance = luminance(first)
        val secondLuminance = luminance(second)
        return (maxOf(firstLuminance, secondLuminance) + .05) / (minOf(firstLuminance, secondLuminance) + .05)
    }

    private fun accessibleAccent(
        seed: Int,
        dark: Boolean,
    ): Int {
        val text = if (dark) 0xFF14161A.toInt() else -1
        var candidate = seed or 0xFF000000.toInt()
        repeat(32) {
            if (contrast(candidate, text) >= 4.5) return candidate

            fun channel(shift: Int): Int {
                val value = (candidate ushr shift) and 255
                return if (dark) (value + 12).coerceAtMost(255) else (value - 12).coerceAtLeast(0)
            }
            candidate = 0xFF000000.toInt() or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
        }
        return candidate
    }

    private fun luminance(color: Int): Double {
        fun channel(shift: Int): Double {
            val value = ((color ushr shift) and 255) / 255.0
            return if (value <= .04045) value / 12.92 else ((value + .055) / 1.055).pow(2.4)
        }
        return .2126 * channel(16) + .7152 * channel(8) + .0722 * channel(0)
    }
}
