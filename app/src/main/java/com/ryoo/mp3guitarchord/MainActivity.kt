
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
import android.graphics.Color
import android.view.Gravity
import android.widget.*
import android.graphics.Typeface

class MainActivity : Activity() {

    private val bg = Color.rgb(13, 18, 28)
    private val panel = Color.rgb(5, 12, 22)
    private val cyan = Color.rgb(0, 220, 255)
    private val yellow = Color.rgb(255, 210, 40)
    private val white = Color.WHITE

    private var mediaPlayer: MediaPlayer? = null
    private var selectedUri: Uri? = null

    private lateinit var timeText: TextView
    private lateinit var chordText: TextView
    private lateinit var volumeBar: SeekBar
    private lateinit var progressBar: SeekBar
    private lateinit var fileText: TextView
    private lateinit var playButton: Button

    private val handler = Handler(Looper.getMainLooper())

    private val progressUpdater = object : Runnable {
        override fun run() {
            val player = mediaPlayer
            if (player != null) {
                try {
                    if (player.isPlaying) {
                        timeText.text = formatTime(player.currentPosition)
                        if (player.duration > 0) {
                            progressBar.progress =
                                player.currentPosition * 1000 / player.duration
                        }
                    }
                } catch (_: Exception) {
                }
            }
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = bg
        window.navigationBarColor = bg

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 12)
            setBackgroundColor(bg)
        }

        // 앱 제목
        val title = TextView(this).apply {
            text = "EASY MP3 GUITAR CHORD"
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(cyan)
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 18)
        }
        root.addView(title)

        // 재생 시간
        timeText = TextView(this).apply {
            text = "00:00"
            textSize = 34f
            typeface = Typeface.MONOSPACE
            setTextColor(yellow)
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 12)
        }
        root.addView(timeText)

        // 기타 코드 표시
        chordText = TextView(this).apply {
            text = "C     G     Am     F     C     G"
            textSize = 27f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(cyan)
            gravity = Gravity.CENTER
            setSingleLine(true)
            setPadding(8, 24, 8, 24)
            setBackgroundColor(panel)
        }
        root.addView(chordText)

        // 볼륨 조절 - 코드 표시 바로 아래
        val volumeLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(4, 4, 4, 4)
        }

        val volumeLabel = TextView(this).apply {
            text = "VOL"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(yellow)
        }

        volumeBar = SeekBar(this).apply {
            max = 100
            progress = 70
            progressTintList =
                android.content.res.ColorStateList.valueOf(cyan)
            thumbTintList =
                android.content.res.ColorStateList.valueOf(yellow)
        }

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

        volumeLayout.addView(volumeLabel)
        volumeLayout.addView(
            volumeBar,
            LinearLayout.LayoutParams(0, 48, 1f)
        )
        root.addView(volumeLayout)

        // 재생 위치 표시
        progressBar = SeekBar(this).apply {
            max = 1000
            progress = 0
            progressTintList =
                android.content.res.ColorStateList.valueOf(yellow)
            thumbTintList =
                android.content.res.ColorStateList.valueOf(cyan)
        }

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
                            val position =
                                progress * player.duration / 1000
                            player.seekTo(position)
                            timeText.text = formatTime(position)
                        }
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            }
        )
        root.addView(progressBar)

        // 재생 버튼
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 16)
        }

        val previous = makeButton("|◀")
        previous.setOnClickListener {
            Toast.makeText(
                this,
                "이전 곡 기능은 재생목록 단계에서 추가합니다.",
                Toast.LENGTH_SHORT
            ).show()
        }

        playButton = makeButton("▶")
        playButton.setOnClickListener {
            togglePlayback()
        }

        val next = makeButton("▶|")
        next.setOnClickListener {
            Toast.makeText(
                this,
                "다음 곡 기능은 재생목록 단계에서 추가합니다.",
                Toast.LENGTH_SHORT
            ).show()
        }

        controls.addView(previous)
        controls.addView(playButton)
        controls.addView(next)
        root.addView(controls)

        // 재생목록 제목
        val playlistTitle = TextView(this).apply {
            text = "PLAY LIST"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(cyan)
            setPadding(4, 12, 4, 10)
        }
        root.addView(playlistTitle)

        // 선택된 파일 표시
        fileText = TextView(this).apply {
            text = "MP3 파일을 추가해 주세요."
            textSize = 15f
            setTextColor(white)
            setPadding(14, 18, 14, 18)
            setBackgroundColor(panel)
            setOnClickListener {
                openFilePicker()
            }
        }
        root.addView(
            fileText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        // ADD / REM / SEL 버튼
        val listControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val addButton = makeButton("ADD")
        addButton.setOnClickListener {
            openFilePicker()
        }

        val removeButton = makeButton("REM")
        removeButton.setOnClickListener {
            releasePlayer()
            selectedUri = null
            fileText.text = "MP3 파일을 추가해 주세요."
            timeText.text = "00:00"
            progressBar.progress = 0
            playButton.text = "▶"
        }

        val selectButton = makeButton("SEL")
        selectButton.setOnClickListener {
            Toast.makeText(
                this,
                "전체 선택 기능은 재생목록 관리 단계에서 추가합니다.",
                Toast.LENGTH_SHORT
            ).show()
        }

        listControls.addView(addButton)
        listControls.addView(removeButton)
        listControls.addView(selectButton)
        root.addView(listControls)

        // 개발자 표시
        val developer = TextView(this).apply {
            text = "Ryoo Jaesang & 최피디"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(yellow)
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 6)
        }
        root.addView(developer)

        setContentView(root)
        handler.post(progressUpdater)
    }

    private fun makeButton(label: String): Button {
        return Button(this).apply {
            text = label
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(cyan)
            setBackgroundColor(Color.rgb(28, 38, 52))
            isAllCaps = false
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
        }
        startActivityForResult(intent, 1001)
    }

    @Deprecated("Deprecated Android API retained for compatibility")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 1001 &&
            resultCode == Activity.RESULT_OK
        ) {
            val uri = data?.data ?: return

            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }

            selectedUri = uri
            fileText.text = getFileName(uri)
            loadAudio(uri)
        }
    }

    private fun getFileName(uri: Uri): String {
        var name = "선택한 MP3 파일"

        contentResolver.query(
            uri,
            null,
            null,
            null,
            null
        )?.use {
            if (it.moveToFirst()) {
                val index =
                    it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    name = it.getString(index) ?: name
                }
            }
        }

        return name
    }

    private fun loadAudio(uri: Uri) {
        releasePlayer()

        timeText.text = "00:00"
        progressBar.progress = 0
        playButton.text = "..."

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

            player.setOnPreparedListener {
                val volume = volumeBar.progress / 100f
                it.setVolume(volume, volume)
                it.start()
                playButton.text = "Ⅱ"
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

        } catch (_: Exception) {
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
        } catch (_: Exception) {
            Toast.makeText(
                this,
                "음악을 준비하고 있습니다.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun formatTime(milliseconds: Int): String {
        val seconds = milliseconds / 1000
        return String.format(
            "%02d:%02d",
            seconds / 60,
            seconds % 60
        )
    }

    private fun releasePlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun onDestroy() {
        handler.removeCallbacks(progressUpdater)
        releasePlayer()
        super.onDestroy()
    }
}
