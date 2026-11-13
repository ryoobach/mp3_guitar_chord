
package com.ryoo.mp3guitarchord

import android.Manifest
import android.app.Activity
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.OpenableColumns
import android.provider.MediaStore
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.View
import android.view.WindowInsets
import android.widget.*
import java.util.Locale
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {

    private val playlist = mutableListOf<Uri>()
    private val songNames = mutableListOf<String>()

    private data class Mp3BrowserEntry(
        val name: String,
        val uri: Uri? = null,
        val folderPath: String? = null
    )

    private val selectedBrowserFiles = linkedMapOf<String, Pair<Uri, String>>()
    private var browserCurrentPath = ""

    private var player: MediaPlayer? = null
    private var currentIndex = -1
    private var isPrepared = false
    private var isSeeking = false
    private var shuffleMode = false
    private var repeatMode = 0
    private var selectionMode = false
    private val selectedPlaylistItems = mutableSetOf<Int>()

    private lateinit var elapsedTimeText: TextView
    private lateinit var totalTimeText: TextView
    private lateinit var songTitle: TextView
    private var titleMarqueeAnimator: ValueAnimator? = null
    @Volatile private var lastFftReceivedMs = 0L
    private val chordSlots = mutableListOf<TextView>()
    private var candidateChord: String? = null
    private var candidateChordFrames = 0
    private var candidateChordSinceMs = 0L
    private val recentChordCandidates = mutableListOf<Pair<String, Long>>()
    private var displayedChord: String? = null
    private val chordHistory = mutableListOf<String>()
    // Invalidates queued FFT callbacks whenever the audio track changes.
    private var chordAnalysisGeneration = 0L
    private var lastIcyMetadataTitle: String? = null

    // A lightweight bass-onset tracker estimates the beat interval for smooth chord motion.
    private var previousBassEnergy = 0.0
    private var smoothedBassEnergy = 0.0
    private var lastBeatPulseMs = 0L
    private val recentBeatIntervals = mutableListOf<Long>()
    @Volatile private var estimatedBeatIntervalMs = 600L
    private lateinit var analyzerView: AudioVisualizerView
    private var audioVisualizer: Visualizer? = null
    private var icyMetadataReader: IcyMetadataReader? = null
    private lateinit var playButton: Button
    private lateinit var shuffleButton: Button
    private lateinit var repeatButton: Button
    private lateinit var progressBar: TrackSpectrumView
    private lateinit var progressPercentText: TextView
    private lateinit var volumeBar: HorizontalVolumeBarView
    private lateinit var volumeValueText: TextView
    private lateinit var playlistView: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var selectButton: Button

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
                            progressBar.setDuration(duration)
                            progressBar.setPosition(position)
                        }
                        updateTimeDisplay(position, duration)
                        updateProgressPercent(position, duration)
                    }
                } catch (_: Exception) {
                }
            }

            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        restorePlaylist()
        configureSystemBars()
        createScreen()
        updateModeButtons()
        handler.post(updateTask)
    }

    // 상태 표시줄과 내비게이션 바의 아이콘을 어두운 앱 배경에 맞춰
    // 항상 밝게 표시합니다. Android 15의 투명 시스템 바에서도 적용됩니다.
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // 화면 회전은 화면 배치만 다시 만들고, 재생기와 재생목록은 유지합니다.
        configureSystemBars()
        isSeeking = false
        chordSlots.clear()
        createScreen()

        adapter.notifyDataSetChanged()
        updateModeButtons()
        renderChordHistory(animate = false)

        if (currentIndex in songNames.indices) {
            setScrollingSongTitle(songNames[currentIndex])
        } else {
            setScrollingSongTitle("")
        }

        selectButton.setTextColor(
            if (selectionMode) Color.rgb(255, 220, 115) else Color.WHITE
        )
        selectButton.background = roundedBackground(
            if (selectionMode) Color.rgb(83, 66, 31) else Color.rgb(35, 65, 88),
            if (selectionMode) Color.rgb(255, 220, 115) else Color.rgb(64, 103, 130)
        )

        val mp = player
        playButton.text = when {
            mp == null -> "▶"
            !isPrepared -> "…"
            mp.isPlaying -> "Ⅱ"
            else -> "▶"
        }

        if (mp != null && isPrepared) {
            try {
                val duration = mp.duration
                val position = mp.currentPosition
                if (duration > 0) {
                    progressBar.setDuration(duration)
                    progressBar.setPosition(position)
                }
                updateTimeDisplay(position, duration)
                updateProgressPercent(position, duration)
            } catch (_: Exception) {
                // 재생 중인 MediaPlayer 상태는 그대로 유지합니다.
            }
        } else {
        }
    }

    private fun configureSystemBars() {
        val appBackground = Color.rgb(8, 15, 27)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = appBackground
            window.navigationBarColor = appBackground
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility and
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility and
                    View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
        }
    }

    // --------------------------------------------------
    // 화면 구성 - 휴대폰 / 태블릿 자동 대응
    // --------------------------------------------------

    private fun createScreen() {

        chordSlots.clear()

        val widthDp = resources.configuration.screenWidthDp
        val heightDp = resources.configuration.screenHeightDp

        val isTablet = widthDp >= 600
        val isCompact = heightDp < 650 || widthDp < 360

        val panelHeight = when {
            isCompact -> 126
            isTablet -> 174
            else -> 150
        }

        val timeFontSize = when {
            isCompact -> 22f
            isTablet -> 31f
            else -> 27f
        }

        // 기타 코드 글씨를 한 번 더 눈에 띄게 축소합니다.
        val chordFontSize = when {
            isCompact -> 22f
            isTablet -> 27f
            else -> 24f
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
                setMargins(0, 0, dp(4), 0)
            }
        )

        val topTitle = TextView(this).apply {
            text = "Easy MP3 Guitar Chord"
            textSize = when {
                isCompact -> 16f
                isTablet -> 22f
                else -> 18f
            }
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(85, 213, 255))
            isSingleLine = true
            includeFontPadding = false
        }

        topBar.addView(
            topTitle,
            LinearLayout.LayoutParams(0, dp(40), 1f)        )

        val homeButton = makeButton("⌂").apply {
            textSize = 10f
            contentDescription = "홈 화면으로 이동"
            setOnClickListener {
                moveTaskToBack(true)
            }
        }

        val developerButton = makeButton("ⓘ").apply {
            textSize = 10f
            contentDescription = "개발자 정보"
            setOnClickListener {
                showDeveloperInfo()
            }
        }

        val exitButton = makeButton("×").apply {
            textSize = 12f
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
                dp(if (isCompact) 46 else 50)
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
            setPadding(dp(3), dp(4), dp(3), dp(4))
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
            text = "00:00"
            textSize = if (isCompact) 12f else 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(180, 195, 210))
            isSingleLine = true
            includeFontPadding = false
        }

        // 첫째 줄: 현재 재생 시간
        timeArea.addView(
            elapsedTimeText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.05f
            )
        )

        // 둘째 줄: 전체 재생 시간
        timeArea.addView(
            totalTimeText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                0.8f
            )
        )

        // 전체 시간 바로 아래에 작게 표시하는 실시간 오디오 스펙트럼
        analyzerView = AudioVisualizerView(this)
        timeArea.addView(
            analyzerView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.15f
            ).apply {
                setMargins(dp(4), dp(2), dp(4), 0)
            }
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
            text = "선택한 곡이 없습니다"
            textSize = if (isCompact) 12f else 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setTextColor(Color.rgb(245, 214, 107))
            isSingleLine = true
            setHorizontallyScrolling(true)
            ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            isFocusable = true
            isFocusableInTouchMode = true
            isSelected = true
            includeFontPadding = false
        }

        chordArea.addView(
            songTitle,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(if (isCompact) 22 else 27)
            )
        )

        val chordRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        // 최근 검출된 코드 5개를 왼쪽에서 오른쪽으로 흘러가듯 표시합니다.
        repeat(5) { index ->
            val slot = TextView(this).apply {
                text = "—"
                textSize = if (index == 2) chordFontSize else chordFontSize * 0.78f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(
                    if (index == 2) Color.rgb(85, 213, 255)
                    else Color.rgb(125, 151, 174)
                )
                includeFontPadding = false
                maxLines = 1
            }
            chordSlots.add(slot)
            chordRow.addView(
                slot,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            )
        }

        chordArea.addView(
            chordRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(if (isCompact) 44 else 50)
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
            setPadding(0, 0, dp(4), 0)        }

        volumeRow.addView(volumeLabel)

        val savedVolume = getSharedPreferences("player_settings", MODE_PRIVATE)
            .getInt("volume_percent", 80).coerceIn(0, 100)
        volumeBar = HorizontalVolumeBarView(this, savedVolume) { value ->
            val volume = value / 100f
            player?.setVolume(volume, volume)
            volumeValueText.text = "$value%"
            getSharedPreferences("player_settings", MODE_PRIVATE)
                .edit().putInt("volume_percent", value).apply()
        }
        volumeRow.addView(
            volumeBar,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                setMargins(dp(3), 0, dp(3), 0)
            }
        )

        volumeValueText = TextView(this).apply {
            text = "$savedVolume%"
            textSize = if (isCompact) 10f else 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(85, 213, 255))
            isSingleLine = true
        }
        volumeRow.addView(
            volumeValueText,
            LinearLayout.LayoutParams(dp(38), LinearLayout.LayoutParams.MATCH_PARENT)
        )

        chordArea.addView(
            volumeRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(if (isCompact) 26 else 30)
            ).apply {
                topMargin = dp(4)
            }
        )

        upperArea.addView(
            chordArea,
            LinearLayout.LayoutParams(0, dp(panelHeight), 3f)
        )

        playerPanel.addView(upperArea)

        // 실시간 스펙트럼은 왼쪽 시간 표시 영역의 전체 재생 시간 아래에 작게 표시합니다.

        // 진행 바

        progressBar = TrackSpectrumView(this).apply {
            setOnSeekPositionChanged { position ->
                if (isPrepared && (player?.duration ?: 0) > 0) {
                    updateTimeDisplay(position, player?.duration ?: 0)
                    updateProgressPercent(position, player?.duration ?: 0)
                }
            }
            setOnSeekFinished { position ->                if (isPrepared && (player?.duration ?: 0) > 0) {
                    player?.seekTo(position)
                }
                isSeeking = false
            }
            setOnSeekStarted {
                isSeeking = true
            }
        }

        val progressRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.rgb(16, 31, 50))
        }
        progressRow.addView(
            progressBar,
            LinearLayout.LayoutParams(0, dp(if (isCompact) 31 else 36), 1f)
        )
        progressPercentText = TextView(this).apply {
            text = "0%"
            textSize = if (isCompact) 10f else 11f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(85, 213, 255))
            isSingleLine = true
        }
        progressRow.addView(
            progressPercentText,
            LinearLayout.LayoutParams(dp(43), dp(if (isCompact) 31 else 36))
        )
        playerPanel.addView(progressRow)

        // 재생 버튼

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(3), dp(2), dp(3), dp(2))
        }

        shuffleButton = makeButton("SHF").apply { contentDescription = "셔플" }
        val first = makeButton("⏮").apply { contentDescription = "처음곡" }
        val previous = makeButton("|◀").apply { contentDescription = "이전곡" }
        playButton = makeButton("▶").apply {
            contentDescription = "재생 또는 일시정지"
            textSize = 22f
        }
        val stop = makeButton("■").apply { contentDescription = "정지" }
        val next = makeButton("▶|").apply { contentDescription = "다음곡" }
        val last = makeButton("⏭").apply { contentDescription = "마지막곡" }
        repeatButton = makeButton("RPT").apply { contentDescription = "반복 재생" }

        shuffleButton.setOnClickListener {
            shuffleMode = !shuffleMode
            updateModeButtons()
        }
        first.setOnClickListener { playFirstTrack() }
        previous.setOnClickListener { playPrevious() }
        playButton.setOnClickListener { togglePlayback() }
        stop.setOnClickListener { stopPlayback() }
        next.setOnClickListener { playNext() }
        last.setOnClickListener { playLastTrack() }
        repeatButton.setOnClickListener {
            repeatMode = (repeatMode + 1) % 3
            updateModeButtons()
        }

        val controlHeight = if (isCompact) 39 else 44
        controls.addView(shuffleButton, controlParams(controlHeight))
        controls.addView(first, controlParams(controlHeight))
        controls.addView(previous, controlParams(controlHeight))
        controls.addView(
            playButton,
            LinearLayout.LayoutParams(0, dp(controlHeight), 1.15f).apply {
                setMargins(dp(2), 0, dp(2), 0)
            }
        )
        controls.addView(stop, controlParams(controlHeight))
        controls.addView(next, controlParams(controlHeight))
        controls.addView(last, controlParams(controlHeight))
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
            // 체크박스는 평상시 표시하지 않고, SEL 모드에서 행 전체를 선택 표시합니다.
            choiceMode = ListView.CHOICE_MODE_NONE
            setBackgroundColor(Color.rgb(16, 31, 50))
            setCacheColorHint(Color.TRANSPARENT)
            divider = android.graphics.drawable.ColorDrawable(
                Color.rgb(52, 75, 97)
            )
            dividerHeight = dp(1)

            setOnItemClickListener { _, _, position, _ ->
                if (selectionMode) {
                    if (!selectedPlaylistItems.add(position)) {
                        selectedPlaylistItems.remove(position)
                    }
                    this@MainActivity.adapter.notifyDataSetChanged()
                } else {
                    playTrack(position)
                }
            }
        }

        adapter = object : ArrayAdapter<String>(            this,
            android.R.layout.simple_list_item_1,
            songNames
        ) {
            override fun getView(
                position: Int,
                convertView: View?,
                parent: android.view.ViewGroup
            ): View {
                val label = (super.getView(position, convertView, parent) as TextView)
                val isSelected = position in selectedPlaylistItems
                val isCurrentTrack = position == currentIndex && player != null

                label.text = songNames[position]
                label.textSize = 14f
                label.typeface = if (isSelected || isCurrentTrack) {
                    Typeface.DEFAULT_BOLD
                } else {
                    Typeface.DEFAULT
                }
                label.setPadding(dp(12), dp(9), dp(8), dp(9))

                // 현재 재생 곡은 청록색, SEL 모드에서 고른 곡은 금색으로 구분합니다.
                label.setTextColor(
                    when {
                        isSelected -> Color.rgb(255, 220, 115)
                        isCurrentTrack -> Color.rgb(85, 213, 255)
                        else -> Color.rgb(245, 245, 245)
                    }
                )
                label.setBackgroundColor(
                    when {
                        isSelected -> Color.rgb(83, 66, 31)
                        isCurrentTrack -> Color.rgb(22, 61, 83)
                        else -> Color.rgb(16, 31, 50)
                    }
                )

                return label
            }
        }

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
        selectButton = makeButton("SEL")

        add.setOnClickListener {
            showAddMenu()
        }

        remove.setOnClickListener {
            removeSelected()
        }

        selectButton.setOnClickListener {
            toggleSelectionMode()
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
            selectButton,
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

        // Android 15 이상(targetSdk 35)의 Edge-to-Edge 화면에서
        // 상태 표시줄과 내비게이션 바가 앱 UI를 가리지 않도록 여백을 적용합니다.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            root.setOnApplyWindowInsetsListener { view, windowInsets ->
                val systemBars = windowInsets.getInsets(
                    WindowInsets.Type.systemBars() or
                        WindowInsets.Type.displayCutout()
                )

                view.setPadding(
                    dp(8) + systemBars.left,
                    dp(4) + systemBars.top,
                    dp(8) + systemBars.right,
                    dp(4) + systemBars.bottom
                )

                windowInsets
            }
        }

        setContentView(root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            root.requestApplyInsets()
        }
    }

    // --------------------------------------------------
    // 실제 앱 아이콘
    // --------------------------------------------------

    private fun createAppIcon(size: Int): ImageView {

        val bitmap = Bitmap.createBitmap(108, 108, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 일렉트릭 펜더 스트라토캐스터를 떠올릴 수 있는
        // 더블 컷어웨이 바디, 넥, 헤드와 픽업을 표현합니다.
        paint.color = Color.rgb(8, 15, 27)
        canvas.drawRoundRect(RectF(1f, 1f, 107f, 107f), 18f, 18f, paint)

        // 기타 전체를 살짝 기울여 실제 일렉트릭 기타를 비스듬히 놓은 느낌을 줍니다.
        canvas.save()
        canvas.rotate(-9f, 54f, 54f)

        val neck = Path().apply {
            moveTo(48f, 8f)
            lineTo(60f, 8f)
            lineTo(62f, 57f)
            lineTo(46f, 57f)
            close()
        }
        paint.color = Color.rgb(205, 157, 99)
        canvas.drawPath(neck, paint)

        // 지판과 프렛
        paint.color = Color.rgb(35, 39, 47)
        canvas.drawRect(49f, 9f, 59f, 56f, paint)
        paint.color = Color.rgb(224, 218, 197)
        paint.strokeWidth = 1f
        for (y in 17..49 step 6) {
            canvas.drawLine(49f, y.toFloat(), 59f, y.toFloat(), paint)
        }
        for (x in 51..57 step 3) {
            canvas.drawLine(x.toFloat(), 9f, x.toFloat(), 56f, paint)
        }

        // 스트라토캐스터 특유의 헤드스톡
        val head = Path().apply {
            moveTo(48f, 10f)
            lineTo(45f, 4f)
            lineTo(49f, 2f)
            lineTo(67f, 5f)
            lineTo(65f, 12f)
            lineTo(59f, 15f)
            close()
        }
        paint.color = Color.rgb(218, 184, 133)
        canvas.drawPath(head, paint)

        // 더블 컷어웨이 바디
        val body = Path().apply {
            moveTo(47f, 48f)
            cubicTo(42f, 43f, 35f, 39f, 29f, 42f)
            cubicTo(24f, 45f, 25f, 53f, 31f, 58f)
            cubicTo(25f, 63f, 23f, 73f, 27f, 84f)
            cubicTo(31f, 96f, 43f, 101f, 54f, 97f)
            cubicTo(65f, 101f, 77f, 96f, 81f, 84f)
            cubicTo(85f, 73f, 83f, 63f, 77f, 58f)
            cubicTo(83f, 53f, 84f, 45f, 79f, 42f)
            cubicTo(73f, 39f, 66f, 43f, 61f, 48f)
            lineTo(60f, 57f)
            lineTo(48f, 57f)
            close()
        }
        paint.color = Color.rgb(205, 48, 48)
        canvas.drawPath(body, paint)
        paint.color = Color.rgb(255, 139, 116)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.4f
        canvas.drawPath(body, paint)
        paint.style = Paint.Style.FILL

        // 픽가드와 3개의 싱글코일 픽업
        val pickguard = Path().apply {
            moveTo(42f, 57f)
            lineTo(66f, 57f)
            lineTo(72f, 76f)
            lineTo(62f, 84f)
            lineTo(42f, 79f)
            lineTo(36f, 65f)
            close()
        }
        paint.color = Color.rgb(239, 226, 202)
        canvas.drawPath(pickguard, paint)
        paint.color = Color.rgb(65, 72, 82)
        for (y in listOf(60f, 66f, 72f)) {
            canvas.drawRoundRect(RectF(46f, y, 62f, y + 3f), 1f, 1f, paint)
        }

        // 브리지와 조절 노브
        paint.color = Color.rgb(190, 190, 183)
        canvas.drawRect(45f, 77f, 64f, 80f, paint)
        paint.color = Color.rgb(245, 214, 107)
        canvas.drawCircle(70f, 65f, 2.2f, paint)
        canvas.drawCircle(72f, 72f, 2.2f, paint)

        // 6개 줄
        paint.color = Color.rgb(255, 246, 220)
        paint.strokeWidth = 0.7f
        for (x in listOf(50f, 52f, 54f, 56f, 58f, 60f)) {
            canvas.drawLine(x, 7f, x - 1f, 82f, paint)
        }

        // 기타 바디 안쪽에 앱 이름을 선명하게 표시합니다.
        paint.color = Color.rgb(8, 15, 27)
        canvas.drawRoundRect(RectF(27f, 83f, 81f, 96f), 2f, 2f, paint)
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 6.2f
        canvas.drawText("EASY MP3", 54f, 89f, paint)
        paint.textSize = 5.4f
        canvas.drawText("GUITAR CHORD", 54f, 94f, paint)
        canvas.restore()

        return ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageBitmap(Bitmap.createScaledBitmap(bitmap, size, size, true))
            contentDescription = "일렉트릭 스트라토캐스터 기타 모양의 Easy MP3 Guitar Chord 앱 아이콘"
        }
    }

    // --------------------------------------------------
    // 앱 정보
    // --------------------------------------------------

    private fun showDeveloperInfo() {

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            isClickable = true
            isFocusable = true
            background = roundedBackground(
                Color.rgb(13, 26, 42),
                Color.rgb(52, 97, 122)
            )
        }

        content.addView(
            createAppIcon(dp(48)),
            LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                setMargins(0, 0, dp(8), 0)
            }
        )

        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "알 수 없음"
        } catch (_: Exception) {
            "알 수 없음"
        }

        val title = TextView(this).apply {
            text = "Easy MP3 Guitar Chord"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(85, 213, 255))
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
            includeFontPadding = false
        }
        textColumn.addView(title)

        val version = TextView(this).apply {
            text = "Version $versionName"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(180, 195, 210))
            isSingleLine = true
            includeFontPadding = false
            setPadding(0, dp(3), 0, 0)
        }
        textColumn.addView(version)

        val developer = TextView(this).apply {
            text = "Ryoo Jaesang & 최피디"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(245, 214, 107))
            setPadding(0, dp(5), 0, 0)
            isSingleLine = true
            includeFontPadding = false
        }
        textColumn.addView(developer)

        content.addView(
            textColumn,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        val dialog = AlertDialog.Builder(this)
            .setView(content)
            .create()

        content.setOnClickListener { dialog.dismiss() }
        dialog.setCanceledOnTouchOutside(true)
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(
            android.R.color.transparent
        )
        dialog.window?.setLayout(dp(280), LinearLayout.LayoutParams.WRAP_CONTENT)
    }

    // --------------------------------------------------
    // 하단 브랜딩
    // --------------------------------------------------

    private fun createBottomBrand(isCompact: Boolean): LinearLayout {

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(3), 0, dp(2), 0)
        }

        // 하단에도 런처에 등록된 실제 앱 아이콘을 표시합니다.
        row.addView(
            createAppIcon(dp(if (isCompact) 27 else 32)),
            LinearLayout.LayoutParams(
                dp(if (isCompact) 27 else 32),
                dp(if (isCompact) 27 else 32)
            ).apply {
                setMargins(0, 0, dp(5), 0)
            }
        )

        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(this).apply {
            text = "Easy MP3 Guitar Chord"
            textSize = if (isCompact) 8f else 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(85, 213, 255))
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
            includeFontPadding = false
        }

        val developer = TextView(this).apply {
            text = "Ryoo Jaesang & 최피디"
            textSize = if (isCompact) 6f else 8f
            setTextColor(Color.rgb(180, 195, 210))
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
            includeFontPadding = false
        }

        textColumn.addView(
            title,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        textColumn.addView(
            developer,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        row.addView(
            textColumn,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
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
                formatTime(duration)
            } else {
                "LIVE"
            }
        updateProgressPercent(position, duration)
    }

    private fun updateProgressPercent(position: Int, duration: Int) {
        if (!::progressPercentText.isInitialized) return
        progressPercentText.text = if (duration > 0) {
            val percent = ((position.coerceAtLeast(0).toLong() * 100L) / duration)
                .toInt().coerceIn(0, 100)
            "$percent%"
        } else {
            "—%"
        }
    }

    private fun setScrollingSongTitle(title: String) {
        if (!::songTitle.isInitialized) return

        titleMarqueeAnimator?.cancel()
        titleMarqueeAnimator = null
        songTitle.animate().cancel()
        songTitle.scrollTo(0, 0)

        val cleanTitle = title.trim().ifBlank { "선택한 곡이 없습니다" }
        // Repeat the title so even shorter names have enough width to trigger
        // Android's native marquee. START gravity lets it move from right to left.
        songTitle.text = "   $cleanTitle     •     $cleanTitle     •     $cleanTitle   "
        songTitle.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        songTitle.isSingleLine = true
        songTitle.setHorizontallyScrolling(true)
        songTitle.ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
        songTitle.marqueeRepeatLimit = -1
        songTitle.isFocusable = true
        songTitle.isFocusableInTouchMode = true
        songTitle.isSelected = false
        songTitle.requestLayout()
        songTitle.post {
            if (::songTitle.isInitialized) {
                songTitle.isSelected = true
                songTitle.requestFocus()
                songTitle.invalidate()
            }
        }
    }

    private fun resetTimeDisplay() {
        elapsedTimeText.text = "00:00"
        totalTimeText.text = "00:00"
        updateProgressPercent(0, 0)
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
        val width = if (isCompact) 18 else 20
        val height = if (isCompact) 18 else 20

        return LinearLayout.LayoutParams(dp(width), dp(height)).apply {
            setMargins(dp(1), 0, dp(1), 0)
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
            val saved = getSharedPreferences("player_settings", MODE_PRIVATE)
                .getString("last_stream_url", "http://live09.inlive.co.kr:11640")
            setText(saved)
            setSelection(text.length)
            hint = "http://live09.inlive.co.kr:11640"
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

        val normalizedAddress = if (address.contains("://")) address else "http://$address"
        val uri = try {
            Uri.parse(normalizedAddress)
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

        getSharedPreferences("player_settings", MODE_PRIVATE).edit()
            .putString("last_stream_url", uri.toString()).apply()
        playlist.add(uri)
        songNames.add("RADIO | ${uri.host ?: "인터넷 방송"}")

        adapter.notifyDataSetChanged()

        if (currentIndex == -1) {
            currentIndex = 0
            setScrollingSongTitle(songNames[0])
            adapter.notifyDataSetChanged()
        }

        savePlaylist()

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
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(permission), 102)
            return
        }

        browserCurrentPath = ""
        selectedBrowserFiles.clear()
        showMp3Browser()
    }

    /**
     * Displays a music-only browser. MediaStore supplies MP3 files and their
     * containing folders, so photos, PDFs and unrelated documents never appear.
     */
    private fun showMp3Browser() {
        val entries = queryMp3BrowserEntries(browserCurrentPath)
        val labels = mutableListOf<String>()
        val hasParent = browserCurrentPath.isNotEmpty()

        if (hasParent) labels.add("⬆  상위 폴더")
        entries.forEach { entry ->
            if (entry.folderPath != null) {
                labels.add("📁  ${entry.name}")
            } else {
                val key = entry.uri?.toString().orEmpty()
                val checked = selectedBrowserFiles.containsKey(key)
                labels.add("${if (checked) "☑" else "□"}  ${entry.name}")
            }
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(4))
        }

        val location = TextView(this).apply {
            text = if (browserCurrentPath.isBlank()) {
                "내장 저장공간 · MP3가 있는 폴더"
            } else {
                "/${browserCurrentPath.trimEnd('/')}"
            }
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setPadding(0, 0, 0, dp(8))
        }
        content.addView(location)

        val listView = ListView(this)
        content.addView(
            listView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(350)
            )
        )

        val emptyMessage = TextView(this).apply {
            text = "이 위치에는 MP3 파일이 없습니다."
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
        }
        content.addView(
            emptyMessage,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(100)
            )
        )
        listView.emptyView = emptyMessage
        listView.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            labels
        )

        val dialog = AlertDialog.Builder(this)
            .setTitle("MP3 파일 선택")
            .setView(content)
            .setPositiveButton("재생목록에 추가 (${selectedBrowserFiles.size})", null)
            .setNegativeButton("취소") { _, _ ->
                selectedBrowserFiles.clear()
            }
            .create()

        dialog.setOnShowListener {
            val addButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            addButton.isEnabled = selectedBrowserFiles.isNotEmpty()
            addButton.setOnClickListener {
                addSelectedBrowserFiles()
                selectedBrowserFiles.clear()
                dialog.dismiss()
            }

            listView.setOnItemClickListener { _, _, position, _ ->
                if (hasParent && position == 0) {
                    browserCurrentPath = browserCurrentPath
                        .trimEnd('/')
                        .substringBeforeLast('/', "")
                        .let { if (it.isBlank()) "" else "$it/" }
                    dialog.dismiss()
                    showMp3Browser()
                    return@setOnItemClickListener
                }

                val entryIndex = position - if (hasParent) 1 else 0
                val entry = entries.getOrNull(entryIndex) ?: return@setOnItemClickListener

                if (entry.folderPath != null) {
                    browserCurrentPath = entry.folderPath
                } else {
                    val uri = entry.uri ?: return@setOnItemClickListener
                    val key = uri.toString()
                    if (selectedBrowserFiles.containsKey(key)) {
                        selectedBrowserFiles.remove(key)
                    } else {
                        selectedBrowserFiles[key] = uri to entry.name
                    }
                }

                dialog.dismiss()
                showMp3Browser()
            }
        }

        dialog.show()
    }

    /**
     * Returns only folders that contain MP3 files and MP3 files in the
     * currently opened folder. Other media and document types are excluded.
     */
    private fun queryMp3BrowserEntries(path: String): List<Mp3BrowserEntry> {
        val folders = sortedSetOf<String>(String.CASE_INSENSITIVE_ORDER)
        val files = mutableListOf<Mp3BrowserEntry>()

        val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.MediaColumns.RELATIVE_PATH
            )
        } else {
            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.DATA
            )
        }

        try {
            contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Audio.Media.DISPLAY_NAME} COLLATE NOCASE ASC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val pathColumn = cursor.getColumnIndexOrThrow(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.MediaColumns.RELATIVE_PATH
                    } else {
                        MediaStore.Audio.Media.DATA
                    }
                )

                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameColumn) ?: continue
                    if (!name.endsWith(".mp3", ignoreCase = true)) continue

                    val storedPath = cursor.getString(pathColumn).orEmpty()
                    val relativePath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        storedPath.trimStart('/')
                    } else {
                        val storageRoot = android.os.Environment
                            .getExternalStorageDirectory().absolutePath.trimEnd('/') + "/"
                        val relativeFile = storedPath.removePrefix(storageRoot)
                        val parent = relativeFile.substringBeforeLast('/', "")
                        if (parent.isBlank()) "" else "$parent/"
                    }

                    if (!relativePath.startsWith(path, ignoreCase = true)) continue

                    val remainder = relativePath.substring(path.length)
                    if (remainder.isNotEmpty()) {
                        val folderName = remainder.substringBefore('/')
                        if (folderName.isNotBlank()) folders.add(folderName)
                        continue
                    }

                    val id = cursor.getLong(idColumn)
                    val uri = Uri.withAppendedPath(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id.toString()
                    )
                    files.add(Mp3BrowserEntry(name = name, uri = uri))
                }
            }
        } catch (_: SecurityException) {
            Toast.makeText(
                this,
                "오디오 파일 접근 권한을 확인해 주세요.",
                Toast.LENGTH_LONG
            ).show()
        } catch (_: Exception) {
            Toast.makeText(
                this,
                "MP3 목록을 읽지 못했습니다.",
                Toast.LENGTH_LONG
            ).show()
        }

        val result = mutableListOf<Mp3BrowserEntry>()
        folders.forEach { folder ->
            result.add(
                Mp3BrowserEntry(
                    name = folder,
                    folderPath = path + folder + "/"
                )
            )
        }
        result.addAll(files)
        return result
    }

    private fun addSelectedBrowserFiles() {
        var addedCount = 0

        selectedBrowserFiles.values.forEach { (uri, name) ->
            if (!playlist.contains(uri)) {
                playlist.add(uri)
                songNames.add(name)
                addedCount++
            }
        }

        adapter.notifyDataSetChanged()

        if (currentIndex == -1 && playlist.isNotEmpty()) {
            currentIndex = 0
            setScrollingSongTitle(songNames[0])
            adapter.notifyDataSetChanged()
        }

        savePlaylist()

        Toast.makeText(
            this,
            if (addedCount > 0) "${addedCount}개 MP3 파일을 추가했습니다."
            else "새로 추가할 MP3 파일이 없습니다.",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun getFileName(uri: Uri): String {

        var name = uri.lastPathSegment ?: "Audio"

        try {
            contentResolver.query(
                uri,
                null,
                null,                null,
                null
            )?.use { cursor ->

                if (cursor.moveToFirst()) {
                    val index =
                        cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)

                    if (index >= 0) {                        name = cursor.getString(index) ?: name
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
        savePlaylist()
        setScrollingSongTitle(songNames[index])
        playButton.text = "…"
        resetTimeDisplay()
        progressBar.reset()
        resetChordDisplay()

        val uri = playlist[index]
        val isStream = uri.scheme == "http" || uri.scheme == "https"

        if (!isStream) {
            startTrackPlayback(index, uri, uri.toString())
            return
        }

        // PLS/M3U 주소와 실제 스트림 주소를 모두 지원합니다.
        // 네트워크 확인은 UI 스레드를 막지 않도록 별도 스레드에서 수행합니다.
        Thread({
            try {
                val resolvedUrl = StreamAddressResolver.resolve(uri.toString())
                runOnUiThread {
                    if (currentIndex == index && playlist.getOrNull(index) == uri) {
                        startTrackPlayback(index, uri, resolvedUrl)
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    if (currentIndex == index && playlist.getOrNull(index) == uri) {
                        playButton.text = "▶"
                        Toast.makeText(
                            this,
                            "방송 주소를 읽을 수 없습니다. 주소를 확인해 주세요.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }, "Stream-Address-Resolver").apply {
            isDaemon = true
            start()
        }
    }

    private fun startTrackPlayback(index: Int, originalUri: Uri, playbackUrl: String) {
        try {
            val isStream = originalUri.scheme == "http" || originalUri.scheme == "https"
            val mp = MediaPlayer()
            player = mp
            isPrepared = false
            adapter.notifyDataSetChanged()

            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )

            if (isStream) {
                mp.setDataSource(playbackUrl)
            } else {
                mp.setDataSource(this, originalUri)
            }

            mp.setOnPreparedListener { prepared ->
                if (player !== prepared) return@setOnPreparedListener

                isPrepared = true
                val volume = volumeBar.value / 100f
                prepared.setVolume(volume, volume)

                if (prepared.duration > 0) {
                    progressBar.reset()
                    progressBar.setDuration(prepared.duration)
                }

                updateTimeDisplay(0, prepared.duration)
                prepared.start()
                lastFftReceivedMs = 0L
                attachAudioVisualizer(prepared.audioSessionId)
                handler.postDelayed({
                    if (player === prepared && isPrepared && prepared.isPlaying &&
                        lastFftReceivedMs == 0L &&
                        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                    ) {
                        attachAudioVisualizer(prepared.audioSessionId)
                    }
                }, 900L)
                if (isStream) {
                    startIcyMetadataReader(playbackUrl, prepared)
                }
                playButton.text = "Ⅱ"
            }

            mp.setOnCompletionListener { handleTrackCompletion() }
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
                progressBar.setPosition(0)
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
            progressBar.setPosition(0)
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

        playTrack(choices.random())    }

    private fun playFirstTrack() {
        if (playlist.isEmpty()) {
            Toast.makeText(this, "재생목록이 비어 있습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        playTrack(0)
    }

    private fun playLastTrack() {
        if (playlist.isEmpty()) {
            Toast.makeText(this, "재생목록이 비어 있습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        playTrack(playlist.lastIndex)
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

    private fun toggleSelectionMode() {
        selectionMode = !selectionMode
        selectedPlaylistItems.clear()

        selectButton.setTextColor(
            if (selectionMode) Color.rgb(255, 220, 115) else Color.WHITE
        )
        selectButton.background = roundedBackground(
            if (selectionMode) Color.rgb(83, 66, 31) else Color.rgb(35, 65, 88),
            if (selectionMode) Color.rgb(255, 220, 115) else Color.rgb(64, 103, 130)
        )

        adapter.notifyDataSetChanged()

        Toast.makeText(
            this,
            if (selectionMode) "삭제할 곡을 선택해 주세요." else "선택 모드를 종료했습니다.",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun removeSelected() {

        if (!selectionMode) {
            Toast.makeText(
                this,
                "먼저 SEL 버튼을 눌러 선택 모드로 전환해 주세요.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val removeIndexes = selectedPlaylistItems
            .filter { it in playlist.indices }
            .sortedDescending()

        if (removeIndexes.isEmpty()) {
            Toast.makeText(
                this,
                "삭제할 곡을 선택해 주세요.",
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
            progressBar.reset()
            resetChordDisplay()
            setScrollingSongTitle("")
        }

        for (index in removeIndexes) {
            playlist.removeAt(index)
            songNames.removeAt(index)
        }

        if (!currentRemoved && oldIndex >= 0) {
            currentIndex = oldIndex - removeIndexes.count { it < oldIndex }

            if (currentIndex in songNames.indices) {
                setScrollingSongTitle(songNames[currentIndex])
            }
        }

        selectedPlaylistItems.clear()
        selectionMode = false
        selectButton.setTextColor(Color.WHITE)
        selectButton.background = roundedBackground(
            Color.rgb(35, 65, 88),
            Color.rgb(64, 103, 130)
        )
        adapter.notifyDataSetChanged()

        savePlaylist()

        Toast.makeText(
            this,
            "${removeIndexes.size}개 항목을 삭제했습니다.",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun startIcyMetadataReader(streamUrl: String, expectedPlayer: MediaPlayer) {
        icyMetadataReader?.stop()
        lastIcyMetadataTitle = null
        val reader = IcyMetadataReader(streamUrl) { title ->
            runOnUiThread {
                if (player !== expectedPlayer || currentIndex !in songNames.indices) return@runOnUiThread

                // Internet radio keeps one MediaPlayer alive while songs change.
                // ICY StreamTitle can change several times during one station session.
                // Keep chord history intact; only a new playlist item resets analysis.

                // StreamTitle belongs only in the now-playing header.
                // Keep the playlist's saved station name and URL unchanged.
                setScrollingSongTitle(title)
            }
        }
        icyMetadataReader = reader
        reader.start()
    }

    private fun attachAudioVisualizer(audioSessionId: Int) {
        releaseAudioVisualizer()
        val analysisGeneration = chordAnalysisGeneration

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Toast.makeText(
                    this,
                    "기타 코드 표시를 위해 오디오 분석 권한을 허용해 주세요.",
                    Toast.LENGTH_LONG
                ).show()
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 210)
            }
            return
        }

        try {
            val visualizer = Visualizer(audioSessionId)
            val captureSize = Visualizer.getCaptureSizeRange()[1]
            visualizer.captureSize = captureSize

            visualizer.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int
                    ) {
                        // Chord analysis uses the actual FFT packets below.
                    }

                    override fun onFftDataCapture(
                        visualizer: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int
                    ) {
                        if (analysisGeneration != chordAnalysisGeneration ||
                            fft == null || fft.size < 8
                        ) return
                        lastFftReceivedMs = SystemClock.elapsedRealtime()
                        val detectedChord = ChordDetector.detectFft(fft, samplingRate)
                        if (detectedChord != null) {
                            runOnUiThread {
                                if (analysisGeneration == chordAnalysisGeneration) {
                                    updateDetectedChord(detectedChord)
                                }
                            }
                        }
                        val bars = FloatArray(48)
                        val usableBins = fft.size / 2
                        for (bar in bars.indices) {
                            val startBin = (1 + (bar.toDouble() / bars.size * (usableBins - 2)).toInt())
                                .coerceIn(1, usableBins - 1)
                            val endBin = (startBin + maxOf(1, usableBins / bars.size))
                                .coerceAtMost(usableBins)
                            var peak = 0.0
                            for (bin in startBin until endBin) {
                                val real = fft[2 * bin].toDouble()
                                val imaginary = fft[2 * bin + 1].toDouble()
                                peak = maxOf(peak, kotlin.math.sqrt(real * real + imaginary * imaginary))
                            }
                            bars[bar] = (peak / 72.0).toFloat().coerceIn(0.04f, 1f)
                        }
                        updateBeatTempo(fft, samplingRate)
                        val playbackPosition = try { player?.currentPosition ?: 0 } catch (_: Exception) { 0 }
                        val trackDuration = try { player?.duration ?: 0 } catch (_: Exception) { 0 }
                        runOnUiThread {
                            if (analysisGeneration != chordAnalysisGeneration) return@runOnUiThread
                            analyzerView.setSpectrum(bars)
                            if (trackDuration > 0) {
                                progressBar.recordSpectrum(playbackPosition, trackDuration, bars)
                            } else {
                                progressBar.recordLiveSpectrum(bars)
                            }
                        }
                    }
                },
                (Visualizer.getMaxCaptureRate() / 2).coerceAtLeast(1000),
                true,
                true
            )
            visualizer.enabled = true
            audioVisualizer = visualizer
        } catch (_: Exception) {
            analyzerView.setSpectrum(FloatArray(48))
            Toast.makeText(
                this,
                "오디오 분석을 시작하지 못했습니다. 앱의 오디오 권한을 확인해 주세요.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Estimates the beat interval from low-frequency spectral onsets.
     * It is intentionally smoothed and bounded so noisy audio cannot make
     * the chord animation jump between extreme speeds.
     */
    private fun updateBeatTempo(fft: ByteArray, samplingRateMilliHz: Int) {
        if (fft.size < 16 || samplingRateMilliHz <= 0) return

        val binCount = fft.size / 2
        val sampleRateHz = samplingRateMilliHz / 1000.0
        val binWidthHz = sampleRateHz / fft.size
        var bassEnergy = 0.0

        for (bin in 1 until binCount) {
            // Android Visualizer FFT bins are spaced at Fs / N, not Fs / (N/2 - 1).
            val frequency = bin * binWidthHz
            if (frequency > 180.0) break
            val real = fft[2 * bin].toDouble()
            val imaginary = fft[2 * bin + 1].toDouble()
            bassEnergy += kotlin.math.sqrt(real * real + imaginary * imaginary)
        }

        val now = SystemClock.elapsedRealtime()
        val flux = (bassEnergy - previousBassEnergy).coerceAtLeast(0.0)
        previousBassEnergy = previousBassEnergy * 0.72 + bassEnergy * 0.28

        val threshold = maxOf(5.0, smoothedBassEnergy * 0.16)
        val isOnset = flux > threshold &&
            bassEnergy > smoothedBassEnergy * 1.08 &&
            now - lastBeatPulseMs >= 260L

        smoothedBassEnergy = smoothedBassEnergy * 0.88 + bassEnergy * 0.12

        if (isOnset) {
            if (lastBeatPulseMs > 0L) {
                val interval = now - lastBeatPulseMs
                if (interval in 300L..1200L) {
                    recentBeatIntervals.add(interval)
                    if (recentBeatIntervals.size > 7) {
                        recentBeatIntervals.removeAt(0)
                    }
                    val sorted = recentBeatIntervals.sorted()
                    val median = sorted[sorted.size / 2]
                    estimatedBeatIntervalMs =
                        (estimatedBeatIntervalMs * 0.35 + median * 0.65)
                            .toLong()
                            .coerceIn(350L, 1000L)
                }
            }
            lastBeatPulseMs = now
        }
    }

    private var lastDisplayedChordMs = 0L

    private fun updateDetectedChord(chord: String) {
        val now = SystemClock.elapsedRealtime()

        // FFT estimates can flicker between adjacent candidates. Vote over a
        // short rolling window instead of requiring perfectly consecutive frames.
        recentChordCandidates.add(chord to now)
        // A long vote window carries the previous chord into the next section.
        // Shorten it so genuine changes in both MP3 and radio playback can surface.
        recentChordCandidates.removeAll { now - it.second > 650L }

        val voteGroups = recentChordCandidates.groupBy { it.first }
        val winner = voteGroups.maxByOrNull { entry ->
            entry.value.size * 1000000L + (entry.value.maxOfOrNull { it.second } ?: 0L)
        } ?: return
        val winningChord = winner.key
        val winningVotes = winner.value.size
        val firstWinningVote = winner.value.minOfOrNull { it.second } ?: now

        val stableLongEnough = winningVotes >= 2 && now - firstWinningVote >= 90L
        val minimumChangeInterval = (estimatedBeatIntervalMs * 0.22)
            .toLong()
            .coerceIn(140L, 260L)
        val changeCooldownPassed = lastDisplayedChordMs == 0L ||
            now - lastDisplayedChordMs >= minimumChangeInterval

        if (stableLongEnough && changeCooldownPassed && winningChord != displayedChord) {
            displayedChord = winningChord
            lastDisplayedChordMs = now
            candidateChord = winningChord
            candidateChordFrames = winningVotes
            candidateChordSinceMs = firstWinningVote
            chordHistory.add(winningChord)
            while (chordHistory.size > 5) chordHistory.removeAt(0)
            renderChordHistory(animate = true)
        }
    }

    private fun renderChordHistory(animate: Boolean = false) {
        if (chordSlots.size < 5) return

        // The newest/current chord stays in the middle. The two immediately
        // preceding chords fill the left side; the two earlier chords fill right.
        val count = chordHistory.size
        val current = chordHistory.lastOrNull()
        val slotChords = listOf(
            chordHistory.getOrNull(count - 3),
            chordHistory.getOrNull(count - 2),
            current,
            chordHistory.getOrNull(count - 4),
            chordHistory.getOrNull(count - 5)
        )

        chordSlots.forEachIndexed { index, slot ->
            val chord = slotChords[index]
            val hasChord = !chord.isNullOrBlank()
            val isCurrentChord = index == 2 && hasChord
            slot.text = chord ?: "—"

            val targetAlpha = when {
                isCurrentChord -> 1.0f
                hasChord -> 0.82f
                else -> 0.38f
            }

            slot.setTextColor(
                when {
                    isCurrentChord -> Color.rgb(85, 213, 255)
                    hasChord -> Color.rgb(180, 199, 216)
                    else -> Color.rgb(105, 125, 145)
                }
            )

            slot.animate().cancel()
            if (animate) {
                // Move the history gently into place at a tempo-related speed.
                slot.translationX = dp(10).toFloat()
                slot.alpha = targetAlpha * 0.55f
                slot.scaleX = if (isCurrentChord) 0.94f else 1.0f
                slot.scaleY = if (isCurrentChord) 0.94f else 1.0f
                slot.animate()
                    .translationX(0f)
                    .alpha(targetAlpha)
                    .scaleX(if (isCurrentChord) 1.08f else 1.0f)
                    .scaleY(if (isCurrentChord) 1.08f else 1.0f)
                    .setDuration((estimatedBeatIntervalMs * 0.42).toLong().coerceIn(180L, 360L))
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            } else {
                slot.translationX = 0f
                slot.alpha = targetAlpha
                slot.scaleX = if (isCurrentChord) 1.08f else 1.0f
                slot.scaleY = if (isCurrentChord) 1.08f else 1.0f
            }
        }
    }

    private fun resetChordDisplay(invalidateAnalysis: Boolean = true) {
        if (invalidateAnalysis) chordAnalysisGeneration++
        candidateChord = null
        candidateChordFrames = 0
        candidateChordSinceMs = 0L
        recentChordCandidates.clear()
        displayedChord = null
        lastDisplayedChordMs = 0L
        chordHistory.clear()
        previousBassEnergy = 0.0
        smoothedBassEnergy = 0.0
        lastBeatPulseMs = 0L
        recentBeatIntervals.clear()
        estimatedBeatIntervalMs = 600L
        if (chordSlots.isNotEmpty()) {
            chordSlots.forEach {
                it.animate().cancel()
                it.text = "—"
                it.translationX = 0f
                it.alpha = 1.0f
                it.setTextColor(Color.rgb(125, 151, 174))
            }
        }
    }

    private fun releaseAudioVisualizer() {
        try {
            audioVisualizer?.enabled = false
            audioVisualizer?.release()
        } catch (_: Exception) {
        }
        audioVisualizer = null
        if (::analyzerView.isInitialized) {
            analyzerView.setSpectrum(FloatArray(48))
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 102) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                browserCurrentPath = ""
                selectedBrowserFiles.clear()
                showMp3Browser()
            } else {
                Toast.makeText(
                    this,
                    "MP3 파일을 보려면 오디오 파일 접근 권한이 필요합니다.",
                    Toast.LENGTH_LONG
                ).show()
            }
        } else if (requestCode == 210) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                player?.let { if (isPrepared) attachAudioVisualizer(it.audioSessionId) }
            } else {
                Toast.makeText(
                    this,
                    "기타 코드 분석을 위해 오디오 권한을 허용해 주세요.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // --------------------------------------------------
    // 플레이어 정리 / 시간 표시
    // --------------------------------------------------

    private fun releaseCurrentPlayer() {

        icyMetadataReader?.stop()
        icyMetadataReader = null
        releaseAudioVisualizer()
        titleMarqueeAnimator?.cancel()
        titleMarqueeAnimator = null
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

    /**
     * Saves playlist URIs, display names and the selected row between launches.
     * SAF document URIs are retained only when Android granted persistent access.
     */
    private fun savePlaylist() {
        try {
            val entries = JSONArray()
            for (index in playlist.indices) {
                entries.put(JSONObject().apply {
                    put("uri", playlist[index].toString())
                    put("name", songNames.getOrNull(index).orEmpty())
                })
            }
            getSharedPreferences("player_settings", MODE_PRIVATE).edit()
                .putString("saved_playlist", entries.toString())
                .putInt("saved_current_index", currentIndex)
                .apply()
        } catch (_: Exception) {
        }
    }

    /** Restores saved entries before the playlist adapter is created. */
    private fun restorePlaylist() {
        val preferences = getSharedPreferences("player_settings", MODE_PRIVATE)
        val saved = preferences.getString("saved_playlist", null) ?: return
        try {
            val entries = JSONArray(saved)
            val persistedUris = contentResolver.persistedUriPermissions
                .filter { it.isReadPermission }
                .map { it.uri.toString() }
                .toSet()
            val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_AUDIO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            val hasMediaAudioPermission =
                checkSelfPermission(audioPermission) == PackageManager.PERMISSION_GRANTED

            for (index in 0 until entries.length()) {
                val entry = entries.optJSONObject(index) ?: continue
                val uriText = entry.optString("uri")
                if (uriText.isBlank()) continue

                val uri = Uri.parse(uriText)
                val isStream = uri.scheme == "http" || uri.scheme == "https"
                val isMediaStoreAudio = uri.authority == "media" && hasMediaAudioPermission
                if (!isStream && uriText !in persistedUris && !isMediaStoreAudio) continue

                if (!playlist.contains(uri)) {
                    val savedName = entry.optString("name")
                    playlist.add(uri)
                    // Do not restore old radio metadata that may have been saved
                    // with the wrong character encoding. Start with a clean station label;
                    // the live ICY title will replace it after playback begins.
                    songNames.add(
                        if (isStream) "RADIO | ${uri.host ?: "인터넷 방송"}"
                        else savedName.ifBlank { getFileName(uri) }
                    )
                }
            }

            currentIndex = preferences.getInt("saved_current_index", -1)
                .takeIf { it in playlist.indices }
                ?: if (playlist.isNotEmpty()) 0 else -1
        } catch (_: Exception) {
            playlist.clear()
            songNames.clear()
            currentIndex = -1
        }
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

        savePlaylist()
        handler.removeCallbacks(updateTask)
        releaseCurrentPlayer()

        super.onDestroy()
    }
}


/**
 * Reads ICY StreamTitle metadata from a parallel HTTP connection.
 * The audio itself continues to be played by MediaPlayer.
 */
private class IcyMetadataReader(
    private val streamUrl: String,
    private val onTitle: (String) -> Unit
) {
    @Volatile private var running = false
    @Volatile private var connection: HttpURLConnection? = null
    private var worker: Thread? = null

    fun start() {
        if (running) return
        running = true
        worker = Thread({
            var localConnection: HttpURLConnection? = null
            try {
                localConnection = (URL(streamUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                    setRequestProperty("Icy-MetaData", "1")
                    setRequestProperty("User-Agent", "EasyMP3GuitarChord/1.5")
                }
                connection = localConnection
                localConnection.connect()
                val interval = localConnection.getHeaderField("icy-metaint")?.toIntOrNull()
                    ?: localConnection.getHeaderField("Icy-MetaInt")?.toIntOrNull()
                    ?: return@Thread
                if (interval <= 0) return@Thread

                localConnection.inputStream.use { input ->
                    val audioBuffer = ByteArray(8192)
                    while (running) {
                        if (!readFully(input, audioBuffer, interval)) break
                        val lengthByte = input.read()
                        if (lengthByte < 0) break
                        val metadataLength = lengthByte * 16
                        if (metadataLength <= 0) continue
                        val metadata = ByteArray(metadataLength)
                        if (!readFully(input, metadata, metadataLength)) break
                        val declaredCharset = localConnection.getContentType()
                            ?.substringAfter("charset=", "")
                            ?.substringBefore(';')
                            ?.trim()
                            ?.trim('"', '\'')
                        val raw = decodeIcyMetadata(metadata, declaredCharset)
                            .trim('\u0000', ' ', '\r', '\n', '\t')
                        if (raw.isEmpty()) continue
                        val match = Regex("(?i)StreamTitle\\s*=\\s*['\\\"]([^'\\\"]*)['\\\"]")
                            .find(raw)
                        val title = match?.groupValues?.getOrNull(1)?.trim().orEmpty()
                        if (title.isNotEmpty()) onTitle(title)
                    }
                }
            } catch (_: Exception) {
                // Metadata is optional; playback continues if the station does not provide it.
            } finally {
                running = false
                try { localConnection?.disconnect() } catch (_: Exception) { }
                if (connection === localConnection) connection = null
            }
        }, "ICY-Metadata-Reader").apply {
            isDaemon = true
            start()
        }
    }


    /**
     * Radio stations do not all use UTF-8 for ICY metadata. Korean stations
     * commonly send MS949/EUC-KR bytes, so try the declared charset and rank
     * valid decodings by readable Hangul before falling back to UTF-8.
     */
    private fun decodeIcyMetadata(bytes: ByteArray, declaredCharset: String?): String {
        val candidates = linkedSetOf<String>()
        if (!declaredCharset.isNullOrBlank()) candidates.add(declaredCharset)
        candidates.add("UTF-8")
        candidates.add("MS949")
        candidates.add("EUC-KR")
        candidates.add("ISO-8859-1")

        var bestText = ""
        var bestScore = Int.MIN_VALUE

        for (charsetName in candidates) {
            val decoded = try {
                val decoder = java.nio.charset.Charset.forName(charsetName)
                    .newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
            } catch (_: Exception) {
                continue
            }

            val trimmed = decoded.trim('\u0000', ' ', '\r', '\n', '\t')
            if (trimmed.isEmpty()) continue

            val hangulCount = trimmed.count { it in '\uAC00'..'\uD7A3' }
            val replacementCount = trimmed.count { it == '\uFFFD' }
            val controlCount = trimmed.count { it.isISOControl() && it !in listOf('\r', '\n', '\t') }
            val score = hangulCount * 8 - replacementCount * 20 - controlCount * 5

            if (score > bestScore) {
                bestScore = score
                bestText = trimmed
            }
        }

        return bestText
    }

    private fun readFully(input: java.io.InputStream, buffer: ByteArray, count: Int): Boolean {
        var offset = 0
        while (offset < count && running) {
            val read = input.read(buffer, offset, minOf(buffer.size, count - offset))
            if (read < 0) return false
            offset += read
        }
        return offset == count
    }

    fun stop() {
        running = false
        try { connection?.disconnect() } catch (_: Exception) { }
        connection = null
        worker?.interrupt()
        worker = null
    }
}

/**
 * Resolves common internet-radio playlist formats to their real audio stream.
 * Direct audio URLs are returned unchanged (including HTTP redirects).
 */
private object StreamAddressResolver {
    fun resolve(inputUrl: String): String {
        var currentUrl = inputUrl
        repeat(4) {
            val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 10000
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Winamp/5.9.2")
                setRequestProperty("Accept", "*/*")
                setRequestProperty("Icy-MetaData", "0")
            }

            try {
                val status = connection.responseCode
                if (status !in 200..299) {
                    throw java.io.IOException("HTTP $status")
                }

                val finalUrl = connection.url.toString()
                val contentType = connection.contentType.orEmpty().lowercase(Locale.ROOT)
                val path = runCatching { URL(finalUrl).path.lowercase(Locale.ROOT) }.getOrDefault("")
                val isKnownPlaylist = path.endsWith(".pls") || path.endsWith(".m3u") ||
                    path.endsWith(".m3u8") || contentType.contains("scpls") ||
                    contentType.contains("mpegurl") || contentType.contains("playlist")

                val input = connection.inputStream
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                var total = 0
                while (total < 65536) {
                    val count = input.read(buffer, 0, minOf(buffer.size, 65536 - total))
                    if (count <= 0) break
                    output.write(buffer, 0, count)
                    total += count
                    if (!isKnownPlaylist && total >= 4096) break
                }
                input.close()

                val bytes = output.toByteArray()
                val text = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF").trim()
                val looksLikePlaylist = isKnownPlaylist ||
                    text.startsWith("[playlist]", ignoreCase = true) ||
                    text.startsWith("#EXTM3U", ignoreCase = true) ||
                    Regex("(?im)^File\\d+\\s*=").containsMatchIn(text)

                if (!looksLikePlaylist) return finalUrl

                val target = when {
                    text.startsWith("[playlist]", ignoreCase = true) ||
                        Regex("(?im)^File\\d+\\s*=").containsMatchIn(text) -> {
                        Regex("(?im)^File\\d+\\s*=\\s*(.+?)\\s*$")
                            .findAll(text)
                            .map { it.groupValues[1].trim().trim('"') }
                            .firstOrNull { it.isNotBlank() }
                    }
                    text.startsWith("#EXTM3U", ignoreCase = true) -> {
                        text.lineSequence()
                            .map { it.trim() }
                            .firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
                    }
                    else -> null
                } ?: throw java.io.IOException("Playlist has no stream URL")

                currentUrl = URL(URL(finalUrl), target).toString()
            } finally {
                connection.disconnect()
            }
        }
        return currentUrl
    }
}
