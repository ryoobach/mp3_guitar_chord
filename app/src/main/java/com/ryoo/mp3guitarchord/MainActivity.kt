
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
import android.graphics.drawable.GradientDrawable
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
                            progressBar.max = duration
                            progressBar.progress = position
                        }

                        timeText.text =
                            "${formatTime(position)} / ${formatTime(duration)}"
                    } else {
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

    // --------------------------------------------------
    // 전체 화면 구성
    // --------------------------------------------------

    private fun createScreen() {

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(7), dp(8), dp(6))
            setBackgroundColor(Color.rgb(8, 15, 27))
        }

        // 상단 브랜딩: 아이콘 + 앱 이름 + 개발자 이름 한 줄
        root.addView(
            createBrandRow(false),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
            )
        )

        addDivider(root)

        // ----------------------------------------------
        // 메인 재생 화면
        // ----------------------------------------------

        val playerPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(
                Color.rgb(13, 26, 42),
                Color.rgb(52, 97, 122)
            )
        }

        // 위쪽 영역: 왼쪽 1/3 시간, 오른쪽 2/3 코드
        val upperArea = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        // 왼쪽 시간 표시 영역
        val timeArea = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(23, 40, 58))
            setPadding(dp(3), dp(5), dp(3), dp(5))
        }

        timeText = TextView(this).apply {
            text = "00:00 / 00:00"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(245, 214, 107))
            isSingleLine = true
            includeFontPadding = false
        }

        timeArea.addView(
            timeText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        upperArea.addView(
            timeArea,
            LinearLayout.LayoutParams(0, dp(135), 1f)
        )

        // 오른쪽 코드 표시 영역
        val chordArea = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(16, 31, 50))
            setPadding(dp(7), dp(5), dp(7), dp(5))
        }

        chordText = TextView(this).apply {
            text = "Gm7"
            textSize = 32f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(85, 213, 255))
        }

        chordArea.addView(
            chordText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        // 볼륨 조절: 코드 영역 안에서 전체 폭
        val volumeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val volumeLabel = TextView(this).apply {
            text = "볼륨"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(245, 214, 107))
            setPadding(0, 0, dp(5), 0)
        }

        volumeRow.addView(volumeLabel)

        volumeBar = SeekBar(this).apply {
            max = 100
            progress = 80
            progressTintList = colorList(Color.rgb(245, 214, 107))
            thumbTintList = colorList(Color.rgb(245, 214, 107))

            setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        value: Int,
                        fromUser: Boolean
                    ) {
                        val volume = value / 100f
                        player?.setVolume(volume, volume)
                    }

                    override fun onStartTrackingTouch(
                        seekBar: SeekBar?
                    ) {}

                    override fun onStopTrackingTouch(
                        seekBar: SeekBar?
                    ) {}
                }
            )
        }

        volumeRow.addView(
            volumeBar,
            LinearLayout.LayoutParams(0, dp(35), 1f)
        )

        chordArea.addView(
            volumeRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(38)
            )
        )

        upperArea.addView(
            chordArea,
            LinearLayout.LayoutParams(0, dp(135), 2f)
        )

        playerPanel.addView(upperArea)

        // 곡 진행 바: 전체 폭, 별도 설명 문구 없음
        progressBar = SeekBar(this).apply {
            max = 100
            progress = 0
            progressTintList = colorList(Color.rgb(85, 213, 255))
            thumbTintList = colorList(Color.rgb(85, 213, 255))

            setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        value: Int,
                        fromUser: Boolean
                    ) {
                        if (fromUser && isPrepared) {
                            timeText.text =
                                "${formatTime(value)} / " +
                                "${formatTime(player?.duration ?: 0)}"
                        }
                    }

                    override fun onStartTrackingTouch(
                        seekBar: SeekBar?
                    ) {
                        isSeeking = true
                    }

                    override fun onStopTrackingTouch(
                        seekBar: SeekBar?
                    ) {
                        if (isPrepared &&
                            (player?.duration ?: 0) > 0
                        ) {
                            player?.seekTo(seekBar?.progress ?: 0)
                        }

                        isSeeking = false
                    }
                }
            )
        }

        playerPanel.addView(
            progressBar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(38)
            )
        )

        // 재생 관련 버튼 한 줄
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(3), dp(3), dp(3), dp(3))
        }

        shuffleButton = makeButton("SHF")
        val previous = makeButton("◀")
        playButton = makeButton("▶")
        val stop = makeButton("■")
        val next = makeButton("▶▶")
        repeatButton = makeButton("RPT")

        shuffleButton.setOnClickListener {
            shuffleMode = !shuffleMode
            updateModeButtons()
        }

        previous.setOnClickListener {
            playPrevious()
        }

        playButton.setOnClickListener {
            togglePlayback()
        }

        stop.setOnClickListener {
            stopPlayback()
        }

        next.setOnClickListener {
            playNext()
        }

        repeatButton.setOnClickListener {
            repeatMode = (repeatMode + 1) % 3
            updateModeButtons()
        }

        controls.addView(shuffleButton, controlParams())
        controls.addView(previous, controlParams())

        controls.addView(
            playButton,
            LinearLayout.LayoutParams(0, dp(44), 1.2f)
        )

        controls.addView(stop, controlParams())
        controls.addView(next, controlParams())
        controls.addView(repeatButton, controlParams())

        playerPanel.addView(controls)

        root.addView(
            playerPanel,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        // ----------------------------------------------
        // 플레이리스트
        // ----------------------------------------------

        val playlistTitle = TextView(this).apply {
            text = "PLAY LIST"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(245, 214, 107))
            setPadding(dp(4), dp(8), 0, dp(5))
        }

        root.addView(playlistTitle)

        playlistView = ListView(this).apply {
            choiceMode = ListView.CHOICE_MODE_MULTIPLE
            setBackgroundColor(Color.rgb(16, 31, 50))
            setCacheColorHint(Color.TRANSPARENT)
            divider = android.graphics.drawable.ColorDrawable(
                Color.rgb(52, 75, 97)
            )
            dividerHeight = dp(1)

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

        // ----------------------------------------------
        // 맨 아래: 왼쪽 관리 버튼 + 오른쪽 브랜딩
        // ----------------------------------------------

        val bottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, 0)
        }

        val add = makeButton("ADD")
        val remove = makeButton("REM")
        val select = makeButton("SEL")

        add.setOnClickListener { showAddMenu() }
        remove.setOnClickListener { removeSelected() }
        select.setOnClickListener { showSelectionMenu() }

        bottomRow.addView(add, bottomButtonParams())
        bottomRow.addView(remove, bottomButtonParams())
        bottomRow.addView(select, bottomButtonParams())

        // 오른쪽에 작은 아이콘 + 앱 이름 + 개발자 이름
        bottomRow.addView(
            createBrandRow(true),
            LinearLayout.LayoutParams(0, dp(38), 1f)
        )

        root.addView(
            bottomRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(43)
            )
        )

        setContentView(root)
    }

    // --------------------------------------------------
    // 상단 / 하단 공통 브랜딩
    // --------------------------------------------------

    private fun createBrandRow(compact: Boolean): LinearLayout {

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                if (compact) dp(2) else dp(1),
                0,
                0,
                0
            )
        }

        val icon = TextView(this).apply {
            text = "🎸"
            gravity = Gravity.CENTER
            textSize = if (compact) 19f else 27f
            background = roundedBackground(
                Color.rgb(18, 60, 97),
                Color.rgb(85, 213, 255)
            )
        }

        val iconSize = if (compact) 29 else 40

        row.addView(
            icon,
            LinearLayout.LayoutParams(dp(iconSize), dp(iconSize))
        )

        val titleSize = if (compact) 10.5f else 16f
        val developerSize = if (compact) 7f else 9f

        val title = TextView(this).apply {
            text = "Easy MP3 Guitar Chord"
            textSize = titleSize
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(85, 213, 255))
            isSingleLine = true
            includeFontPadding = false
        }

        val developer = TextView(this).apply {
            text = "Ryoo Jaesang & 최피디"
            textSize = developerSize
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(245, 214, 107))
            isSingleLine = true
            includeFontPadding = false
        }

        row.addView(
            title,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = dp(if (compact) 4 else 7)
            }
        )

        row.addView(
            developer,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = dp(5)
            }
        )

        return row
    }

    // --------------------------------------------------
    // 화면 보조 함수
    // --------------------------------------------------

    private fun addDivider(parent: LinearLayout) {
        val line = View(this).apply {
            setBackgroundColor(Color.rgb(52, 97, 122))
        }

        parent.addView(
            line,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)
            ).apply {
                topMargin = dp(4)
                bottomMargin = dp(5)
            }
        )
    }

    private fun roundedBackground(
        color: Int,
        strokeColor: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(5).toFloat()
            setStroke(dp(1), strokeColor)
        }
    }

    private fun makeButton(label: String): Button {
        return Button(this).apply {
            text = label
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            isAllCaps = false
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(2), 0, dp(2), 0)
            background = roundedBackground(
                Color.rgb(35, 65, 88),
                Color.rgb(64, 103, 130)
            )
        }
    }

    private fun controlParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(0, dp(44), 1f).apply {
            setMargins(dp(2), 0, dp(2), 0)
        }
    }

    private fun bottomButtonParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(dp(43), dp(34)).apply {
            setMargins(dp(2), 0, dp(2), 0)
        }
    }

    private fun colorList(color: Int) =
        android.content.res.ColorStateList.valueOf(color)

    // --------------------------------------------------
    // 추가 메뉴
    // --------------------------------------------------

    private fun showAddMenu() {
        val options = arrayOf(
            "MP3 파일 추가",
            "인터넷 방송 주소 추가"
        )

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
            inputType =
                android.text.InputType.TYPE_CLASS_TEXT or
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
            Toast.makeText(
                this,
                "방송 주소를 입력해 주세요.",
                Toast.LENGTH_SHORT
            ).show()
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
            Toast.makeText(
                this,
                "올바른 HTTP 또는 HTTPS 주소가 아닙니다.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (playlist.contains(uri)) {
            Toast.makeText(
                this,
                "이미 재생목록에 있습니다.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        playlist.add(uri)
        songNames.add("RADIO | ${uri.host ?: "인터넷 방송"}")

        adapter.notifyDataSetChanged()

        if (currentIndex == -1) {
            currentIndex = 0
            playlistView.setItemChecked(0, true)
        }

        Toast.makeText(
            this,
            "인터넷 방송을 추가했습니다.",
            Toast.LENGTH_SHORT
        ).show()
    }

    // --------------------------------------------------
    // 셔플 / 반복 표시
    // --------------------------------------------------

    private fun updateModeButtons() {

        shuffleButton.text =
            if (shuffleMode) "SHF ON" else "SHF"

        shuffleButton.setTextColor(
            if (shuffleMode) Color.YELLOW else Color.WHITE
        )

        repeatButton.text = when (repeatMode) {
            1 -> "RPT ALL"
            2 -> "RPT ONE"
            else -> "RPT"
        }

        repeatButton.setTextColor(
            if (repeatMode == 0) Color.WHITE else Color.YELLOW
        )
    }

    // --------------------------------------------------
    // MP3 파일 불러오기
    // --------------------------------------------------

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

        if (requestCode != 100 ||
            resultCode != RESULT_OK ||
            data == null
        ) return

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
            Toast.makeText(
                this,
                "파일 불러오기 실패",
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
                    val index =
                        cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)

                    if (index >= 0) {
                        name = cursor.getString(index) ?: name
                    }
                }
            }
        } catch (_: Exception) {
        }

        return name
    }

    // --------------------------------------------------
    // 재생 기능
    // --------------------------------------------------

    private fun playTrack(index: Int) {
        if (index !in playlist.indices) return

        releaseCurrentPlayer()

        currentIndex = index
        playlistView.setItemChecked(index, true)

        playButton.text = "…"
        timeText.text = "00:00 / 00:00"
        progressBar.progress = 0

        try {
            val uri = playlist[index]
            val isStream =
                uri.scheme == "http" || uri.scheme == "https"

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
                    progressBar.max = prepared.duration
                    progressBar.progress = 0

                    timeText.text =
                        "00:00 / ${formatTime(prepared.duration)}"
                } else {
                    timeText.text = "00:00 / LIVE"
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

            Toast.makeText(
                this,
                "재생을 시작할 수 없습니다.",
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
                    "먼저 파일이나 방송을 추가해 주세요.",
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

    // 정지 버튼: 재생을 멈추고 처음 위치로 이동
    private fun stopPlayback() {
        val mp = player

        if (mp != null && isPrepared) {
            try {
                if (mp.isPlaying) {
                    mp.pause()
                }

                mp.seekTo(0)
                progressBar.progress = 0

                timeText.text =
                    "00:00 / ${formatTime(mp.duration)}"

                playButton.text = "▶"

            } catch (_: Exception) {
            }
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

    private fun playRandomTrack() {
        if (playlist.isEmpty()) return

        if (playlist.size == 1) {
            playTrack(0)
            return
        }

        val choices =
            playlist.indices.filter { it != currentIndex }

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
            Toast.makeText(
                this,
                "마지막 곡입니다.",
                Toast.LENGTH_SHORT
            ).show()
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
            Toast.makeText(
                this,
                "첫 번째 곡입니다.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // --------------------------------------------------
    // 재생목록 선택 / 삭제
    // --------------------------------------------------

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
                "삭제할 항목을 선택해 주세요.",
                Toast.LENGTH_SHORT
            ).show()
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
            currentIndex =
                oldIndex - removeIndexes.count { it < oldIndex }
        }

        adapter.notifyDataSetChanged()
        playlistView.clearChoices()

        Toast.makeText(
            this,
            "${removeIndexes.size}개 항목을 삭제했습니다.",
            Toast.LENGTH_SHORT
        ).show()
    }

    // --------------------------------------------------
    // 플레이어 정리 / 시간 표시
    // --------------------------------------------------

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
        val seconds =
            milliseconds.coerceAtLeast(0) / 1000

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
