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

    /**
     * Detects chords directly from Android Visualizer's FFT packets.
     * FFT bytes are signed real/imaginary pairs; samplingRateMilliHz is
     * converted to Hz and bins are mapped to musical pitch classes.
     */
    fun detectFft(fft: ByteArray, samplingRateMilliHz: Int): String? {
        if (fft.size < 32 || samplingRateMilliHz <= 0) return null
        val sampleRate = samplingRateMilliHz / 1000.0
        val binWidth = sampleRate / fft.size
        if (binWidth <= 0.0) return null

        val chroma = DoubleArray(12)
        for (bin in 1 until fft.size / 2) {
            val real = fft[2 * bin].toDouble()
            val imaginary = fft[2 * bin + 1].toDouble()
            val magnitude = hypot(real, imaginary)
            if (magnitude <= 0.0) continue
            val frequency = bin * binWidth
            if (frequency !in 45.0..1400.0) continue
            val midi = (69.0 + 12.0 * ln(frequency / 440.0) / ln(2.0)).roundToInt()
            val noteFrequency = 440.0 * 2.0.pow((midi - 69) / 12.0)
            val cents = abs(1200.0 * ln(frequency / noteFrequency) / ln(2.0))
            if (cents > 65.0) continue
            val pitchClass = ((midi % 12) + 12) % 12
            chroma[pitchClass] += magnitude / sqrt(maxOf(1.0, frequency / 110.0))
        }

        val total = chroma.sum()
        if (total < 1.0) return null
        val normalized = DoubleArray(12) { chroma[it] / total }
        var bestName: String? = null
        var bestScore = Double.NEGATIVE_INFINITY
        var secondScore = Double.NEGATIVE_INFINITY

        for (root in 0 until 12) {
            for ((quality, intervals) in arrayOf("" to major, "m" to minor)) {
                val rootEnergy = normalized[root]
                val thirdEnergy = normalized[(root + intervals[1]) % 12]
                val fifthEnergy = normalized[(root + intervals[2]) % 12]
                val chordEnergy = rootEnergy + thirdEnergy + fifthEnergy
                val coverage = minOf(rootEnergy, thirdEnergy, fifthEnergy)
                val outsideEnergy = (1.0 - chordEnergy).coerceAtLeast(0.0)
                val score = chordEnergy * 0.72 + coverage * 1.1 - outsideEnergy * 0.18
                if (score > bestScore) {
                    secondScore = bestScore
                    bestScore = score
                    bestName = noteNames[root] + quality
                } else if (score > secondScore) {
                    secondScore = score
                }
            }
        }
        return if (bestScore >= 0.28 && bestScore - secondScore >= 0.001) bestName else null
    }

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
        // Mixed recordings often contain a dominant vocal or instrument.
        // Do not reject a frame just because all three chord tones are not
        // equally strong; the template scorer below evaluates the full spectrum.

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
        // The previous confidence gate was too strict for real-world mixes:
        // vocals, bass, compression and radio processing can mask one chord tone.
        // Keep only a very small floor to suppress near-silence, then let the
        // rolling vote in MainActivity stabilize the result over multiple frames.
        return if (bestCoverage >= 0.012 && bestScore >= 0.20 && margin >= 0.002) bestName else null
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
