package com.ryoo.mp3guitarchord

import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Estimates a basic major/minor chord from the playing audio's FFT spectrum. */
object ChordDetector {
    private val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val major = intArrayOf(0, 4, 7)
    private val minor = intArrayOf(0, 3, 7)

    /** samplingRate is supplied by Android Visualizer in millihertz. */
    fun detect(fft: ByteArray, samplingRateMilliHz: Int): String? {
        if (fft.size < 16 || samplingRateMilliHz <= 0) return null
        val binCount = fft.size / 2
        val sampleRateHz = samplingRateMilliHz / 1000.0
        val nyquist = sampleRateHz / 2.0
        val chroma = DoubleArray(12)

        for (bin in 1 until binCount) {
            val frequency = bin * nyquist / (binCount - 1)
            if (frequency !in 75.0..1400.0) continue
            val real = fft[2 * bin].toDouble()
            val imaginary = fft[2 * bin + 1].toDouble()
            val magnitude = sqrt(real * real + imaginary * imaginary)
            if (magnitude < 2.0) continue
            val midi = (69.0 + 12.0 * (ln(frequency / 440.0) / ln(2.0))).roundToInt()
            val pitchClass = ((midi % 12) + 12) % 12
            chroma[pitchClass] += magnitude
        }

        val total = chroma.sum()
        val maximum = chroma.maxOrNull() ?: 0.0
        if (total <= 0.0 || maximum <= 0.0) return null
        val activeNotes = chroma.count { it >= maximum * 0.16 }
        if (activeNotes < 2) return null

        var bestName: String? = null
        var bestScore = 0.0
        for (root in 0 until 12) {
            for ((quality, intervals) in arrayOf("" to major, "m" to minor)) {
                val chordNotes = intervals.map { (root + it) % 12 }
                val chordEnergy = chordNotes.sumOf { chroma[it] }
                val missingPenalty = chordNotes.count { chroma[it] < maximum * 0.10 } * 0.10
                val score = chordEnergy / total - missingPenalty
                if (score > bestScore) {
                    bestScore = score
                    bestName = noteNames[root] + quality
                }
            }
        }
        return if (bestScore >= 0.32) bestName else null
    }
}
