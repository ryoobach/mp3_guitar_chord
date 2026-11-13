package com.ryoo.mp3guitarchord

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Estimates a basic major/minor chord from Android Visualizer's FFT spectrum.
 *
 * The spectrum is converted to pitch-class energy using the actual FFT bin
 * width. Frequency weighting reduces the tendency of upper harmonics to
 * dominate the chord decision, while bass-note energy helps identify the root.
 */
object ChordDetector {
    private val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val major = intArrayOf(0, 4, 7)
    private val minor = intArrayOf(0, 3, 7)

    /** samplingRate is supplied by Android Visualizer in millihertz. */
    fun detect(fft: ByteArray, samplingRateMilliHz: Int): String? {
        if (fft.size < 32 || samplingRateMilliHz <= 0) return null

        val fftSize = fft.size
        val binCount = fftSize / 2
        val sampleRateHz = samplingRateMilliHz / 1000.0
        val binWidthHz = sampleRateHz / fftSize
        if (binWidthHz <= 0.0) return null

        val chroma = DoubleArray(12)
        val bassChroma = DoubleArray(12)

        // Android packs the real and imaginary values for bins 1..N/2-1
        // into alternating bytes. Bin frequency is k * Fs / N.
        for (bin in 1 until binCount) {
            val frequency = bin * binWidthHz
            if (frequency !in 45.0..1400.0) continue

            val real = fft[2 * bin].toDouble()
            val imaginary = fft[2 * bin + 1].toDouble()
            val magnitude = sqrt(real * real + imaginary * imaginary)
            if (magnitude < 2.0) continue

            val midiValue = 69.0 + 12.0 * (ln(frequency / 440.0) / ln(2.0))
            val nearestMidi = midiValue.roundToInt()
            val semitoneDistance = abs(midiValue - nearestMidi)

            // Keep some tolerance for FFT resolution and slightly detuned audio.
            val tuningWeight = exp(-0.5 * (semitoneDistance / 0.48) * (semitoneDistance / 0.48))
            val frequencyWeight = 1.0 / sqrt(maxOf(1.0, frequency / 110.0))
            val energy = magnitude * tuningWeight * frequencyWeight
            val pitchClass = ((nearestMidi % 12) + 12) % 12

            chroma[pitchClass] += energy
            if (frequency <= 220.0) bassChroma[pitchClass] += energy
        }

        val totalEnergy = chroma.sum()
        val maximum = chroma.maxOrNull() ?: 0.0
        if (totalEnergy <= 0.0 || maximum <= 0.0) return null

        val activeNotes = chroma.count { it >= maximum * 0.12 }
        if (activeNotes < 2) return null

        val bassTotal = bassChroma.sum()
        var bestName: String? = null
        var bestScore = Double.NEGATIVE_INFINITY
        var secondBestScore = Double.NEGATIVE_INFINITY

        for (root in 0 until 12) {
            for ((quality, intervals) in arrayOf("" to major, "m" to minor)) {
                val third = (root + intervals[1]) % 12
                val fifth = (root + intervals[2]) % 12
                val rootEnergy = chroma[root]
                val thirdEnergy = chroma[third]
                val fifthEnergy = chroma[fifth]
                val chordEnergy = rootEnergy * 1.15 + thirdEnergy + fifthEnergy * 0.90
                val nonChordEnergy = (totalEnergy - rootEnergy - thirdEnergy - fifthEnergy)
                    .coerceAtLeast(0.0)
                val bassRootBonus = if (bassTotal > 0.0) {
                    (bassChroma[root] / bassTotal) * 0.12
                } else {
                    0.0
                }
                val missingPenalty =
                    (if (rootEnergy < maximum * 0.10) 0.07 else 0.0) +
                    (if (thirdEnergy < maximum * 0.10) 0.07 else 0.0) +
                    (if (fifthEnergy < maximum * 0.10) 0.05 else 0.0)

                val score = chordEnergy / totalEnergy -
                    (nonChordEnergy / totalEnergy) * 0.20 +
                    bassRootBonus - missingPenalty

                if (score > bestScore) {
                    secondBestScore = bestScore
                    bestScore = score
                    bestName = noteNames[root] + quality
                } else if (score > secondBestScore) {
                    secondBestScore = score
                }
            }
        }

        // Avoid displaying a random chord when the spectrum does not contain
        // enough evidence or two candidates are almost equally plausible.
        val confidenceMargin = bestScore - secondBestScore
        return if (bestScore >= 0.30 && confidenceMargin >= 0.006) bestName else null
    }
}
