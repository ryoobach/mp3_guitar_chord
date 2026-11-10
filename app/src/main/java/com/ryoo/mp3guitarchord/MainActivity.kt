
package com.ryoo.mp3guitarchord

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

class MainActivity : Activity() {

    private val backgroundColor = Color.rgb(20, 22, 26)
    private val greenColor = Color.rgb(0, 255, 120)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 24, 18, 18)
            setBackgroundColor(backgroundColor)
        }

        val title = TextView(this).apply {
            text = "MP3 기타 코드"
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        root.addView(title)

        val time = TextView(this).apply {
            text = "00:00"
            textSize = 32f
            setTextColor(greenColor)
            gravity = Gravity.CENTER
            setPadding(0, 30, 0, 10)
        }
        root.addView(time)

        val chords = TextView(this).apply {
            text = "  C     G     Am     F     C     G  "
            textSize = 25f
            setTextColor(greenColor)
            gravity = Gravity.CENTER
            setSingleLine(true)
            setPadding(8, 18, 8, 18)
            setBackgroundColor(Color.rgb(5, 10, 8))
        }
        root.addView(chords)

        val progress = SeekBar(this)
        root.addView(progress)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 24)
        }

        val previous = Button(this).apply {
            text = "|◀"
        }

        val play = Button(this).apply {
            text = "▶"
        }

        val next = Button(this).apply {
            text = "▶|"
        }

        controls.addView(previous)
        controls.addView(play)
        controls.addView(next)

        root.addView(controls)

        val playlistTitle = TextView(this).apply {
            text = "PLAYLIST"
            textSize = 16f
            setTextColor(greenColor)
            setPadding(4, 18, 4, 12)
        }
        root.addView(playlistTitle)

        val playlist = TextView(this).apply {
            text = "MP3 파일을 선택해 주세요."
            textSize = 15f
            setTextColor(Color.LTGRAY)
            setPadding(12, 20, 12, 20)
            setBackgroundColor(Color.rgb(30, 32, 36))
        }
        root.addView(playlist)

        val volumeTitle = TextView(this).apply {
            text = "VOLUME"
            setTextColor(Color.LTGRAY)
            setPadding(4, 20, 4, 4)
        }
        root.addView(volumeTitle)

        // 볼륨 조절 SeekBar
        val volume = SeekBar(this)
        volume.max = 100
        volume.progress = 70
        root.addView(volume)

        setContentView(root)
    }
}
