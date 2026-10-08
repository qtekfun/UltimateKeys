// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.gesture.harness

import com.qtekfun.ultimatekeys.gesture.GestureAlphabet
import com.qtekfun.ultimatekeys.gesture.GestureKeyboard
import java.util.Random
import kotlin.math.hypot

/**
 * How a simulated finger deviates from the ideal path through the keys of a word. Distances are in key
 * units. The three named profiles bracket real use: a careful writer, a typical one and a sloppy one.
 */
data class NoiseProfile(
    val name: String,
    /** Standard deviation of the miss of each key the path is aimed at, per axis. */
    val keySigma: Float,
    /** Standard deviation of a shift that applies to the whole gesture (a hand that sits off-centre). */
    val biasSigma: Float,
    /** How much of each corner the finger cuts, as a fraction of the adjoining segments (0 = sharp). */
    val cornerCut: Float,
    /** Standard deviation of the touch sampling noise, in key units. */
    val jitterSigma: Float = DEFAULT_JITTER
) {
    companion object {
        private const val DEFAULT_JITTER = 0.02f
        val Careful =
            NoiseProfile("careful", keySigma = 0.10f, biasSigma = 0.04f, cornerCut = 0.08f)
        val Typical =
            NoiseProfile("typical", keySigma = 0.20f, biasSigma = 0.08f, cornerCut = 0.15f)
        val Sloppy = NoiseProfile("sloppy", keySigma = 0.35f, biasSigma = 0.15f, cornerCut = 0.25f)
        val all = listOf(Careful, Typical, Sloppy)
    }
}

/**
 * Makes up the touch path of someone gliding over the letters of a word: a polyline through the key
 * centres, each vertex missed by random amounts, the whole thing shifted a little, corners rounded by
 * corner cutting (Chaikin subdivision), then sampled like a touchscreen reports it (every few pixels,
 * unevenly, with a little noise). Deterministic for a given seed.
 */
class SyntheticGestureGenerator(
    private val keyboard: GestureKeyboard,
    private val profile: NoiseProfile,
    seed: Long
) {
    private val random = Random(seed)

    /** The path of [word] as interleaved coordinates, or null when the word cannot be traced here. */
    fun generate(word: String): FloatArray? {
        val sequence = GestureAlphabet.sequenceOf(word) ?: return null
        if (sequence.any { !keyboard.has(it.toInt()) }) return null
        val unit = keyboard.unit
        val biasX = gaussian(profile.biasSigma) * unit
        val biasY = gaussian(profile.biasSigma) * unit
        var vertices = FloatArray(2 * sequence.size)
        sequence.forEachIndexed { i, key ->
            vertices[2 * i] =
                keyboard.centerX[key.toInt()] + biasX + gaussian(profile.keySigma) * unit
            vertices[2 * i + 1] =
                keyboard.centerY[key.toInt()] + biasY + gaussian(profile.keySigma) * unit
        }
        repeat(SUBDIVISIONS) { vertices = chaikin(vertices) }
        return sample(vertices, unit)
    }

    private fun gaussian(sigma: Float): Float = (random.nextGaussian() * sigma).toFloat()

    /** One round of corner cutting; the first and last points are kept so that the word's ends stay put. */
    private fun chaikin(points: FloatArray): FloatArray {
        val n = points.size / 2
        if (n < MIN_CURVE_POINTS) return points
        val ratio = profile.cornerCut
        val out = ArrayList<Float>(points.size * 2)
        out += points[0]
        out += points[1]
        for (i in 0 until n - 1) {
            val ax = points[2 * i]
            val ay = points[2 * i + 1]
            val bx = points[2 * (i + 1)]
            val by = points[2 * (i + 1) + 1]
            if (i > 0) {
                out += ax + (bx - ax) * ratio
                out += ay + (by - ay) * ratio
            }
            if (i < n - 2) {
                out += bx + (ax - bx) * ratio
                out += by + (ay - by) * ratio
            }
        }
        out += points[2 * n - 2]
        out += points[2 * n - 1]
        return out.toFloatArray()
    }

    /** Walks the curve and reports a touch point every few pixels, like a screen at a steady finger speed. */
    private fun sample(curve: FloatArray, unit: Float): FloatArray {
        val out = ArrayList<Float>()
        out += curve[0]
        out += curve[1]
        var untilNext = stepLength(unit)
        for (i in 1 until curve.size / 2) {
            val ax = curve[2 * i - 2]
            val ay = curve[2 * i - 1]
            val bx = curve[2 * i]
            val by = curve[2 * i + 1]
            val segment = hypot(bx - ax, by - ay)
            var at = 0f
            while (segment - at >= untilNext) {
                at += untilNext
                val t = at / segment
                out += ax + (bx - ax) * t + gaussian(profile.jitterSigma) * unit
                out += ay + (by - ay) * t + gaussian(profile.jitterSigma) * unit
                untilNext = stepLength(unit)
            }
            untilNext -= segment - at
        }
        out += curve[curve.size - 2]
        out += curve[curve.size - 1]
        return out.toFloatArray()
    }

    private fun stepLength(unit: Float): Float =
        unit * (MIN_STEP + random.nextFloat() * (MAX_STEP - MIN_STEP))

    private companion object {
        const val SUBDIVISIONS = 2
        const val MIN_CURVE_POINTS = 3
        const val MIN_STEP = 0.08f
        const val MAX_STEP = 0.25f
    }
}
