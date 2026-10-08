// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.engine

/**
 * [NativeBridge] over the real JNI. Only array plumbing lives here; decisions belong to
 * [AospSuggestionEngine]. Covered by on-device tests, not by JVM unit tests.
 */
internal object JniNativeBridge : NativeBridge {
    private const val MAX_WORD_LENGTH = 48
    private const val MAX_RESULTS = 18
    private const val MAX_PREV_WORDS = 3
    private const val NOT_A_COORDINATE = -1
    private const val BEGINNING_OF_SENTENCE_CODE_POINT = 0x110000
    private const val FORMAT_VERSION_4 = 403L
    private const val OPTION_BLOCK_OFFENSIVE = 2
    private const val OPTION_WEIGHT_FOR_LOCALE = 4
    private const val OPTIONS_SIZE = 5
    private const val FULL_LOCALE_WEIGHT_IN_THOUSANDS = 1000
    private const val NOT_A_TOKEN = 0

    override fun openDictionary(
        path: String,
        offset: Long,
        length: Long,
        updatable: Boolean
    ): Long = NativeEngine.openNative(path, offset, length, updatable)

    override fun createEmptyDictionary(
        path: String,
        locale: String,
        attributes: Map<String, String>
    ): Boolean = NativeEngine.createEmptyDictFileNative(
        path,
        FORMAT_VERSION_4,
        locale,
        attributes.keys.toTypedArray(),
        attributes.values.toTypedArray()
    )

    override fun closeDictionary(dictionary: Long) = NativeEngine.closeNative(dictionary)

    override fun newSession(locale: String, dictionarySize: Long): Long =
        NativeEngine.setDicTraverseSessionNative(locale, dictionarySize)

    override fun releaseSession(session: Long) =
        NativeEngine.releaseDicTraverseSessionNative(session)

    override fun newProximityInfo(geometry: KeyboardGeometry): Long {
        val keys = geometry.keys
        return NativeEngine.setProximityInfoNative(
            displayWidth = geometry.width,
            displayHeight = geometry.height,
            gridWidth = KeyboardGeometry.GRID_WIDTH,
            gridHeight = KeyboardGeometry.GRID_HEIGHT,
            mostCommonKeyWidth = geometry.mostCommonKeyWidth,
            mostCommonKeyHeight = geometry.mostCommonKeyHeight,
            proximityChars = geometry.proximityChars(),
            keyCount = keys.size,
            keyXCoordinates = IntArray(keys.size) { keys[it].x },
            keyYCoordinates = IntArray(keys.size) { keys[it].y },
            keyWidths = IntArray(keys.size) { keys[it].width },
            keyHeights = IntArray(keys.size) { keys[it].height },
            keyCharCodes = IntArray(keys.size) { keys[it].codePoint },
            // No touch-position correction data: the engine then ignores the sweet spots.
            sweetSpotCenterXs = null,
            sweetSpotCenterYs = null,
            sweetSpotRadii = null
        )
    }

    override fun releaseProximityInfo(proximityInfo: Long) =
        NativeEngine.releaseProximityInfoNative(proximityInfo)

    @Suppress("LongMethod")
    override fun suggest(
        dictionary: Long,
        session: Long,
        proximityInfo: Long,
        composing: String,
        context: List<String>
    ): List<RawSuggestion> {
        val input = composing.codePoints().toArray()
        if (input.size > MAX_WORD_LENGTH) return emptyList()
        val (prevWords, prevIsBeginning) = encodeContext(context)
        val options = IntArray(OPTIONS_SIZE).also {
            it[OPTION_BLOCK_OFFENSIVE] = 1
            it[OPTION_WEIGHT_FOR_LOCALE] = FULL_LOCALE_WEIGHT_IN_THOUSANDS
        }
        val count = IntArray(1)
        val codePoints = IntArray(MAX_WORD_LENGTH * MAX_RESULTS)
        val scores = IntArray(MAX_RESULTS)
        val kinds = IntArray(MAX_RESULTS)
        NativeEngine.getSuggestionsNative(
            dict = dictionary,
            proximityInfo = proximityInfo,
            traverseSession = session,
            xCoordinates = IntArray(input.size) { NOT_A_COORDINATE },
            yCoordinates = IntArray(input.size) { NOT_A_COORDINATE },
            times = IntArray(input.size),
            pointerIds = IntArray(input.size),
            inputCodePoints = input,
            inputSize = input.size,
            suggestOptions = options,
            prevWordCodePointArrays = prevWords,
            isBeginningOfSentenceArray = prevIsBeginning,
            prevWordCount = prevWords.size,
            outSuggestionCount = count,
            outCodePoints = codePoints,
            outScores = scores,
            outSpaceIndices = IntArray(MAX_RESULTS),
            outTypes = kinds,
            outAutoCommitFirstWordConfidence = IntArray(1),
            inOutWeightOfLangModelVsSpatialModel = floatArrayOf(-1f)
        )
        return List(count[0].coerceIn(0, MAX_RESULTS)) { index ->
            RawSuggestion(decode(codePoints, index * MAX_WORD_LENGTH), scores[index], kinds[index])
        }
    }

    override fun probability(dictionary: Long, word: String): Int =
        NativeEngine.getProbabilityNative(dictionary, word.codePoints().toArray())

    override fun learnWord(
        dictionary: Long,
        context: List<String>,
        word: String,
        isValidWord: Boolean,
        timestampSeconds: Int
    ): Boolean {
        val (prevWords, prevIsBeginning) = encodeContext(context)
        return NativeEngine.updateEntriesForWordWithNgramContextNative(
            dict = dictionary,
            prevWordCodePointArrays = prevWords,
            isBeginningOfSentenceArray = prevIsBeginning,
            word = word.codePoints().toArray(),
            isValidWord = isValidWord,
            count = 1,
            timestamp = timestampSeconds
        )
    }

    override fun addUnigram(
        dictionary: Long,
        word: String,
        probability: Int,
        timestampSeconds: Int
    ): Boolean = NativeEngine.addUnigramEntryNative(
        dict = dictionary,
        word = word.codePoints().toArray(),
        probability = probability,
        shortcutTarget = null,
        shortcutProbability = 0,
        isBeginningOfSentence = false,
        isNotAWord = false,
        isPossiblyOffensive = false,
        timestamp = timestampSeconds
    )

    override fun removeUnigram(dictionary: Long, word: String): Boolean =
        NativeEngine.removeUnigramEntryNative(dictionary, word.codePoints().toArray())

    override fun words(dictionary: Long): List<String> {
        val words = mutableListOf<String>()
        val buffer = IntArray(MAX_WORD_LENGTH)
        val isBeginning = BooleanArray(1)
        var token = NOT_A_TOKEN
        // The word reported by the call that returns the last token is valid too.
        do {
            buffer.fill(0)
            token = NativeEngine.getNextWordNative(dictionary, token, buffer, isBeginning)
            val word = decode(buffer, 0)
            if (!isBeginning[0] && word.isNotEmpty()) words += word
        } while (token != NOT_A_TOKEN)
        return words
    }

    override fun flush(dictionary: Long, path: String): Boolean =
        if (NativeEngine.needsToRunGCNative(dictionary, false)) {
            NativeEngine.flushWithGCNative(dictionary, path)
        } else {
            NativeEngine.flushNative(dictionary, path)
        }

    override fun compactIfNeeded(dictionary: Long, path: String): Boolean =
        !NativeEngine.needsToRunGCNative(dictionary, true) ||
            NativeEngine.flushWithGCNative(dictionary, path)

    override fun normalizedScore(typed: String, candidate: String, score: Int): Float =
        NativeEngine.calcNormalizedScoreNative(
            typed.codePoints().toArray(),
            candidate.codePoints().toArray(),
            score
        )

    /** Most recent word first, at most [MAX_PREV_WORDS]; an empty context means start of sentence. */
    private fun encodeContext(context: List<String>): Pair<Array<IntArray>, BooleanArray> {
        val recent = context.filter { it.isNotEmpty() }.takeLast(MAX_PREV_WORDS).asReversed()
        if (recent.isEmpty()) {
            return arrayOf(intArrayOf(BEGINNING_OF_SENTENCE_CODE_POINT)) to booleanArrayOf(true)
        }
        return Array(recent.size) { recent[it].codePoints().toArray() } to BooleanArray(recent.size)
    }

    private fun decode(codePoints: IntArray, start: Int): String {
        var end = start
        while (end < codePoints.size && end - start < MAX_WORD_LENGTH && codePoints[end] != 0) end++
        return String(codePoints, start, end - start)
    }
}
