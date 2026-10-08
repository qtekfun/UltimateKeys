// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

@file:Suppress("TooManyFunctions", "LongParameterList")

package com.qtekfun.ultimatekeys.engine

import java.util.ArrayList

/**
 * Raw JNI surface of the vendored AOSP LatinIME engine (`libukengine.so`).
 *
 * The native side registers every method below on this class with `RegisterNatives`
 * (`third_party/aosp-latinime/native/jni/jni_common.cpp`), so names and signatures must match the
 * `JNINativeMethod` tables exactly; a mismatch makes `System.loadLibrary` fail with
 * `NoSuchMethodError`. Nothing outside `:engine` should use this object: [AospSuggestionEngine]
 * wraps it behind [NativeBridge].
 *
 * Handles (`dict`, `session`, `proximityInfo`) are native pointers held as `Long`; `0` means
 * "none". The native code does no locking of its own, so a dictionary handle must never be used
 * from two threads at once. None of these calls may run on the main thread: opening a dictionary
 * maps files and a suggestion query takes milliseconds.
 */
internal object NativeEngine {
    init {
        System.loadLibrary("ukengine")
    }

    // region Dictionary lifecycle

    @JvmStatic
    external fun openNative(
        sourceDir: String,
        dictOffset: Long,
        dictSize: Long,
        isUpdatable: Boolean
    ): Long

    @JvmStatic
    external fun createOnMemoryNative(
        formatVersion: Long,
        locale: String,
        attributeKeys: Array<String>,
        attributeValues: Array<String>
    ): Long

    @JvmStatic
    external fun closeNative(dict: Long)

    @JvmStatic
    external fun getFormatVersionNative(dict: Long): Int

    @JvmStatic
    external fun getHeaderInfoNative(
        dict: Long,
        outHeaderSize: IntArray,
        outFormatVersion: IntArray,
        outAttributeKeys: ArrayList<IntArray>,
        outAttributeValues: ArrayList<IntArray>
    )

    @JvmStatic
    external fun flushNative(dict: Long, filePath: String): Boolean

    @JvmStatic
    external fun needsToRunGCNative(dict: Long, mindsBlockByGC: Boolean): Boolean

    @JvmStatic
    external fun flushWithGCNative(dict: Long, filePath: String): Boolean

    @JvmStatic
    external fun isCorruptedNative(dict: Long): Boolean

    @JvmStatic
    external fun migrateNative(dict: Long, dictFilePath: String, newFormatVersion: Long): Boolean

    @JvmStatic
    external fun getPropertyNative(dict: Long, query: String): String

    // endregion

    // region Queries

    @JvmStatic
    external fun getSuggestionsNative(
        dict: Long,
        proximityInfo: Long,
        traverseSession: Long,
        xCoordinates: IntArray,
        yCoordinates: IntArray,
        times: IntArray,
        pointerIds: IntArray,
        inputCodePoints: IntArray,
        inputSize: Int,
        suggestOptions: IntArray,
        prevWordCodePointArrays: Array<IntArray>,
        isBeginningOfSentenceArray: BooleanArray,
        prevWordCount: Int,
        outSuggestionCount: IntArray,
        outCodePoints: IntArray,
        outScores: IntArray,
        outSpaceIndices: IntArray,
        outTypes: IntArray,
        outAutoCommitFirstWordConfidence: IntArray,
        inOutWeightOfLangModelVsSpatialModel: FloatArray
    )

    @JvmStatic
    external fun getProbabilityNative(dict: Long, word: IntArray): Int

    @JvmStatic
    external fun getMaxProbabilityOfExactMatchesNative(dict: Long, word: IntArray): Int

    @JvmStatic
    external fun getNgramProbabilityNative(
        dict: Long,
        prevWordCodePointArrays: Array<IntArray>,
        isBeginningOfSentenceArray: BooleanArray,
        word: IntArray
    ): Int

    @JvmStatic
    external fun getWordPropertyNative(
        dict: Long,
        word: IntArray,
        isBeginningOfSentence: Boolean,
        outCodePoints: IntArray,
        outFlags: BooleanArray,
        outProbabilityInfo: IntArray,
        outNgramPrevWordsArray: ArrayList<Array<IntArray>>,
        outNgramPrevWordIsBeginningOfSentenceArray: ArrayList<BooleanArray>,
        outNgramTargets: ArrayList<IntArray>,
        outNgramProbabilityInfo: ArrayList<IntArray>,
        outShortcutTargets: ArrayList<IntArray>,
        outShortcutProbabilities: ArrayList<Int>
    )

    /** Iterates all words: pass `0` to start, then the returned token; returns `0` at the end. */
    @JvmStatic
    external fun getNextWordNative(
        dict: Long,
        token: Int,
        outCodePoints: IntArray,
        outIsBeginningOfSentence: BooleanArray
    ): Int

    // endregion

    // region Updates (only on dictionaries opened as updatable)

    @JvmStatic
    external fun addUnigramEntryNative(
        dict: Long,
        word: IntArray,
        probability: Int,
        shortcutTarget: IntArray?,
        shortcutProbability: Int,
        isBeginningOfSentence: Boolean,
        isNotAWord: Boolean,
        isPossiblyOffensive: Boolean,
        timestamp: Int
    ): Boolean

    @JvmStatic
    external fun removeUnigramEntryNative(dict: Long, word: IntArray): Boolean

    @JvmStatic
    external fun addNgramEntryNative(
        dict: Long,
        prevWordCodePointArrays: Array<IntArray>,
        isBeginningOfSentenceArray: BooleanArray,
        word: IntArray,
        probability: Int,
        timestamp: Int
    ): Boolean

    @JvmStatic
    external fun removeNgramEntryNative(
        dict: Long,
        prevWordCodePointArrays: Array<IntArray>,
        isBeginningOfSentenceArray: BooleanArray,
        word: IntArray
    ): Boolean

    @JvmStatic
    external fun updateEntriesForWordWithNgramContextNative(
        dict: Long,
        prevWordCodePointArrays: Array<IntArray>,
        isBeginningOfSentenceArray: BooleanArray,
        word: IntArray,
        isValidWord: Boolean,
        count: Int,
        timestamp: Int
    ): Boolean

    // endregion

    // region Proximity info and traverse sessions

    @JvmStatic
    external fun setProximityInfoNative(
        displayWidth: Int,
        displayHeight: Int,
        gridWidth: Int,
        gridHeight: Int,
        mostCommonKeyWidth: Int,
        mostCommonKeyHeight: Int,
        proximityChars: IntArray,
        keyCount: Int,
        keyXCoordinates: IntArray?,
        keyYCoordinates: IntArray?,
        keyWidths: IntArray?,
        keyHeights: IntArray?,
        keyCharCodes: IntArray?,
        sweetSpotCenterXs: FloatArray?,
        sweetSpotCenterYs: FloatArray?,
        sweetSpotRadii: FloatArray?
    ): Long

    @JvmStatic
    external fun releaseProximityInfoNative(proximityInfo: Long)

    @JvmStatic
    external fun setDicTraverseSessionNative(locale: String, dictSize: Long): Long

    @JvmStatic
    external fun initDicTraverseSessionNative(
        session: Long,
        dict: Long,
        previousWord: IntArray?,
        previousWordLength: Int
    )

    @JvmStatic
    external fun releaseDicTraverseSessionNative(session: Long)

    // endregion

    // region Utilities

    @JvmStatic
    external fun createEmptyDictFileNative(
        filePath: String,
        dictVersion: Long,
        locale: String,
        attributeKeys: Array<String>,
        attributeValues: Array<String>
    ): Boolean

    @JvmStatic
    external fun calcNormalizedScoreNative(before: IntArray, after: IntArray, score: Int): Float

    @JvmStatic
    external fun setCurrentTimeForTestNative(currentTime: Int): Int

    // endregion
}
