package com.ryoo.mp3guitarchord

import kotlin.math.*

/**
 * Chord analysis for Android Visualizer FFT packets.
 * Higher harmonics help separate guitar notes when Android's FFT resolution
 * is too coarse to distinguish low fundamentals.
 */
object ChordDetector {
    private val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val major = intArrayOf(0, 4, 7)
    private val minor = intArrayOf(0, 3, 7)

    @Volatile
    var lastDiagnostic: String = "FFT 대기 중"
        private set

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

    fun detectFft(fft: ByteArray, samplingRateMilliHz: Int): String? {
        if (fft.size < 32 || samplingRateMilliHz <= 0) {
            lastDiagnostic = "잘못된 FFT 입력: size=${fft.size}, rate=$samplingRateMilliHz"
            return null
        }

        val sampleRate = samplingRateMilliHz / 1000.0
        val binWidth = sampleRate / fft.size
        if (binWidth <= 0.0) {
            lastDiagnostic = "FFT bin 폭 오류"
            return null
        }

        fun magnitudeAt(bin: Int): Double {
            if (bin < 1 || bin >= fft.size / 2) return 0.0
            return hypot(fft[2 * bin].toDouble(), fft[2 * bin + 1].toDouble())
        }

        fun peakAt(frequency: Double): Double {
            val center = (frequency / binWidth).roundToInt()
            if (center < 1 || center >= fft.size / 2) return 0.0
            var peak = 0.0
            for (bin in (center - 1).coerceAtLeast(1)..(center + 1).coerceAtMost(fft.size / 2 - 1)) {
                val weight = if (bin == center) 1.0 else 0.72
                peak = maxOf(peak, magnitudeAt(bin) * weight)
            }
            return peak
        }

        val chroma = DoubleArray(12)
        for (midi in 36..84) {
            val fundamental = 440.0 * 2.0.pow((midi - 69) / 12.0)
            var salience = 0.0
            var harmonicWeightTotal = 0.0
            for (harmonic in 1..8) {
                val frequency = fundamental * harmonic
                if (frequency >= sampleRate / 2.0 || frequency > 4000.0) break
                val weight = 1.0 / harmonic.toDouble().pow(1.10)
                salience += peakAt(frequency) * weight
                harmonicWeightTotal += weight
            }
            if (harmonicWeightTotal > 0.0 && salience > 0.0) {
                chroma[((midi % 12) + 12) % 12] += salience / harmonicWeightTotal
            }
        }

        val total = chroma.sum()
        if (total <= 0.0) {
            lastDiagnostic = "음정 에너지 없음: bin=${"%.1f".format(binWidth)}Hz"
            return null
        }

        val normalized = DoubleArray(12) { chroma[it] / total }
        var bestName: String? = null
        var bestScore = Double.NEGATIVE_INFINITY
        var secondScore = Double.NEGATIVE_INFINITY
        var bestCoverage = 0.0
        var bestChordEnergy = 0.0

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
                    bestChordEnergy = chordEnergy
                } else if (score > secondScore) {
                    secondScore = score
                }
            }
        }

        val margin = bestScore - secondScore
        val topNotes = normalized.indices.sortedByDescending { normalized[it] }.take(4)
            .joinToString(",") { "${noteNames[it]}:${"%.2f".format(normalized[it])}" }
        lastDiagnostic = "bin=${"%.1f".format(binWidth)}Hz top=$topNotes best=$bestName " +
            "score=${"%.3f".format(bestScore)} coverage=${"%.3f".format(bestCoverage)} " +
            "energy=${"%.3f".format(bestChordEnergy)} margin=${"%.3f".format(margin)}"

        // Android Visualizer FFT has limited low-frequency resolution. The previous\n        // strict gates rejected many genuine chords when one chord tone was weak.\n        // Relax confidence gates modestly; MainActivity still confirms candidates\n        // across multiple FFT frames before displaying a chord.\n        return if (bestCoverage >= 0.004 && bestScore >= 0.16 && margin >= 0.00035) bestName else null
    }
}