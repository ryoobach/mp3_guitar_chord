
package com.ryoo.mp3guitarchord

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.*
import java.util.Locale

class MainActivity : Activity() {

    private val playlist = mutableListOf<Uri>()
    private val songNames = mutableListOf<String>()

    private var player: MediaPlayer? = null
    private var currentIndex = -1
    private var isPrepared = false
    private var isSeeking = false

    private lateinit var titleText: TextView
    private lateinit var timeText: TextView
    private lateinit var chordText: TextView
    private lateinit var playButton: Button
    private lateinit var progressBar: SeekBar
    private lateinit var volumeBar: SeekBar
    private lateinit var playlistView: ListView
    private lateinit var adapter: ArrayAdapter<String>

    private val handler = Handler(Looper.getMainLooper())

    private val updateTask = object : Runnable {
        override fun run() {
            val mp = player
            if (mp != null && isPrepared) {
                try {
                    val position = mp.currentPosition
                    val duration = mp.duration

                    if (!isSeeking) {
                        progressBar.max = duration.coerceAtLeast(1)
                        progressBar.progress = position
                    }

                    timeText.text =
                        "${formatTime(position)} / ${formatTime(duration)}"
                } catch (_: Exception) {
                }
            }
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createScreen()
        handler.post(updateTask)
    }

    private fun createScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(10))
            setBackgroundColor(Color.rgb(15, 20, 29))
        }

        titleText = TextView(this).apply {
            text = "EASY MP3 GUITAR CHORD"
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(0, 220, 255))
            setPadding(0, dp(8), 0, dp(12))
        }
        root.addView(titleText)

        timeText = TextView(this).apply {
            text = "00:00 / 00:00"
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(Color.YELLOW)
            setPadding(0, dp(5), 0, dp(8))
        }
        root.addView(timeText)

        chordText = TextView(this).apply {
            text = "C     G     Am     F"
            textSize = 27f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(0, 230, 255))
            setPadding(0, dp(15), 0, dp(15))
        }
        root.addView(chordText)

        // 볼륨 조절 바: 코드 표시 바로 아래
        volumeBar = SeekBar(this).apply {
            max = 100
            progress = 80
            progressTintList =
                android.content.res.ColorStateList.valueOf(Color.YELLOW)
            thumbTintList =
                android.content.res.ColorStateList.valueOf(Color.YELLOW)

            setOnSeekBarChangeListener(object :
                SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    value: Int,
                    fromUser: Boolean
                ) {
                    val volume = value / 100f
                    player?.setVolume(volume, volume)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        root.addView(volumeBar)

        progressBar = SeekBar(this).apply {
            progressTintList =
                android.content.res.ColorStateList.valueOf(
                    Color.rgb(0, 220, 255)
                )

            setOnSeekBarChangeListener(object :
                SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    value: Int,
                    fromUser: Boolean
                ) {
                    if (fromUser && isPrepared) {
                        timeText.text =
                            "${formatTime(value)} / ${formatTime(player?.duration ?: 0)}"
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    isSeeking = true
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    if (isPrepared) {
                        player?.seekTo(seekBar?.progress ?: 0)
                    }
                    isSeeking = false
                }
            })
        }
        root.addView(progressBar)

        val controlRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.HORIZONTAL
        }

        val previousButton = makeButton("◀")
        playButton = makeButton("▶")
        val nextButton = makeButton("▶▶")

        previousButton.setOnClickListener { playPrevious() }
        playButton.setOnClickListener { togglePlayback() }
        nextButton.setOnClickListener { playNext() }

        controlRow.addView(previousButton, buttonParams())
        controlRow.addView(playButton, buttonParams())
        controlRow.addView(nextButton, buttonParams())
        root.addView(controlRow)

        val playlistTitle = TextView(this).apply {
            text = "PLAY LIST"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.YELLOW)
            setPadding(0, dp(12), 0, dp(6))
        }
        root.addView(playlistTitle)

        playlistView = ListView(this).apply {
            choiceMode = ListView.CHOICE_MODE_MULTIPLE
            setBackgroundColor(Color.rgb(24, 31, 43))
            setCacheColorHint(Color.TRANSPARENT)
            setOnItemClickListener { _, _, position, _ ->
                currentIndex = position
                playTrack(position)
            }
        }

        adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_multiple_choice,
            songNames
        )
        playlistView.adapter = adapter

        root.addView(
            playlistView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val bottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val addButton = makeButton("ADD")
        val removeButton = makeButton("REM")
        val selectButton = makeButton("SEL")

        addButton.setOnClickListener { openFilePicker() }
        removeButton.setOnClickListener { removeSelected() }
        selectButton.setOnClickListener { showSelectionMenu() }

        bottomRow.addView(addButton, buttonParams())
        bottomRow.addView(removeButton, buttonParams())
        bottomRow.addView(selectButton, buttonParams())
        root.addView(bottomRow)

        val developer = TextView(this).apply {
            text = "Ryoo Jaesang & 최피디"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(0, 220, 255))
            setPadding(0, dp(10), 0, dp(2))
        }
        root.addView(developer)

        setContentView(root)
    }

    private fun makeButton(label: String): Button {
        return Button(this).apply {
            text = label
            textSize = 14f
            setTextColor(Color.WHITE)
            isAllCaps = false
            setBackgroundColor(Color.rgb(35, 65, 88))
        }
    }

    private fun buttonParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            0,
            dp(48),
            1f
        ).apply {
            setMargins(dp(4), dp(5), dp(4), dp(5))
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(intent, 100)
    }

    @Deprecated("Deprecated in Android, retained for compatibility")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != 100 || resultCode != RESULT_OK || data == null) {
            return
        }

        val added = mutableListOf<Uri>()

        try {
            if (data.clipData != null) {
                val clip = data.clipData!!
                for (i in 0 until clip.itemCount) {
                    added.add(clip.getItemAt(i).uri)
                }
            } else {
                data.data?.let { added.add(it) }
            }

            for (uri in added) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                }

                if (!playlist.contains(uri)) {
                    playlist.add(uri)
                    songNames.add(getFileName(uri))
                }
            }

            adapter.notifyDataSetChanged()

            if (currentIndex == -1 && playlist.isNotEmpty()) {
                currentIndex = 0
                playlistView.setItemChecked(0, true)
            }

            Toast.makeText(
                this,
                "${added.size}개 파일을 불러왔습니다.",
                Toast.LENGTH_SHORT
            ).show()

        } catch (e: Exception) {
            Toast.makeText(
                this,
                "파일을 불러오지 못했습니다.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun getFileName(uri: Uri): String {
        var name = uri.lastPathSegment ?: "Audio"

        try {
            contentResolver.query(
                uri,
                null,
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(
                        OpenableColumns.DISPLAY_NAME
                    )
                    if (index >= 0) {
                        name = cursor.getString(index) ?: name
                    }
                }
            }
        } catch (_: Exception) {
        }

        return name
    }

    private fun playTrack(index: Int) {
        if (index !in playlist.indices) return

        releaseCurrentPlayer()

        currentIndex = index
        playlistView.setItemChecked(index, true)
        playButton.text = "…"
        timeText.text = "00:00 / 00:00"

        try {
            val mp = MediaPlayer()
            player = mp
            isPrepared = false

            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )

            mp.setDataSource(this, playlist[index])

            mp.setOnPreparedListener { preparedPlayer ->
                if (player !== preparedPlayer) return@setOnPreparedListener

                isPrepared = true
                preparedPlayer.setVolume(
                    volumeBar.progress / 100f,
                    volumeBar.progress / 100f
                )
                progressBar.max = preparedPlayer.duration.coerceAtLeast(1)
                preparedPlayer.start()
                playButton.text = "Ⅱ"
            }

            mp.setOnCompletionListener {
                playNext()
            }

            mp.setOnErrorListener { _, _, _ ->
                Toast.makeText(
                    this,
                    "이 파일을 재생할 수 없습니다.",
                    Toast.LENGTH_SHORT
                ).show()
                playButton.text = "▶"
                isPrepared = false
                true
            }

            mp.prepareAsync()

        } catch (e: Exception) {
            releaseCurrentPlayer()
            playButton.text = "▶"
            Toast.makeText(
                this,
                "재생 중 오류가 발생했습니다.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun togglePlayback() {
        val mp = player

        if (mp == null) {
            if (currentIndex in playlist.indices) {
                playTrack(currentIndex)
            } else if (playlist.isNotEmpty()) {
                playTrack(0)
            } else {
                Toast.makeText(
                    this,
                    "먼저 MP3 파일을 추가해 주세요.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            return
        }

        if (!isPrepared) return

        if (mp.isPlaying) {
            mp.pause()
            playButton.text = "▶"
        } else {
            mp.start()
            playButton.text = "Ⅱ"
        }
    }

    private fun playPrevious() {
        if (playlist.isEmpty()) return

        val index = if (currentIndex <= 0) {
            playlist.lastIndex
        } else {
            currentIndex - 1
        }
        playTrack(index)
    }

    private fun playNext() {
        if (playlist.isEmpty()) return

        val index = if (currentIndex >= playlist.lastIndex) {
            0
        } else {
            currentIndex + 1
        }
        playTrack(index)
    }

    private fun showSelectionMenu() {
        val options = arrayOf(
            "전체 선택",
            "전체 선택 해제"
        )

        AlertDialog.Builder(this)
            .setTitle("재생목록 선택")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        for (i in songNames.indices) {
                            playlistView.setItemChecked(i, true)
                        }
                    }
                    1 -> playlistView.clearChoices()
                }
            }
            .show()
    }

    private fun removeSelected() {
        val checked = playlistView.checkedItemPositions
        val removeIndexes = mutableListOf<Int>()

        for (i in 0 until checked.size()) {
            if (checked.valueAt(i)) {
                removeIndexes.add(checked.keyAt(i))
            }
        }

        if (removeIndexes.isEmpty()) {
            Toast.makeText(
                this,
                "삭제할 곡을 먼저 선택해 주세요.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val oldCurrentIndex = currentIndex
        val currentRemoved = removeIndexes.contains(oldCurrentIndex)

        if (currentRemoved) {
            releaseCurrentPlayer()
            currentIndex = -1
            playButton.text = "▶"
            timeText.text = "00:00 / 00:00"
            progressBar.progress = 0
        }

        for (index in removeIndexes.sortedDescending()) {
            playlist.removeAt(index)
            songNames.removeAt(index)
        }

        if (!currentRemoved && oldCurrentIndex >= 0) {
            currentIndex =
                oldCurrentIndex - removeIndexes.count { it < oldCurrentIndex }
        }

        adapter.notifyDataSetChanged()
        playlistView.clearChoices()

        Toast.makeText(
            this,
            "${removeIndexes.size}개 곡을 삭제했습니다.",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun releaseCurrentPlayer() {
        isPrepared = false

        try {
            player?.stop()
        } catch (_: Exception) {
        }

        try {
            player?.release()
        } catch (_: Exception) {
        }

        player = null
    }

    private fun formatTime(milliseconds: Int): String {
        val totalSeconds = milliseconds.coerceAtLeast(0) / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60

        return String.format(
            Locale.getDefault(),
            "%02d:%02d",
            minutes,
            seconds
        )
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        handler.removeCallbacks(updateTask)
        releaseCurrentPlayer()
        super.onDestroy()
    }
}
