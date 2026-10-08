<!--
SPDX-FileCopyrightText: 2026 UltimateKeys contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Gesture typing: design note

Plan task 10.1. SPEC section 5 asks for word-gesture typing ("glide typing") implemented by us, as the
`:gesture` module, with SHARK2-style template matching combined with the engine's language model. This note
says what the algorithm is, why, what it costs, and where it is deliberately simpler than the literature.
The code is original; the papers below were read for their ideas only.

## 1. What the user sees

1. A finger lands on a letter key and moves. Until it has travelled about one key width it is still an
   ordinary key press (the old "slide to the neighbouring key" correction keeps working). Past that distance
   the touch is a gesture: the key preview and long-press are cancelled, and a trail in the style's
   `gestureTrail` colour follows the finger (it thins and fades towards its tail).
2. On lift, the path is decoded off the main thread. The best word is typed followed by a space (a space is
   also put before it when it would stick to the previous word). The strip shows the second and third
   candidates on both sides of the typed word; tapping one swaps the word in place.
3. Backspace right after a gesture removes the whole word with its spacing. Punctuation typed right after
   swallows the automatic space, like after a picked suggestion.
4. A "gesture" shorter than 1.2 key widths is treated as the key press it really was.

## 2. The decoder

### 2.1 Templates

A word has an *ideal path*: the polyline through the centres of the letter keys of its letters on the layout
that is on screen. Letters are mapped to keys by their base letter (accents dropped: `está` is traced as
`esta`; apostrophes skipped: `don't` as `dont`; `ñ` and `ç` keep their own keys); repeated letters collapse
because a finger cannot dwell on a key to type `ll`. Words that cannot be traced (digits, symbols, a letter
this layout has no key for) are not candidates. Because templates come from the *current* key rectangles,
any layout, key height, row scale or number row works without retraining. Templates are never stored: each
one is rebuilt from a precomputed key sequence when needed.

### 2.2 Two channels (SHARK2)

SHARK2 (Kristensson and Zhai 2004) recognises a word gesture by comparing it with every word's template
through two complementary channels, after both are resampled to N equidistant points (the same preprocessing
step as the $1 recogniser, Wobbrock et al. 2007):

* **Shape channel.** Both paths are translated to their centroid and scaled so that the larger side of the
  bounding box is 1 (never less than one key, so a tiny path is not blown up). The cost is the mean distance
  between corresponding points. It tells words apart by their form and ignores where and how large the
  gesture was drawn.
* **Location channel.** The raw paths are compared. A point within a tolerance of 0.3 key of the template
  point costs nothing (it is on the key); farther points cost their excess distance. The cost is the mean
  excess in key units. It tells apart words with the same form at different places, and punishes a gesture
  that wanders off the keys.

Each channel is divided by a sigma (shape 0.06, location 0.30 key) so that their sum is a cost in comparable
units: the log-likelihood of a Laplace error model. The Gaussian (squared) variant of the paper was tried in the
offline harness and was never better. N is 32.

### 2.3 Language model

The cost of a candidate is `shape + location + frequency + language`, minus a bonus if the engine expects the
word next:

* **Unigram frequency.** The vocabulary comes from the same word lists that build the suggestion engine's
  dictionaries (`:dictionaries`, `WordListParser`), so the 0..255 frequency of a word is the engine's unigram
  score (the scale is logarithmic). Cost `frequencyWeight * (255 - f) / 255`, weight 6. Words with frequency
  below 30 (about a third of the lists) and offensive words are left out, which keeps memory and work down
  without touching the words people glide.
* **Mixed Spanish/English weighting.** `LanguagePrior` re-implements, on the vocabulary, the model of
  `MixedSuggestionEngine`: the last four words are evidence for the languages that accept them (words valid in
  only one language count fully, words valid in both say nothing), recent words count more, and the evidence
  is shrunk towards a prior that favours the primary language (0.75). The cost of a word is
  `languageWeight * ln(bestWeight / weight(language))`, weight 0.35. With no context the primary language wins
  ties; after a few English words English words come first, with no switch to press.
* **Context.** The words the engine predicts after the previous words (`predictNext`) get a bonus of up to 3
  cost units, so learned bigrams and the person's habits matter.
* **The person's own words.** `userDictionaryWords` are added as candidates with a high fixed frequency, so
  words that are in no shipped list can be glided.

### 2.4 Candidate generation and pruning

Scoring all 255,000 words would be too slow, so the work is cut in this order (cheapest test first):

1. **End keys.** Words are indexed by (first key, last key), 28 x 28 buckets. Only buckets whose first key is
   within 0.85 key of the first touch point and whose last key is within 0.85 key of the last point are
   visited: typically 3 to 5 keys on each side. If nothing is found at all, the search is repeated once with a
   1.7 times wider radius (a finger that landed far from its key).
2. **Path length.** The ideal length of the word (a table lookup of key to key distances) must be within
   0.5 to 1.9 times the length of the gesture.
3. **Frequency order and early abandon.** Inside a bucket words are sorted by frequency, so the best
   candidates are scored first and fill a shortlist of 40. A word whose language and frequency cost alone is
   not below the shortlist's worst cost is dropped without geometry, and the sums of both channels stop as
   soon as they exceed what the word may still spend.
4. **Re-ranking.** The shortlist is re-ranked with the engine's next-word bonus, deduplicated ignoring case
   (a word in both languages is listed once), and cut to five candidates.

### 2.5 Complexity budget

Let `V` be the vocabulary size, `B` the words in the visited buckets, `S <= B` those that pass the length
test, `N` the resample size and `K` the keys of a word.

| Step | Cost per gesture |
|---|---|
| Resample and normalise the gesture | O(P + N), P = captured points (a few hundred) |
| Bucket visits | O(B) cheap tests (frequency cost, key distance table) |
| Scoring a surviving word | O(K + N): polyline, resample to N points, two channel sums with early exit |
| Total | O(P + B + S (K + N)) |

Measured over the shipped vocabulary (V = 255,127 words, both languages): `B` is about 4,500 and `S` about
3,300 words per gesture; the budget in `docs/gesture/RESULTS.md` is p95 3.6 ms of CPU on the development
machine against the requirement of 50 ms. Memory: words are one `CharArray`, key sequences one `ByteArray`,
with offsets, frequencies and languages in primitive arrays (about 10 MB for V = 255,000, no object per
word). The vocabulary is built once in the background after the dictionaries are installed (a second or two)
and shared by all gestures; the decoder itself is stateless.

## 3. Evaluation

A synthetic-gesture harness (`gesture/harness`) makes the path of a finger gliding over a word: the key
centres, each missed by a random amount, a hand offset, rounded corners (Chaikin 1974 corner cutting), then
sampled unevenly like a touch screen. Three named noise profiles (careful, typical, sloppy) are reported.
Results and their limits are in `docs/gesture/RESULTS.md`. Synthetic gestures measure the decoder, not
people: the noise model is an assumption, so the real feel is a human check (plan task 10.8,
`docs/HUMAN_VERIFICATION.md`).

Tuning (sigmas, frequency and language weights, end radius, N) used every eighth word of the 5,000 most
common ones with a different random seed than the reported runs.

## 4. Differences from SHARK2 and what is not done

* SHARK2 weighs the location channel by a per-point factor and prunes with it first; here the two channels
  are plain means with early abandon, and pruning uses the end keys and the length (simpler, and faster on
  a large vocabulary).
* No dwell or speed features (the paper's and later work's corner detection): every point of the path counts
  equally. Slowing down on a letter is a strong signal that real devices could use; it is the first thing
  to try if the feel check shows short words missing.
* The language model is a unigram with a next-word bonus, not a full n-gram over the candidate lattice.
  Candidates are single words: a gesture is one word, spaces are automatic.
* No live preview while the finger is down, no per-person adaptation of the sigmas.

## 5. Where it lives

| Piece | Module |
|---|---|
| Alphabet, keyboard geometry, path math, capture, vocabulary, language prior, decoder, harness | `:gesture` (pure Kotlin, no Android, no other module) |
| Vocabulary from the word lists, keyboard from `KeyGeometry`, engine feed | `:ime` (`gesture/GestureSupport`, `gesture/GestureTyping`) |
| Touch capture, trail, publishing | `SurfaceGestures`, `SurfaceRenderer` |
| Commit, swap, undo, learning | `InputLogic` (`commitGestureWord`, `replaceGestureWord`), `SuggestionController` |
| Colour of the trail | `:style` palette field `gestureTrail` (schema version 2) |
| Enable, trail, sensitivity | `KeyboardSettings`, settings screen |

Privacy: a gesture word is only handed to the engine's `learn` when it stays (the next edit after it, not
when it is swapped or taken back), and that goes through the same `learningAllowed` and `PrivacyGuardedEngine`
path as typed words, so nothing is learned while private mode is on.

## 6. References

1. S. Zhai and P.-O. Kristensson. *Shorthand writing on stylus keyboard.* Proc. CHI 2003, 97-104.
2. P.-O. Kristensson and S. Zhai. *SHARK2: a large vocabulary shorthand writing system for pen-based
   computers.* Proc. UIST 2004, 43-52.
3. P.-O. Kristensson and S. Zhai. *Relaxing stylus typing precision by geometric pattern matching.* Proc. IUI
   2005, 151-158.
4. S. Zhai and P.-O. Kristensson. *The word-gesture keyboard: reimagining keyboard interaction.*
   Communications of the ACM 55(9), 2012, 91-101.
5. J. O. Wobbrock, A. D. Wilson and Y. Li. *Gestures without libraries, toolkits or training: a $1 recognizer
   for user interface prototypes.* Proc. UIST 2007, 159-168.
6. G. M. Chaikin. *An algorithm for high-speed curve generation.* Computer Graphics and Image Processing
   3(4), 1974, 346-349 (used to round the corners of synthetic gestures).
