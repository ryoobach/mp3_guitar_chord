package com.ryoo.mp3guitarchord

import kotlin.math.*

/**
 * PCM waveform chord analyzer.
 * Combines 8192 waveform samples before analysis to obtain much finer
 * low-frequency resolution than Android Visualizer's short FFT packets.
 */
object ChordDetector {
    private val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val major = intArrayOf(0, 4, 7)
    private val minor = intArrayOf(0, 3, 7)
    private const val FFT_SIZE = 8192

    /** waveform is Android Visualizer's unsigned 8-bit PCM, sampling rate in millihertz. */
    fun detectWaveform(waveform: ByteArray, samplingRateMilliHz: Int): String? {
        if (waveform.size != FFT_SIZE || samplingRateMilliHz <= 0) return null
        val sampleRate = samplingRateMilliHz / 1000.0
        val real = DoubleArray(FFT_SIZE)
        val imag = DoubleArray(FFT_SIZE)

        // Remove DC and apply a Hann window before transforming.
        var mean = 0.0
        for (sample in waveform) mean += (sample.toInt() and 0xff) - 128
        mean /= FFT_SIZE
        for (i in 0 until FFT_SIZE) {
            val value = ((waveform[i].toInt() and 0xff) - 128) - mean
            val window = 0.5 - 0.5 * cos(2.0 * Math.PI * i / (FFT_SIZE - 1))
            real[i] = value * window
        }
        fft(real, imag)

        val binWidth = sampleRate / FFT_SIZE
        val magnitudes = DoubleArray(FFT_SIZE / 2)
        for (bin in 1 until magnitudes.size) {
            magnitudes[bin] = hypot(real[bin], imag[bin])
        }

        // Harmonic summation maps overtones back to their likely fundamental,
        // preventing one bright overtone from being mistaken for a new chord.
        val chroma = DoubleArray(12)
        val bassChroma = DoubleArray(12)
        for (midi in 31..84) {
            val fundamental = 440.0 * 2.0.pow((midi - 69) / 12.0)
            if (fundamental >= sampleRate / 2.0) continue
            var salience = 0.0
            for (harmonic in 1..5) {
                val frequency = fundamental * harmonic
                if (frequency >= sampleRate / 2.0) break
                val position = frequency / binWidth
                val center = position.roundToInt()
                if (center !in 1 until magnitudes.size - 1) continue
                val localPeak = maxOf(
                    magnitudes[center],
                    maxOf(magnitudes[center - 1], magnitudes[center + 1]) * 0.82
                )
                salience += localPeak / harmonic.toDouble().pow(1.25)
            }
            val pitchClass = ((midi % 12) + 12) % 12
            chroma[pitchClass] += salience
            if (midi <= 57) bassChroma[pitchClass] += salience
        }

        val total = chroma.sum()
        val maximum = chroma.maxOrNull() ?: 0.0
        if (total <= 0.0 || maximum <= 0.0) return null

        val normalized = DoubleArray(12) { chroma[it] / total }
        val activeTones = normalized.count { it >= 0.075 }
        if (activeTones < 3) return null

        val bassTotal = bassChroma.sum()
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
                val bassBonus = if (bassTotal > 0.0) {
                    (bassChroma[root] / bassTotal) * 0.08
                } else 0.0

                // All three chord tones must contribute; unrelated pitch classes
                // lower the score instead of allowing a single note to dominate.
                val score = chordEnergy * 0.68 + coverage * 1.65 -
                    outsideEnergy * 0.32 + bassBonus

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
        return if (bestCoverage >= 0.055 && bestScore >= 0.42 && margin >= 0.012) bestName else null
    }

    private fun fft(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while ((j and bit) != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
        }

        var length = 2
        while (length <= n) {
            val angle = -2.0 * Math.PI / length
            val wLenReal = cos(angle)
            val wLenImag = sin(angle)
            var start = 0
            while (start < n) {
                var wReal = 1.0
                var wImag = 0.0
                for (k in 0 until length / 2) {
                    val even = start + k
                    val odd = even + length / 2
                    val oddReal = real[odd] * wReal - imag[odd] * wImag
                    val oddImag = real[odd] * wImag + imag[odd] * wReal
                    real[odd] = real[even] - oddReal
                    imag[odd] = imag[even] - oddImag
                    real[even] += oddReal
                    imag[even] += oddImag
                    val nextReal = wReal * wLenReal - wImag * wLenImag
                    wImag = wReal * wLenImag + wImag * wLenReal
                    wReal = nextReal
                }
                start += length
            }
            length = length shl 1
        }
    }
}
