
package com.ryoo.mp3guitarchord

import android.app.Activity
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.graphics.Color

class MainActivity : Activity() {

    private val backgroundColor = Color.rgb(20, 22, 26)
    private val greenColor = Color.rgb(0, 255, 120)

    private var mediaPlayer: MediaPlayer? = null
    private var selectedUri: Uri? = null

    private lateinit var timeText: TextView
    private lateinit var playlistText: TextView
    private lateinit var playButton: Button
    private lateinit var progressBar: SeekBar
    private lateinit var volumeBar: SeekBar

    private val handler = Handler(Looper.getMainLooper())

    private val updateProgress = object : Runnable {
        override fun run() {
            val player = mediaPlayer

            if (player != null && player.isPlaying) {
                val position = player.currentPosition
                val duration = player.duration

                timeText.text = formatTime(position)

                if (duration > 0) {
                    progressBar.progress =
                        (position * 1000 / duration)
                }
            }

            handler.postDelayed(this, 500)
        }
    }

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

        timeText = TextView(this).apply {
            text = "00:00"
            textSize = 32f
            setTextColor(greenColor)
            gravity = Gravity.CENTER
            setPadding(0, 30, 0, 10)
        }
        root.addView(timeText)

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

        progressBar = SeekBar(this)
        progressBar.max = 1000
        root.addView(progressBar)

        progressBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    if (fromUser) {
                        val player = mediaPlayer
                        if (player != null && player.duration > 0) {
                            val newPosition =
                                progress * player.duration / 1000
                            player.seekTo(newPosition)
                            timeText.text = formatTime(newPosition)
                        }
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            }
        )

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 24)
        }

        val previous = Button(this).apply {
            text = "|◀"
            setOnClickListener {
                Toast.makeText(
                    this@MainActivity,
                    "재생목록 기능은 다음 단계에서 추가합니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        playButton = Button(this).apply {
            text = "▶"
            setOnClickListener {
                togglePlayback()
            }
        }

        val next = Button(this).apply {
            text = "▶|"
            setOnClickListener {
                Toast.makeText(
                    this@MainActivity,
                    "재생목록 기능은 다음 단계에서 추가합니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        controls.addView(previous)
        controls.addView(playButton)
        controls.addView(next)

        root.addView(controls)

        val playlistTitle = TextView(this).apply {
            text = "PLAYLIST"
            textSize = 16f
            setTextColor(greenColor)
            setPadding(4, 18, 4, 12)
        }
        root.addView(playlistTitle)

        playlistText = TextView(this).apply {
            text = "MP3 파일을 선택해 주세요."
            textSize = 15f
            setTextColor(Color.LTGRAY)
            setPadding(12, 20, 12, 20)
            setBackgroundColor(Color.rgb(30, 32, 36))
            setOnClickListener {
                openFilePicker()
            }
        }
        root.addView(playlistText)

        val volumeTitle = TextView(this).apply {
            text = "VOLUME"
            setTextColor(Color.LTGRAY)
            setPadding(4, 20, 4, 4)
        }
        root.addView(volumeTitle)

        volumeBar = SeekBar(this)
        volumeBar.max = 100
        volumeBar.progress = 70

        volumeBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    val volume = progress / 100f
                    mediaPlayer?.setVolume(volume, volume)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            }
        )

        root.addView(volumeBar)

        setContentView(root)

        handler.post(updateProgress)
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
        }

        startActivityForResult(intent, REQUEST_CODE_AUDIO)
    }

    @Deprecated("Deprecated in Android, retained for compatibility")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_AUDIO &&
            resultCode == Activity.RESULT_OK
        ) {
            val uri = data?.data ?: return

            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // 일부 파일 제공자는 영구 권한을 지원하지 않습니다.
            }

            selectedUri = uri
            playlistText.text = getFileName(uri)
            loadAudio(uri)
        }
    }

    private fun getFileName(uri: Uri): String {
        var name = "선택한 MP3 파일"

        val cursor = contentResolver.query(
            uri,
            null,
            null,
            null,
            null
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(
                    OpenableColumns.DISPLAY_NAME
                )

                if (index >= 0) {
                    name = it.getString(index) ?: name
                }
            }
        }

        return name
    }

    private fun loadAudio(uri: Uri) {
        releasePlayer()

        playButton.text = "..."
        timeText.text = "00:00"
        progressBar.progress = 0

        val player = MediaPlayer()
        mediaPlayer = player

        try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )

            player.setDataSource(this, uri)

            player.setOnPreparedListener { preparedPlayer ->
                preparedPlayer.setVolume(
                    volumeBar.progress / 100f,
                    volumeBar.progress / 100f
                )

                progressBar.max = 1000
                preparedPlayer.start()
                playButton.text = "Ⅱ"

                Toast.makeText(
                    this,
                    "음악 재생을 시작합니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }

            player.setOnCompletionListener {
                playButton.text = "▶"
                progressBar.progress = 0
                timeText.text = "00:00"
            }

            player.setOnErrorListener { _, _, _ ->
                playButton.text = "▶"
                Toast.makeText(
                    this,
                    "파일을 재생할 수 없습니다.",
                    Toast.LENGTH_LONG
                ).show()
                true
            }

            player.prepareAsync()

        } catch (e: Exception) {
            releasePlayer()
            playButton.text = "▶"

            Toast.makeText(
                this,
                "파일을 여는 중 오류가 발생했습니다.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun togglePlayback() {
        val player = mediaPlayer

        if (player == null) {
            if (selectedUri == null) {
                openFilePicker()
            }
            return
        }

        try {
            if (player.isPlaying) {
                player.pause()
                playButton.text = "▶"
            } else {
                player.start()
                playButton.text = "Ⅱ"
            }
        } catch (_: IllegalStateException) {
            Toast.makeText(
                this,
                "음악을 준비하고 있습니다. 잠시 기다려 주세요.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun formatTime(milliseconds: Int): String {
        val totalSeconds = milliseconds / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60

        return String.format(
            "%02d:%02d",
            minutes,
            seconds
        )
    }

    private fun releasePlayer() {
        handler.removeCallbacks(updateProgress)

        mediaPlayer?.release()
        mediaPlayer = null

        handler.post(updateProgress)
    }

    override fun onDestroy() {
        handler.removeCallbacks(updateProgress)
        mediaPlayer?.release()
        mediaPlayer = null
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_CODE_AUDIO = 1001
    }
}
