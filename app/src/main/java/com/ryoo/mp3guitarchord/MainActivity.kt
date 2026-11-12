
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
    private var shuffleMode = false
    private var repeatMode = 0

    private lateinit var timeText: TextView
    private lateinit var chordText: TextView
    private lateinit var playButton: Button
    private lateinit var shuffleButton: Button
    private lateinit var repeatButton: Button
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
                    val duration = mp.duration
                    val position = mp.currentPosition

                    if (duration > 0) {
                        if (!isSeeking) {
                            progressBar.visibility = View.VISIBLE
                            progressBar.max = duration
                            progressBar.progress = position
                        }
                        timeText.text =
                            "${formatTime(position)} / ${formatTime(duration)}"
                    } else {
                        progressBar.visibility = View.GONE
                        timeText.text = "${formatTime(position)} / LIVE"
                    }
                } catch (_: Exception) {
                }
            }
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createScreen()
        updateModeButtons()
        handler.post(updateTask)
    }

    private fun createScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(10))
            setBackgroundColor(Color.rgb(15, 20, 29))
        }

        val title = TextView(this).apply {
            text = "EASY MP3 GUITAR CHORD"
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(0, 220, 255))
            setPadding(0, dp(8), 0, dp(12))
        }
        root.addView(title)

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

        // 코드 표시 바로 아래는 볼륨 조절 바
        volumeBar = SeekBar(this).apply {
            max = 100
            progress = 80
            progressTintList = colorList(Color.YELLOW)
            thumbTintList = colorList(Color.YELLOW)
            setOnSeekBarChangeListener(object :
                SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?, value: Int, fromUser: Boolean
                ) {
                    val volume = value / 100f
                    player?.setVolume(volume, volume)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        root.addView(volumeBar)

        // 재생 위치 표시 바
        progressBar = SeekBar(this).apply {
            progressTintList = colorList(Color.CYAN)
            setOnSeekBarChangeListener(object :
                SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?, value: Int, fromUser: Boolean
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
                    if (isPrepared && (player?.duration ?: 0) > 0) {
                        player?.seekTo(seekBar?.progress ?: 0)
                    }
                    isSeeking = false
                }
            })
        }
        root.addView(progressBar)

        val controls = LinearLayout(this).apply {
            gravity = Gravity.CENTER
        }

        val previous = makeButton("◀")
        playButton = makeButton("▶")
        val next = makeButton("▶▶")

        previous.setOnClickListener { playPrevious() }
        playButton.setOnClickListener { togglePlayback() }
        next.setOnClickListener { playNext() }

        controls.addView(previous, buttonParams())
        controls.addView(playButton, buttonParams())
        controls.addView(next, buttonParams())
        root.addView(controls)

        val modeRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER
        }

        shuffleButton = makeButton("SHUFFLE OFF")
        repeatButton = makeButton("REPEAT OFF")

        shuffleButton.setOnClickListener {
            shuffleMode = !shuffleMode
            updateModeButtons()
        }

        repeatButton.setOnClickListener {
            repeatMode = (repeatMode + 1) % 3
            updateModeButtons()
        }

        modeRow.addView(shuffleButton, buttonParams())
        modeRow.addView(repeatButton, buttonParams())
        root.addView(modeRow)

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
            gravity = Gravity.CENTER
        }

        val add = makeButton("ADD")
        val remove = makeButton("REM")
        val select = makeButton("SEL")

        add.setOnClickListener { showAddMenu() }
        remove.setOnClickListener { removeSelected() }
        select.setOnClickListener { showSelectionMenu() }

        bottomRow.addView(add, buttonParams())
        bottomRow.addView(remove, buttonParams())
        bottomRow.addView(select, buttonParams())
        root.addView(bottomRow)

        val developer = TextView(this).apply {
            text = "Ryoo Jaesang & 최피디"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.CYAN)
            setPadding(0, dp(10), 0, dp(2))
        }
        root.addView(developer)

        setContentView(root)
    }

    private fun showAddMenu() {
        val options = arrayOf("MP3 파일 추가", "인터넷 방송 주소 추가")
        AlertDialog.Builder(this)
            .setTitle("재생목록 추가")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openFilePicker()
                    1 -> showUrlDialog()
                }
            }
            .show()
    }

    private fun showUrlDialog() {
        val input = EditText(this).apply {
            hint = "https://example.com/radio.mp3"
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_URI
        }

        AlertDialog.Builder(this)
            .setTitle("인터넷 방송 주소")
            .setMessage("방송 URL을 입력하거나 붙여넣으세요.")
            .setView(input)
            .setPositiveButton("추가") { _, _ ->
                addStream(input.text.toString().trim())
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun addStream(address: String) {
        if (address.isBlank()) {
            Toast.makeText(this, "방송 주소를 입력해 주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = try {
            Uri.parse(address)
        } catch (_: Exception) {
            null
        }

        if (uri == null ||
            (uri.scheme != "http" && uri.scheme != "https") ||
            uri.host.isNullOrBlank()
        ) {
            Toast.makeText(this, "올바른 HTTP 또는 HTTPS 주소가 아닙니다.", Toast.LENGTH_LONG).show()
            return
        }

        if (playlist.contains(uri)) {
            Toast.makeText(this, "이미 재생목록에 있습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        playlist.add(uri)
        val displayName = uri.host ?: "인터넷 방송"
        songNames.add("RADIO | $displayName")

        adapter.notifyDataSetChanged()

        if (currentIndex == -1) {
            currentIndex = 0
            playlistView.setItemChecked(0, true)
        }

        Toast.makeText(this, "인터넷 방송을 추가했습니다.", Toast.LENGTH_SHORT).show()
    }

    private fun updateModeButtons() {
        shuffleButton.text =
            if (shuffleMode) "SHUFFLE ON" else "SHUFFLE OFF"
        shuffleButton.setTextColor(
            if (shuffleMode) Color.YELLOW else Color.WHITE
        )

        repeatButton.text = when (repeatMode) {
            1 -> "REPEAT ALL"
            2 -> "REPEAT ONE"
            else -> "REPEAT OFF"
        }

        repeatButton.setTextColor(
            if (repeatMode == 0) Color.WHITE else Color.YELLOW
        )
    }

    private fun makeButton(label: String): Button {
        return Button(this).apply {
            text = label
            textSize = 13f
            setTextColor(Color.WHITE)
            isAllCaps = false
            setBackgroundColor(Color.rgb(35, 65, 88))
        }
    }

    private fun buttonParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(0, dp(46), 1f).apply {
            setMargins(dp(3), dp(4), dp(3), dp(4))
        }
    }

    private fun colorList(color: Int) =
        android.content.res.ColorStateList.valueOf(color)

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(intent, 100)
    }

    @Deprecated("Deprecated in Android")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != 100 || resultCode != RESULT_OK || data == null) return

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

        } catch (_: Exception) {
            Toast.makeText(this, "파일 불러오기 실패", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getFileName(uri: Uri): String {
        var name = uri.lastPathSegment ?: "Audio"

        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) name = cursor.getString(index) ?: name
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
        timeText.text = "연결 중..."
        progressBar.progress = 0
        progressBar.visibility = View.VISIBLE

        try {
            val uri = playlist[index]
            val isStream = uri.scheme == "http" || uri.scheme == "https"

            val mp = MediaPlayer()
            player = mp
            isPrepared = false

            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )

            if (isStream) {
                mp.setDataSource(uri.toString())
            } else {
                mp.setDataSource(this, uri)
            }

            mp.setOnPreparedListener { prepared ->
                if (player !== prepared) return@setOnPreparedListener

                isPrepared = true
                val volume = volumeBar.progress / 100f
                prepared.setVolume(volume, volume)

                if (prepared.duration > 0) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.max = prepared.duration
                    timeText.text =
                        "00:00 / ${formatTime(prepared.duration)}"
                } else {
                    progressBar.visibility = View.GONE
                    timeText.text = "LIVE"
                }

                prepared.start()
                playButton.text = "Ⅱ"
            }

            mp.setOnCompletionListener {
                handleTrackCompletion()
            }

            mp.setOnErrorListener { _, _, _ ->
                isPrepared = false
                playButton.text = "▶"
                Toast.makeText(
                    this,
                    "재생할 수 없는 주소 또는 파일입니다.",
                    Toast.LENGTH_LONG
                ).show()
                true
            }

            mp.prepareAsync()

        } catch (_: Exception) {
            releaseCurrentPlayer()
            playButton.text = "▶"
            Toast.makeText(this, "재생을 시작할 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleTrackCompletion() {
        if (repeatMode == 2) {
            playTrack(currentIndex)
            return
        }

        if (shuffleMode) {
            playRandomTrack()
            return
        }

        if (currentIndex < playlist.lastIndex) {
            playTrack(currentIndex + 1)
        } else if (repeatMode == 1 && playlist.isNotEmpty()) {
            playTrack(0)
        } else {
            playButton.text = "▶"
            progressBar.progress = 0
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
                Toast.makeText(this, "먼저 파일이나 방송을 추가해 주세요.", Toast.LENGTH_SHORT).show()
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

    private fun playRandomTrack() {
        if (playlist.isEmpty()) return

        if (playlist.size == 1) {
            playTrack(0)
            return
        }

        val choices = playlist.indices.filter { it != currentIndex }
        playTrack(choices.random())
    }

    private fun playNext() {
        if (playlist.isEmpty()) return

        if (shuffleMode) {
            playRandomTrack()
        } else if (currentIndex < playlist.lastIndex) {
            playTrack(currentIndex + 1)
        } else if (repeatMode == 1) {
            playTrack(0)
        } else {
            Toast.makeText(this, "마지막 곡입니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun playPrevious() {
        if (playlist.isEmpty()) return

        if (shuffleMode) {
            playRandomTrack()
        } else if (currentIndex > 0) {
            playTrack(currentIndex - 1)
        } else if (repeatMode == 1) {
            playTrack(playlist.lastIndex)
        } else {
            Toast.makeText(this, "첫 번째 곡입니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSelectionMenu() {
        val options = arrayOf("전체 선택", "전체 선택 해제")

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
            if (checked.valueAt(i)) removeIndexes.add(checked.keyAt(i))
        }

        if (removeIndexes.isEmpty()) {
            Toast.makeText(this, "삭제할 항목을 선택해 주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        val oldIndex = currentIndex
        val currentRemoved = removeIndexes.contains(oldIndex)

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

        if (!currentRemoved && oldIndex >= 0) {
            currentIndex = oldIndex - removeIndexes.count { it < oldIndex }
        }

        adapter.notifyDataSetChanged()
        playlistView.clearChoices()

        Toast.makeText(
            this,
            "${removeIndexes.size}개 항목을 삭제했습니다.",
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
        val seconds = milliseconds.coerceAtLeast(0) / 1000
        return String.format(
            Locale.getDefault(),
            "%02d:%02d",
            seconds / 60,
            seconds % 60
        )
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        handler.removeCallbacks(updateTask)
        releaseCurrentPlayer()
        super.onDestroy()
    }
}
