// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.gesture

/**
 * Tuning of the decoder. Distances are in key units (see [GestureKeyboard.unit]) unless said otherwise;
 * the defaults were tuned with the offline harness (docs/gesture/RESULTS.md).
 */
data class GestureConfig(
    /** Points both the gesture and every template are resampled to. */
    val resamplePoints: Int = 32,
    /**
     * Typical shape error (mean point distance after normalising size and position, in bounding box units).
     * A channel's cost is its mean distance divided by its sigma, so a smaller sigma weighs it more.
     */
    val shapeSigma: Float = 0.06f,
    /** Typical location error in key units. */
    val locationSigma: Float = 0.3f,
    /** A point this close to the template counts as exactly on it: it is inside the key. */
    val locationTolerance: Float = 0.3f,
    /** Cost of the least frequent word relative to the most frequent one. */
    val frequencyWeight: Float = 6f,
    /** Cost of a word in a language the context does not support, in nats of language weight. */
    val languageWeight: Float = 0.35f,
    /** Cost bonus of a word the engine predicts as the next one, at confidence 1. */
    val contextWeight: Float = 3f,
    /** Cost of the words that make up the person's own dictionary: they rank like very common words. */
    val userWordFrequency: Int = 200,
    /** How far from the first and last point a key may be to start or end a word. */
    val endpointRadius: Float = 0.85f,
    /** A word is a candidate when the gesture is this long relative to the word's ideal path. */
    val minLengthRatio: Float = 0.5f,
    val maxLengthRatio: Float = 1.9f,
    /** Gestures shorter than this many key units are not decoded. */
    val minPathUnits: Float = GestureSensitivity.MIN_PATH_UNITS,
    /** Words kept before the context model re-ranks them. */
    val shortlist: Int = 40,
    /** Candidates returned. */
    val maxCandidates: Int = 5
) {
    init {
        require(resamplePoints >= MIN_POINTS) { "resamplePoints must be at least $MIN_POINTS" }
        require(shortlist >= maxCandidates) { "shortlist must hold the returned candidates" }
        require(maxCandidates > 0) { "maxCandidates must be positive" }
        require(shapeSigma > 0f && locationSigma > 0f) { "sigmas must be positive" }
    }

    companion object {
        const val MIN_POINTS = 8
    }
}

/** What the sensitivity setting means (0 = hard to trigger, 100 = triggers almost at once). */
object GestureSensitivity {
    const val MIN = 0
    const val MAX = 100
    const val DEFAULT = 50

    /** A path shorter than this many key widths is a key press that slid, not a word. */
    const val MIN_PATH_UNITS = 1.2f

    private const val SLOWEST_UNITS = 1.8f
    private const val FASTEST_UNITS = 0.55f

    /** How far the finger must travel from where it landed, in key widths, before a touch is a gesture. */
    fun startDistanceUnits(sensitivity: Int): Float {
        val t = sensitivity.coerceIn(MIN, MAX) / MAX.toFloat()
        return SLOWEST_UNITS + (FASTEST_UNITS - SLOWEST_UNITS) * t
    }
}
