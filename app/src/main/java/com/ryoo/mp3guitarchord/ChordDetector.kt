package com.ryoo.mp3guitarchord

import kotlin.math.*

/**
 * Chord analysis for Android Visualizer FFT packets.
 * Uses harmonic summation around each note instead of discarding FFT bins
 * that do not land within a narrow cents window. This is more tolerant of
 * the coarse frequency resolution of Android's short Visualizer captures.
 */
object ChordDetector {
    private val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val major = intArrayOf(0, 4, 7)
    private val minor = intArrayOf(0, 3, 7)

    fun hasAudioSignal(fft: ByteArray): Boolean {
        if (fft.size < 16) return false
        var total = 0.0
        var peak = 0.0
        for (bin in 1 until fft.size / 2) {
            val real = fft[2 * bin].toDouble()
            val imaginary = fft[2 * bin + 1].toDouble()
            val magnitude = hypot(real, imaginary)
            total += magnitude
            peak = maxOf(peak, magnitude)
        }
        return peak >= 5.0 && total >= 65.0
    }

    /**
     * Returns a major/minor triad only when at least three pitch classes
     * contribute meaningful energy. Multiple nearby FFT bins are sampled
     * around each harmonic to reduce note-bin quantization errors.
     */
    fun detectFft(fft: ByteArray, samplingRateMilliHz: Int): String? {
        if (fft.size < 32 || samplingRateMilliHz <= 0) return null
        val sampleRate = samplingRateMilliHz / 1000.0
        val binWidth = sampleRate / fft.size
        if (binWidth <= 0.0) return null

        fun peakAt(frequency: Double): Double {
            val center = (frequency / binWidth).roundToInt()
            if (center < 1 || center >= fft.size / 2 - 1) return 0.0
            var peak = 0.0
            for (bin in (center - 1).coerceAtLeast(1)..(center + 1).coerceAtMost(fft.size / 2 - 1)) {
                val real = fft[2 * bin].toDouble()
                val imaginary = fft[2 * bin + 1].toDouble()
                peak = maxOf(peak, hypot(real, imaginary))
            }
            return peak
        }

        val chroma = DoubleArray(12)
        for (midi in 36..84) {
            val fundamental = 440.0 * 2.0.pow((midi - 69) / 12.0)
            var salience = 0.0
            for (harmonic in 1..4) {
                val frequency = fundamental * harmonic
                if (frequency >= sampleRate / 2.0 || frequency > 1800.0) break
                salience += peakAt(frequency) / harmonic.toDouble().pow(1.25)
            }
            if (salience > 0.0) {
                val pitchClass = ((midi % 12) + 12) % 12
                chroma[pitchClass] += salience
            }
        }

        val total = chroma.sum()
        if (total <= 0.0) return null
        val normalized = DoubleArray(12) { chroma[it] / total }

        var bestName: String? = null
        var bestScore = Double.NEGATIVE_INFINITY
        var secondScore = Double.NEGATIVE_INFINITY
        var bestCoverage = 0.0

        for (root in 0 until 12) {
            for ((quality, intervals) in arrayOf("" to major, "m" to minor)) {
                val rootEnergy = normalized[root]
                val thirdEnergy = normalized[(root + intervals[1]) % 12]
                val fifthEnergy = normalized[(root + intervals[2]) % 12]
                val coverage = minOf(rootEnergy, thirdEnergy, fifthEnergy)
                val chordEnergy = rootEnergy + thirdEnergy + fifthEnergy
                val outsideEnergy = (1.0 - chordEnergy).coerceAtLeast(0.0)
                val score = chordEnergy * 0.76 + coverage * 1.55 - outsideEnergy * 0.24

                if (score > bestScore) {
                    secondScore = bestScore
                    bestScore = score
                    bestName = noteNames[root] + quality
                    bestCoverage = coverage
                } else if (score > secondScore) {
                    secondScore = score
                }
            }
        }

        val margin = bestScore - secondScore
        // Keep the acceptance gate permissive enough for compressed mixes,
        // radio processing and vocal-heavy recordings. The rolling vote in
        // MainActivity handles short-lived false candidates.
        return if (bestCoverage >= 0.012 && bestScore >= 0.20 && margin >= 0.002) bestName else null
    }
}
