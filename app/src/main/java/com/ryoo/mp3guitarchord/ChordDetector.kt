package com.ryoo.mp3guitarchord

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.pow

/**
 * Estimates basic major/minor triads from Android Visualizer FFT data.
 * FFT bins are converted to pitch-class energy, then compared with all
 * 24 major/minor templates. Non-chord energy is penalized to avoid
 * confidently naming a chord from one dominant spectral peak.
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

        // Android's packed FFT uses [DC, Nyquist] followed by real/imaginary
        // pairs for bins 1..N/2-1. Frequencies are k * Fs / N.
        for (bin in 1 until binCount) {
            val frequency = bin * binWidthHz
            if (frequency !in 55.0..1800.0) continue

            val real = fft[2 * bin].toDouble()
            val imaginary = fft[2 * bin + 1].toDouble()
            val rawMagnitude = sqrt(real * real + imaginary * imaginary)
            if (rawMagnitude < 2.0) continue
            // Compress loud spectral peaks so a single dominant note or harmonic
            // cannot drown out the other chord tones.
            val magnitude = sqrt(rawMagnitude)

            val midiValue = 69.0 + 12.0 * (ln(frequency / 440.0) / ln(2.0))
            val nearestMidi = midiValue.roundToInt()
            val semitoneDistance = abs(midiValue - nearestMidi)

            // A wider tuning window accommodates the coarse low-frequency bins
            // available from Android Visualizer's short FFT capture.
            val tuningWeight = exp(-0.5 * (semitoneDistance / 0.72) * (semitoneDistance / 0.72))
            // Keep upper harmonics useful while retaining some bass-root information.
            val frequencyWeight = 1.0 / (frequency / 110.0).coerceAtLeast(1.0).pow(0.35)
            val energy = magnitude * tuningWeight * frequencyWeight
            val pitchClass = ((nearestMidi % 12) + 12) % 12

            chroma[pitchClass] += energy
            if (frequency <= 220.0) bassChroma[pitchClass] += energy
        }

        val totalEnergy = chroma.sum()
        val maximum = chroma.maxOrNull() ?: 0.0
        if (totalEnergy <= 0.0 || maximum <= 0.0) return null

        // Ignore a spectrum dominated by a single pitch class; it cannot
        // reliably distinguish a complete triad.
        val activeNotes = chroma.count { it >= maximum * 0.16 }
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
                val inChord = rootEnergy + thirdEnergy + fifthEnergy
                val outsideChord = (totalEnergy - inChord).coerceAtLeast(0.0)

                val rootShare = rootEnergy / totalEnergy
                val thirdShare = thirdEnergy / totalEnergy
                val fifthShare = fifthEnergy / totalEnergy
                val coverage = minOf(rootShare, thirdShare, fifthShare)
                val bassRootBonus = if (bassTotal > 0.0) {
                    (bassChroma[root] / bassTotal) * 0.10
                } else {
                    0.0
                }

                // Require evidence for all three chord tones and penalize
                // unrelated notes instead of allowing one loud note to dominate.
                val score = (inChord / totalEnergy) -
                    (outsideChord / totalEnergy) * 0.55 +
                    coverage * 0.45 +
                    bassRootBonus

                if (score > bestScore) {
                    secondBestScore = bestScore
                    bestScore = score
                    bestName = noteNames[root] + quality
                } else if (score > secondBestScore) {
                    secondBestScore = score
                }
            }
        }

        val confidenceMargin = bestScore - secondBestScore
        return if (bestScore >= 0.27 && confidenceMargin >= 0.004) bestName else null
    }
}
