// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.style

import kotlin.math.pow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** A colour as ARGB, written in style files as `#RRGGBB` or `#AARRGGBB`. */
@Serializable(with = ArgbColorSerializer::class)
@JvmInline
value class ArgbColor(val argb: Int) {
    val alpha: Int get() = argb ushr ALPHA_SHIFT and BYTE
    val red: Int get() = argb ushr RED_SHIFT and BYTE
    val green: Int get() = argb ushr GREEN_SHIFT and BYTE
    val blue: Int get() = argb and BYTE

    /** WCAG relative luminance of the colour, ignoring alpha. */
    fun luminance(): Double =
        LUMA_RED * linear(red) + LUMA_GREEN * linear(green) + LUMA_BLUE * linear(blue)

    fun toHex(): String =
        if (alpha == BYTE) "#%06X".format(argb and RGB_MASK) else "#%08X".format(argb)

    override fun toString(): String = toHex()

    companion object {
        private const val ALPHA_SHIFT = 24
        private const val RED_SHIFT = 16
        private const val GREEN_SHIFT = 8
        private const val BYTE = 0xFF
        private const val RGB_MASK = 0xFFFFFF
        private const val HEX_RGB_LENGTH = 6
        private const val HEX_ARGB_LENGTH = 8
        private const val HEX_RADIX = 16
        private const val LUMA_RED = 0.2126
        private const val LUMA_GREEN = 0.7152
        private const val LUMA_BLUE = 0.0722
        private const val SRGB_KNEE = 0.03928
        private const val SRGB_LOW_DIVISOR = 12.92
        private const val SRGB_OFFSET = 0.055
        private const val SRGB_SCALE = 1.055
        private const val SRGB_GAMMA = 2.4
        private val HEX_DIGITS = "0123456789abcdefABCDEF".toSet()

        /** An opaque colour from `0xRRGGBB`. */
        fun rgb(rgb: Int) = ArgbColor(rgb or (BYTE shl ALPHA_SHIFT))

        /** Parses `#RRGGBB` or `#AARRGGBB`; returns null for anything else. */
        fun parse(text: String): ArgbColor? {
            val hex = text.trim().removePrefix("#")
            if (hex.any { it !in HEX_DIGITS }) return null
            val value = hex.toLongOrNull(HEX_RADIX) ?: return null
            return when (hex.length) {
                HEX_RGB_LENGTH -> rgb(value.toInt())
                HEX_ARGB_LENGTH -> ArgbColor(value.toInt())
                else -> null
            }
        }

        private fun linear(channel: Int): Double {
            val c = channel / BYTE.toDouble()
            return if (c <= SRGB_KNEE) {
                c / SRGB_LOW_DIVISOR
            } else {
                ((c + SRGB_OFFSET) / SRGB_SCALE).pow(SRGB_GAMMA)
            }
        }
    }
}

private const val CONTRAST_OFFSET = 0.05
private const val AA_NORMAL_TEXT = 4.5

/** WCAG contrast ratio between two colours (1 to 21). */
fun contrastRatio(a: ArgbColor, b: ArgbColor): Double {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + CONTRAST_OFFSET) / (minOf(la, lb) + CONTRAST_OFFSET)
}

/** True when text of colour [text] on [background] meets WCAG AA for normal text (4.5:1). */
fun meetsAa(text: ArgbColor, background: ArgbColor): Boolean =
    contrastRatio(text, background) >= AA_NORMAL_TEXT

internal object ArgbColorSerializer : KSerializer<ArgbColor> {
    override val descriptor = PrimitiveSerialDescriptor("ArgbColor", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ArgbColor) = encoder.encodeString(value.toHex())

    override fun deserialize(decoder: Decoder): ArgbColor {
        val text = decoder.decodeString()
        return ArgbColor.parse(text) ?: throw IllegalArgumentException("Invalid colour: $text")
    }
}
