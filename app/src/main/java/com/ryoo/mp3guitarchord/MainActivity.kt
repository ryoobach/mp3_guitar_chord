
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

    private lateinit var elapsedTimeText: TextView
    private lateinit var totalTimeText: TextView
    private lateinit var songTitle: TextView
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

                    if (!isSeeking) {
                        if (duration > 0) {
                            progressBar.max = duration
                            progressBar.progress = position
                        }
                        updateTimeDisplay(position, duration)
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
    // 화면 구성 - 휴대폰 / 태블릿 자동 대응
    // --------------------------------------------------

    private fun createScreen() {

        val widthDp = resources.configuration.screenWidthDp
        val heightDp = resources.configuration.screenHeightDp

        val isTablet = widthDp >= 600
        val isCompact = heightDp < 650 || widthDp < 360

        val panelHeight = when {
            isCompact -> 108
            isTablet -> 158
            else -> 132
        }

        val timeFontSize = when {
            isCompact -> 22f
            isTablet -> 31f
            else -> 27f
        }

        val chordFontSize = when {
            isCompact -> 25f
            isTablet -> 38f
            else -> 32f
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setBackgroundColor(Color.rgb(8, 15, 27))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        // 상단 앱 이름과 메뉴

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(3), 0, dp(2), 0)
        }

        topBar.addView(
            createAppIcon(dp(32)),
            LinearLayout.LayoutParams(dp(32), dp(32)).apply {
                setMargins(0, 0, dp(6), 0)
            }
        )

        val topTitle = TextView(this).apply {
            text = "Easy MP3 Guitar Chord"
            textSize = if (isCompact) 11f else 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(85, 213, 255))
            isSingleLine = true
            includeFontPadding = false
        }

        topBar.addView(
            topTitle,
            LinearLayout.LayoutParams(0, dp(40), 1f)
        )

        val homeButton = makeButton("⌂").apply {
            textSize = 20f
            contentDescription = "홈 화면으로 이동"
            setOnClickListener {
                moveTaskToBack(true)
            }
        }

        val developerButton = makeButton("ⓘ").apply {
            textSize = 18f
            contentDescription = "개발자 정보"
            setOnClickListener {
                showDeveloperInfo()
            }
        }

        val exitButton = makeButton("×").apply {
            textSize = 23f
            contentDescription = "앱 종료"
            setOnClickListener {
                releaseCurrentPlayer()
                finishAffinity()
            }
        }

        topBar.addView(homeButton, topButtonParams(isCompact))
        topBar.addView(developerButton, topButtonParams(isCompact))
        topBar.addView(exitButton, topButtonParams(isCompact))

        root.addView(
            topBar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(if (isCompact) 38 else 42)
            )
        )

        addDivider(root)

        // 재생 화면

        val playerPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(
                Color.rgb(13, 26, 42),
                Color.rgb(52, 97, 122)
            )
        }

        val upperArea = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        // 왼쪽 시간 표시

        val timeArea = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(23, 40, 58))
            setPadding(dp(2), dp(3), dp(2), dp(3))
        }

        elapsedTimeText = TextView(this).apply {
            text = "00:00"
            textSize = timeFontSize
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(245, 214, 107))
            isSingleLine = true
            includeFontPadding = false
        }

        totalTimeText = TextView(this).apply {
            text = "/ 00:00"
            textSize = if (isCompact) 13f else 16f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(180, 195, 210))
            isSingleLine = true
            includeFontPadding = false
        }

        timeArea.addView(
            elapsedTimeText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        timeArea.addView(
            totalTimeText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                0.7f
            )
        )

        upperArea.addView(
            timeArea,
            LinearLayout.LayoutParams(0, dp(panelHeight), 1f)
        )

        // 오른쪽 곡 제목 / 코드 / 볼륨

        val chordArea = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(16, 31, 50))
            setPadding(dp(5), dp(3), dp(5), dp(3))
        }

        songTitle = TextView(this).apply {
            text = "곡을 선택해 주세요"
            textSize = if (isCompact) 12f else 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(245, 214, 107))
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
            includeFontPadding = false
        }

        chordArea.addView(
            songTitle,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(if (isCompact) 22 else 27)
            )
        )

        chordText = TextView(this).apply {
            text = "Gm7"
            textSize = chordFontSize
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

        // 볼륨

        val volumeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val volumeLabel = TextView(this).apply {
            text = "볼륨"
            textSize = if (isCompact) 10f else 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(245, 214, 107))
            setPadding(0, 0, dp(4), 0)
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
            LinearLayout.LayoutParams(0, dp(32), 1f)
        )

        chordArea.addView(
            volumeRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(if (isCompact) 30 else 36)
            )
        )

        upperArea.addView(
            chordArea,
            LinearLayout.LayoutParams(0, dp(panelHeight), 2f)
        )

        playerPanel.addView(upperArea)

        // 진행 바

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
                            updateTimeDisplay(
                                value,
                                player?.duration ?: 0
                            )
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
                dp(if (isCompact) 31 else 36)
            )
        )

        // 재생 버튼

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(3), dp(2), dp(3), dp(2))
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

        val controlHeight = if (isCompact) 39 else 44

        controls.addView(shuffleButton, controlParams(controlHeight))
        controls.addView(previous, controlParams(controlHeight))

        controls.addView(
            playButton,
            LinearLayout.LayoutParams(0, dp(controlHeight), 1.2f).apply {
                setMargins(dp(2), 0, dp(2), 0)
            }
        )

        controls.addView(stop, controlParams(controlHeight))
        controls.addView(next, controlParams(controlHeight))
        controls.addView(repeatButton, controlParams(controlHeight))

        playerPanel.addView(controls)

        root.addView(
            playerPanel,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        // 플레이리스트 영역
        // 제목과 목록을 하나의 가변 영역으로 묶습니다.

        val playlistSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
        }

        val playlistTitle = TextView(this).apply {
            text = "PLAY LIST"
            textSize = if (isCompact) 13f else 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(245, 214, 107))
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(3), 0, dp(3))
        }

        playlistSection.addView(
            playlistTitle,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(if (isCompact) 27 else 31)
            )
        )

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

        playlistSection.addView(
            playlistView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        // 중요: 플레이리스트 영역만 남은 공간을 사용합니다.
        // 하단 버튼은 이 영역 바깥에 별도로 배치합니다.

        root.addView(
            playlistSection,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        // 하단 버튼 영역 - 항상 고정

        val bottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, 0)
            setBackgroundColor(Color.rgb(8, 15, 27))
        }

        val add = makeButton("ADD")
        val remove = makeButton("REM")
        val select = makeButton("SEL")

        add.setOnClickListener {
            showAddMenu()
        }

        remove.setOnClickListener {
            removeSelected()
        }

        select.setOnClickListener {
            showSelectionMenu()
        }

        val bottomButtonWidth = when {
            widthDp < 360 -> 39
            isTablet -> 52
            else -> 44
        }

        bottomRow.addView(
            add,
            bottomButtonParams(bottomButtonWidth)
        )

        bottomRow.addView(
            remove,
            bottomButtonParams(bottomButtonWidth)
        )

        bottomRow.addView(
            select,
            bottomButtonParams(bottomButtonWidth)
        )

        bottomRow.addView(
            createBottomBrand(isCompact),
            LinearLayout.LayoutParams(0, dp(42), 1f)
        )

        root.addView(
            bottomRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(46)
            )
        )

        setContentView(root)
    }

    // --------------------------------------------------
    // 실제 앱 아이콘
    // --------------------------------------------------

    private fun createAppIcon(size: Int): ImageView {

        return ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER

            try {
                setImageDrawable(
                    packageManager.getApplicationIcon(packageName)
                )
            } catch (_: Exception) {
                setImageDrawable(
                    roundedBackground(
                        Color.rgb(18, 60, 97),
                        Color.rgb(85, 213, 255)
                    )
                )
            }

            contentDescription = "Easy MP3 Guitar Chord 앱 아이콘"
        }
    }

    // --------------------------------------------------
    // 개발자 정보
    // --------------------------------------------------

    private fun showDeveloperInfo() {

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(20), dp(24), dp(20))
        }

        content.addView(
            createAppIcon(dp(88)),
            LinearLayout.LayoutParams(dp(88), dp(88)).apply {
                gravity = Gravity.CENTER
                bottomMargin = dp(14)
            }
        )

        val title = TextView(this).apply {
            text = "Easy MP3 Guitar Chord"
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(85, 213, 255))
        }

        content.addView(title)

        val developer = TextView(this).apply {
            text = "Ryoo Jaesang & 최피디"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(245, 214, 107))
            setPadding(0, dp(12), 0, dp(4))
        }

        content.addView(developer)

        AlertDialog.Builder(this)
            .setTitle("개발자 정보")
            .setView(content)
            .setPositiveButton("확인", null)
            .show()
    }

    // --------------------------------------------------
    // 하단 브랜딩
    // --------------------------------------------------

    private fun createBottomBrand(isCompact: Boolean): LinearLayout {

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), 0, 0, 0)
        }

        val guitarIcon = TextView(this).apply {
            text = "🎸"
            textSize = if (isCompact) 17f else 21f
            gravity = Gravity.CENTER
            includeFontPadding = false
        }

        row.addView(
            guitarIcon,
            LinearLayout.LayoutParams(
                dp(if (isCompact) 25 else 30),
                dp(36)
            )
        )

        val title = TextView(this).apply {
            text = "Easy MP3 Guitar Chord"
            textSize = if (isCompact) 8f else 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(85, 213, 255))
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
            includeFontPadding = false
        }

        row.addView(
            title,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        return row
    }

    // --------------------------------------------------
    // 시간 표시
    // --------------------------------------------------

    private fun updateTimeDisplay(position: Int, duration: Int) {

        elapsedTimeText.text = formatTime(position)

        totalTimeText.text =
            if (duration > 0) {
                "/ ${formatTime(duration)}"
            } else {
                "/ LIVE"
            }
    }

    private fun resetTimeDisplay() {
        elapsedTimeText.text = "00:00"
        totalTimeText.text = "/ 00:00"
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
                topMargin = dp(2)
                bottomMargin = dp(2)
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

    private fun topButtonParams(isCompact: Boolean): LinearLayout.LayoutParams {
        val size = if (isCompact) 34 else 38

        return LinearLayout.LayoutParams(dp(size), dp(size - 2)).apply {
            setMargins(dp(2), 0, dp(2), 0)
        }
    }

    private fun controlParams(height: Int): LinearLayout.LayoutParams {

        return LinearLayout.LayoutParams(0, dp(height), 1f).apply {
            setMargins(dp(2), 0, dp(2), 0)
        }
    }

    private fun bottomButtonParams(width: Int): LinearLayout.LayoutParams {

        return LinearLayout.LayoutParams(dp(width), dp(34)).apply {
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
            songTitle.text = songNames[0]
            playlistView.setItemChecked(0, true)
        }

        Toast.makeText(
            this,
            "인터넷 방송을 추가했습니다.",
            Toast.LENGTH_SHORT
        ).show()
    }

    // --------------------------------------------------
    // 셔플 / 반복
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
                songTitle.text = songNames[0]
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
        songTitle.text = songNames[index]

        playlistView.setItemChecked(index, true)

        playButton.text = "…"
        resetTimeDisplay()
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
                }

                updateTimeDisplay(0, prepared.duration)

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

    private fun stopPlayback() {

        val mp = player

        if (mp != null && isPrepared) {

            try {
                if (mp.isPlaying) {
                    mp.pause()
                }

                mp.seekTo(0)
                progressBar.progress = 0
                updateTimeDisplay(0, mp.duration)
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
    // 플레이리스트 선택 / 삭제
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
            resetTimeDisplay()
            progressBar.progress = 0
            songTitle.text = "곡을 선택해 주세요"
        }

        for (index in removeIndexes.sortedDescending()) {
            playlist.removeAt(index)
            songNames.removeAt(index)
        }

        if (!currentRemoved && oldIndex >= 0) {

            currentIndex =
                oldIndex - removeIndexes.count { it < oldIndex }

            if (currentIndex in songNames.indices) {
                songTitle.text = songNames[currentIndex]
            }
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
