package com.fourvplayer.clx

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.Window
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.fourvplayer.clx.audio.AudioVisualizerManager
import com.fourvplayer.clx.model.AspectRatioMode
import com.fourvplayer.clx.model.EffectItem
import com.fourvplayer.clx.model.EffectType
import com.fourvplayer.clx.model.ExportConfig
import com.fourvplayer.clx.model.ExportFps
import com.fourvplayer.clx.model.ExportResolution
import com.fourvplayer.clx.model.LayerItem
import com.fourvplayer.clx.model.LayerType
import com.fourvplayer.clx.ui.AdjustBottomSheet
import com.fourvplayer.clx.ui.MockIconType
import com.fourvplayer.clx.ui.MockupIconDrawable
import com.fourvplayer.clx.ui.PreviewCanvasView
import java.util.Locale
import kotlin.math.abs

class MainActivity : ComponentActivity() {

    private val visualizerManager = AudioVisualizerManager()
    private var mediaPlayer: MediaPlayer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var exportConfig = ExportConfig()
    private val layers = mutableListOf<LayerItem>()
    private var selectedLayerIndex = 0

    private val undoStack = ArrayDeque<List<LayerItem>>()
    private val redoStack = ArrayDeque<List<LayerItem>>()

    private lateinit var previewView: PreviewCanvasView
    private lateinit var layerListContainer: LinearLayout
    private lateinit var layerScrollView: ScrollView
    private lateinit var playLoopIcon: ImageView
    private lateinit var undoIcon: ImageView
    private lateinit var redoIcon: ImageView
    private lateinit var projectTitleTv: TextView

    private var pendingTargetLayerForImage: LayerItem? = null
    private var isRebuildPending = false

    private val pickAudioLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            playAudioUri(uri)
        }
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            pushUndoState()
            val target = pendingTargetLayerForImage ?: layers.getOrNull(selectedLayerIndex)
            if (target != null) {
                target.mediaUriString = uri.toString()
            }
            pendingTargetLayerForImage = null
            refreshAll()
        }
    }

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installCrashCatcher()

        layers.clear()

        buildMockupUi()
        ensureStoragePermissions()
        refreshAll()
    }

    @SuppressLint("ApplySharedPref")
    private fun installCrashCatcher() {
        val prefs = getSharedPreferences("fv_crash_prefs", MODE_PRIVATE)
        val lastCrash = prefs.getString("last_crash", null)
        if (!lastCrash.isNullOrEmpty()) {
            prefs.edit().remove("last_crash").commit()
            AlertDialog.Builder(this)
                .setTitle("Crash Log")
                .setMessage(lastCrash)
                .setPositiveButton("Copy Error") { _, _ ->
                    val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("CrashLog", lastCrash))
                    Toast.makeText(this, "Error copied to clipboard", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Close", null)
                .show()
        }

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val stack = Log.getStackTraceString(throwable)
            prefs.edit().putString("last_crash", stack).commit()
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun ensureStoragePermissions() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed.add(Manifest.permission.READ_MEDIA_AUDIO)
            needed.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            needed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            requestPermissionsLauncher.launch(missing.toTypedArray())
        }
    }

    private fun pushUndoState() {
        if (undoStack.size >= 20) undoStack.removeFirst()
        undoStack.addLast(layers.map { it.copy(effects = it.effects.map { fx -> fx.copy() }.toMutableList()) })
        redoStack.clear()
        updateUndoRedoIcons()
    }

    private fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.addLast(layers.map { it.copy(effects = it.effects.map { fx -> fx.copy() }.toMutableList()) })
        val prev = undoStack.removeLast()
        layers.clear()
        layers.addAll(prev)
        refreshAll()
    }

    private fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(layers.map { it.copy(effects = it.effects.map { fx -> fx.copy() }.toMutableList()) })
        val next = redoStack.removeLast()
        layers.clear()
        layers.addAll(next)
        refreshAll()
    }

    private fun updateUndoRedoIcons() {
        if (::undoIcon.isInitialized) {
            undoIcon.setImageDrawable(
                MockupIconDrawable(
                    MockIconType.UNDO,
                    if (undoStack.isNotEmpty()) Color.WHITE else Color.parseColor("#4B5563")
                )
            )
        }
        if (::redoIcon.isInitialized) {
            redoIcon.setImageDrawable(
                MockupIconDrawable(
                    MockIconType.REDO,
                    if (redoStack.isNotEmpty()) Color.WHITE else Color.parseColor("#4B5563")
                )
            )
        }
    }

    private fun makeIconButton(type: MockIconType, sizeDp: Int = 26, onClick: () -> Unit): ImageView {
        val px = (sizeDp * resources.displayMetrics.density).toInt()
        return ImageView(this).apply {
            setImageDrawable(MockupIconDrawable(type, Color.WHITE))
            layoutParams = LinearLayout.LayoutParams(px, px)
            setOnClickListener { onClick() }
        }
    }

    private fun buildMockupUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }

        // 1. TOP BAR
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(32, 24, 32, 20)
        }

        val exitBtn = makeIconButton(MockIconType.EXIT_BACK, 26) { finish() }

        val titleWrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(28, 0, 28, 0)
            }
            setOnClickListener { pickAudioLauncher.launch("audio/*") }
        }
        projectTitleTv = TextView(this).apply {
            text = "Tap to Select Audio"
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 8)
        }
        val underline = View(this).apply {
            setBackgroundColor(Color.parseColor("#242429"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2)
        }
        titleWrapper.addView(projectTitleTv)
        titleWrapper.addView(underline)

        val gearBtn = makeIconButton(MockIconType.SETTINGS_GEAR, 26) {
            showProjectSettingsDialog()
        }.apply {
            (layoutParams as LinearLayout.LayoutParams).setMargins(0, 0, 28, 0)
        }

        val exportBtn = makeIconButton(MockIconType.EXPORT_SHARE, 26) {
            showExportDialog()
        }

        topBar.addView(exitBtn)
        topBar.addView(titleWrapper)
        topBar.addView(gearBtn)
        topBar.addView(exportBtn)
        root.addView(topBar)

        // 2. PREVIEW CANVAS
        previewView = PreviewCanvasView(this).apply {
            visualizerManager = this@MainActivity.visualizerManager
            aspectRatioMode = exportConfig.aspectRatio
            onLayerSelectedByTouch = { touchedLayer ->
                selectedLayerIndex = layers.indexOf(touchedLayer).coerceAtLeast(0)
                scheduleRebuildLayerList()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.05f
            )
        }
        root.addView(previewView)

        // 3. MIDDLE 7-ICON TOOLBAR
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#111114"))
            setPadding(16, 20, 16, 20)
        }

        fun addToolSlot(icon: ImageView) {
            val slot = LinearLayout(this).apply {
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            slot.addView(icon)
            toolbar.addView(slot)
        }

        undoIcon = makeIconButton(MockIconType.UNDO, 24) { undo() }
        redoIcon = makeIconButton(MockIconType.REDO, 24) { redo() }
        val skipStartIcon = makeIconButton(MockIconType.SKIP_START, 24) {
            val mp = mediaPlayer
            if (mp == null) pickAudioLauncher.launch("audio/*") else mp.seekTo(0)
        }
        playLoopIcon = makeIconButton(MockIconType.PLAY_LOOP, 25) { togglePlayPause() }
        val skipEndIcon = makeIconButton(MockIconType.SKIP_END, 24) { pickAudioLauncher.launch("audio/*") }
        val duplicateIcon = makeIconButton(MockIconType.DUPLICATE, 24) {
            if (layers.isNotEmpty()) {
                pushUndoState()
                val idx = selectedLayerIndex.coerceIn(0, layers.lastIndex)
                layers.add(idx, layers[idx].deepCopy())
                refreshAll()
            }
        }
        val fitIcon = makeIconButton(MockIconType.FIT_SCREEN, 24) {
            if (layers.isNotEmpty()) {
                pushUndoState()
                val idx = selectedLayerIndex.coerceIn(0, layers.lastIndex)
                layers[idx].posX = 0f
                layers[idx].posY = 0f
                layers[idx].scale = 1.0f
                refreshAll()
            }
        }

        addToolSlot(undoIcon)
        addToolSlot(redoIcon)
        addToolSlot(skipStartIcon)
        addToolSlot(playLoopIcon)
        addToolSlot(skipEndIcon)
        addToolSlot(duplicateIcon)
        addToolSlot(fitIcon)
        root.addView(toolbar)

        // 4. LAYER STACK LIST
        layerScrollView = ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                0.95f
            )
        }
        layerListContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
        }
        layerScrollView.addView(layerListContainer)
        root.addView(layerScrollView)

        // 5. BOTTOM MINT-GREEN "+" PILL BUTTON
        val bottomArea = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            setPadding(44, 14, 44, 34)
        }

        val addPillBtn = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#9FF3D6"))
                cornerRadius = 100f
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (56 * resources.displayMetrics.density).toInt()
            )
            setOnClickListener { showTwoColumnAddGrid() }
        }

        val plusIcon = ImageView(this).apply {
            val px = (28 * resources.displayMetrics.density).toInt()
            setImageDrawable(MockupIconDrawable(MockIconType.PLUS, Color.parseColor("#111111")))
            layoutParams = LinearLayout.LayoutParams(px, px)
        }
        addPillBtn.addView(plusIcon)
        bottomArea.addView(addPillBtn)
        root.addView(bottomArea)

        setContentView(root)
        updateUndoRedoIcons()
    }

    private fun scheduleRebuildLayerList() {
        if (isRebuildPending) return
        isRebuildPending = true
        mainHandler.post {
            isRebuildPending = false
            if (!isFinishing && !isDestroyed) {
                rebuildLayerListNow()
            }
        }
    }

    private fun deleteLayerById(layerId: String) {
        val removedIdx = layers.indexOfFirst { it.id == layerId }
        if (removedIdx < 0) return
        pushUndoState()
        layers.removeAt(removedIdx)
        selectedLayerIndex = if (layers.isEmpty()) 0 else removedIdx.coerceAtMost(layers.lastIndex)
        refreshAll()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun rebuildLayerListNow() {
        layerListContainer.clearFocus()
        layerListContainer.removeAllViews()
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        val glideInterpolator = DecelerateInterpolator()

        if (layers.isEmpty()) {
            val emptyHint = TextView(this).apply {
                text = "No Layers yet — Tap + below to add"
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#6B7280"))
                setPadding(0, 64, 0, 64)
            }
            layerListContainer.addView(emptyHint)
            return
        }

        layers.forEachIndexed { index, layer ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 8, 0, 8)
                }
            }

            // Eye Visibility Toggle
            val eyeBoxSize = (36 * resources.displayMetrics.density).toInt()
            val eyeBadge = LinearLayout(this).apply {
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    setColor(if (layer.isVisible) Color.parseColor("#232730") else Color.parseColor("#15171C"))
                    setStroke(2, if (layer.isVisible) Color.parseColor("#9FF3D6") else Color.parseColor("#2E323C"))
                    cornerRadius = 14f
                }
                layoutParams = LinearLayout.LayoutParams(eyeBoxSize, eyeBoxSize).apply {
                    setMargins(0, 0, 16, 0)
                }
                setOnClickListener {
                    pushUndoState()
                    layer.isVisible = !layer.isVisible
                    refreshAll()
                }
            }

            val eyeIcon = ImageView(this).apply {
                val iconPx = (20 * resources.displayMetrics.density).toInt()
                setImageDrawable(
                    MockupIconDrawable(
                        if (layer.isVisible) MockIconType.EYE_OPEN else MockIconType.EYE_CLOSED,
                        if (layer.isVisible) Color.parseColor("#9FF3D6") else Color.parseColor("#6B7280")
                    )
                )
                layoutParams = LinearLayout.LayoutParams(iconPx, iconPx)
            }
            eyeBadge.addView(eyeIcon)

            // Dark Gray Pill with Theme Mint-Teal Edge
            val pillHeight = (38 * resources.displayMetrics.density).toInt()
            val isSelected = index == selectedLayerIndex
            val pillBar = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(24, 0, 14, 0)
                background = GradientDrawable().apply {
                    setColor(if (isSelected) Color.parseColor("#232730") else Color.parseColor("#1A1C22"))
                    setStroke(
                        if (isSelected) 3 else 2,
                        if (isSelected) Color.parseColor("#9FF3D6") else Color.parseColor("#354542")
                    )
                    cornerRadius = 100f
                }
                layoutParams = LinearLayout.LayoutParams(0, pillHeight, 1f)
            }

            val themeDot = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (isSelected) Color.parseColor("#9FF3D6") else Color.parseColor("#546B66"))
                }
                layoutParams = LinearLayout.LayoutParams(14, 14).apply {
                    setMargins(0, 0, 14, 0)
                }
            }
            pillBar.addView(themeDot)

            val fxBadge = if (layer.effects.isNotEmpty()) "  • +${layer.effects.size}FX" else ""
            val pillLabel = TextView(this).apply {
                text = "${layer.name}$fxBadge"
                textSize = 12f
                isSingleLine = true
                setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#D1D5DB"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            pillBar.addView(pillLabel)

            // Direct Delete Button on Layer Row
            val deleteRowBtn = LinearLayout(this).apply {
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#25191D"))
                    setStroke(1, Color.parseColor("#4A262D"))
                    cornerRadius = 14f
                }
                layoutParams = LinearLayout.LayoutParams(eyeBoxSize, eyeBoxSize).apply {
                    setMargins(14, 0, 0, 0)
                }
                setOnClickListener {
                    deleteLayerById(layer.id)
                }
            }
            val trashIv = ImageView(this).apply {
                val iconPx = (18 * resources.displayMetrics.density).toInt()
                setImageDrawable(MockupIconDrawable(MockIconType.TRASH_DELETE, Color.parseColor("#FF6B6B")))
                layoutParams = LinearLayout.LayoutParams(iconPx, iconPx)
            }
            deleteRowBtn.addView(trashIv)

            var startRawY = 0f
            var initialRowTop = 0f
            var isDragging = false
            var currentIndex = index

            pillBar.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startRawY = event.rawY
                        initialRowTop = row.top.toFloat()
                        isDragging = false
                        currentIndex = layers.indexOfFirst { it.id == layer.id }
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val totalDy = event.rawY - startRawY
                        if (!isDragging && abs(totalDy) > touchSlop) {
                            isDragging = true
                            layerScrollView.requestDisallowInterceptTouchEvent(true)
                            pushUndoState()
                            row.animate().scaleX(1.03f).scaleY(1.03f).setDuration(100).start()
                            row.elevation = 20f
                            row.alpha = 0.92f
                        }

                        if (isDragging && currentIndex >= 0) {
                            val desiredTop = initialRowTop + totalDy
                            row.translationY = desiredTop - row.top
                            val dragCenterY = desiredTop + row.height * 0.5f

                            if (currentIndex > 0) {
                                val aboveRow = layerListContainer.getChildAt(currentIndex - 1)
                                if (aboveRow != null && dragCenterY < aboveRow.top + aboveRow.height * 0.5f) {
                                    val shiftDist = (row.top - aboveRow.top).toFloat()
                                    val moved = layers.removeAt(currentIndex)
                                    currentIndex -= 1
                                    layers.add(currentIndex, moved)
                                    selectedLayerIndex = currentIndex

                                    layerListContainer.removeView(row)
                                    layerListContainer.addView(row, currentIndex)

                                    row.translationY += shiftDist
                                    aboveRow.translationY = -shiftDist
                                    aboveRow.animate()
                                        .translationY(0f)
                                        .setDuration(160)
                                        .setInterpolator(glideInterpolator)
                                        .start()

                                    previewView.selectedLayerId = moved.id
                                    previewView.submitLayers(layers)
                                }
                            }

                            if (currentIndex < layers.lastIndex) {
                                val belowRow = layerListContainer.getChildAt(currentIndex + 1)
                                if (belowRow != null && dragCenterY > belowRow.top + belowRow.height * 0.5f) {
                                    val shiftDist = (belowRow.top - row.top).toFloat()
                                    val moved = layers.removeAt(currentIndex)
                                    currentIndex += 1
                                    layers.add(currentIndex, moved)
                                    selectedLayerIndex = currentIndex

                                    layerListContainer.removeView(row)
                                    layerListContainer.addView(row, currentIndex)

                                    row.translationY -= shiftDist
                                    belowRow.translationY = shiftDist
                                    belowRow.animate()
                                        .translationY(0f)
                                        .setDuration(160)
                                        .setInterpolator(glideInterpolator)
                                        .start()

                                    previewView.selectedLayerId = moved.id
                                    previewView.submitLayers(layers)
                                }
                            }
                        }
                        true
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        layerScrollView.requestDisallowInterceptTouchEvent(false)
                        if (isDragging) {
                            row.animate()
                                .translationY(0f)
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .alpha(1.0f)
                                .setDuration(150)
                                .setInterpolator(glideInterpolator)
                                .withEndAction {
                                    row.elevation = 0f
                                    refreshAll()
                                }
                                .start()
                        } else {
                            selectedLayerIndex = layers.indexOfFirst { it.id == layer.id }.coerceAtLeast(0)
                            scheduleRebuildLayerList()
                            mainHandler.post {
                                openInspectorForLayer(layer)
                            }
                        }
                        true
                    }

                    else -> false
                }
            }

            row.addView(eyeBadge)
            row.addView(pillBar)
            row.addView(deleteRowBtn)
            layerListContainer.addView(row)
        }
    }

    private fun openInspectorForLayer(layer: LayerItem) {
        if (isFinishing || isDestroyed) return
        AdjustBottomSheet.show(
            context = this,
            layer = layer,
            onPickImageForLayer = {
                pendingTargetLayerForImage = layer
                pickImageLauncher.launch("image/*")
            },
            onDeleteLayer = {
                deleteLayerById(layer.id)
            },
            onChanged = {
                previewView.submitLayers(layers)
                scheduleRebuildLayerList()
            }
        )
    }

    private fun showTwoColumnAddGrid() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0D0D0D"))
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 28)
        }
        scroll.addView(container)

        data class GridEntry(val title: String, val isLayer: Boolean, val layerType: LayerType?, val effectType: EffectType?)

        val items = listOf(
            GridEntry("COMPOSITION", true, LayerType.COMPOSITION, null),
            GridEntry("TEXT", true, LayerType.TEXT, null),
            GridEntry("BARS", true, LayerType.BARS, null),
            GridEntry("PARTICLES", true, LayerType.PARTICLES, null),
            GridEntry("IMAGE", true, LayerType.IMAGE, null),
            GridEntry("AUDIOPROVIDER", true, LayerType.AUDIO_PROVIDER, null),
            GridEntry("WAVE WARP", false, null, EffectType.WAVE_WARP),
            GridEntry("GLOW", false, null, EffectType.GLOW),
            GridEntry("MOTION BLUR", false, null, EffectType.MOTION_BLUR),
            GridEntry("GAUSSIAN BLUR", false, null, EffectType.BLUR),
            GridEntry("RGB SPLIT", false, null, EffectType.RGB_SPLIT),
            GridEntry("MIRROR", false, null, EffectType.MIRROR)
        )

        var i = 0
        while (i < items.size) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 10, 0, 10) }
            }

            for (col in 0 until 2) {
                if (i < items.size) {
                    val entry = items[i]
                    val card = TextView(this).apply {
                        text = entry.title.uppercase(Locale.US)
                        textSize = 13.5f
                        gravity = Gravity.CENTER
                        setTextColor(Color.parseColor("#E5E7EB"))
                        background = GradientDrawable().apply {
                            setColor(Color.parseColor("#181A20"))
                            setStroke(2, Color.parseColor("#354542"))
                            cornerRadius = 14f
                        }
                        setPadding(14, 42, 14, 42)
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                            setMargins(10, 0, 10, 0)
                        }
                        setOnClickListener {
                            dialog.dismiss()
                            mainHandler.post {
                                handleGridSelection(entry.isLayer, entry.layerType, entry.effectType)
                            }
                        }
                    }
                    row.addView(card)
                    i++
                } else {
                    row.addView(View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
                    })
                }
            }
            container.addView(row)
        }

        dialog.setContentView(scroll)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setGravity(Gravity.BOTTOM)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        dialog.show()
    }

    private fun handleGridSelection(isLayer: Boolean, layerType: LayerType?, effectType: EffectType?) {
        try {
            if (isLayer && layerType != null) {
                if (layerType == LayerType.AUDIO_PROVIDER) {
                    pickAudioLauncher.launch("audio/*")
                    return
                }
                pushUndoState()
                val newLayer = LayerItem(
                    type = layerType,
                    name = layerType.displayName,
                    pillColorHex = layerType.defaultPillColor
                )
                layers.add(0, newLayer)
                selectedLayerIndex = 0
                refreshAll()
                if (layerType == LayerType.IMAGE) {
                    pendingTargetLayerForImage = newLayer
                    pickImageLauncher.launch("image/*")
                }
            } else if (effectType != null) {
                val target = layers.getOrNull(selectedLayerIndex) ?: layers.firstOrNull()
                if (target != null) {
                    pushUndoState()
                    target.effects.add(
                        EffectItem(
                            type = effectType,
                            angle = 0f,
                            spacing = 20f,
                            magnitude = 4.0f,
                            beatSync = 1.0f,
                            tintColorHex = target.secondaryColorHex
                        )
                    )
                    selectedLayerIndex = layers.indexOf(target).coerceAtLeast(0)
                    refreshAll()
                    Toast.makeText(
                        this,
                        "${effectType.displayName} added to ${target.name}",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(this, "Add a layer first", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun playAudioUri(uri: Uri) {
        try {
            mediaPlayer?.release()
            visualizerManager.loadAudioUri(this, uri)

            val mp = MediaPlayer().apply {
                setDataSource(this@MainActivity, uri)
                setOnPreparedListener { player ->
                    visualizerManager.attachMediaPlayer(player)
                    player.start()
                    playLoopIcon.setImageDrawable(MockupIconDrawable(MockIconType.PAUSE_LOOP, Color.WHITE))
                    projectTitleTv.text = uri.lastPathSegment ?: "Audio Track"
                }
                setOnCompletionListener {
                    playLoopIcon.setImageDrawable(MockupIconDrawable(MockIconType.PLAY_LOOP, Color.WHITE))
                }
                prepareAsync()
            }
            mediaPlayer = mp
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot play audio: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun togglePlayPause() {
        val mp = mediaPlayer
        if (mp == null) {
            pickAudioLauncher.launch("audio/*")
            return
        }
        if (mp.isPlaying) {
            mp.pause()
            playLoopIcon.setImageDrawable(MockupIconDrawable(MockIconType.PLAY_LOOP, Color.WHITE))
        } else {
            mp.start()
            playLoopIcon.setImageDrawable(MockupIconDrawable(MockIconType.PAUSE_LOOP, Color.WHITE))
        }
    }

    private fun showProjectSettingsDialog() {
        val options = arrayOf(
            "Select Audio File",
            "Aspect Ratio: 9:16 (Vertical)",
            "Aspect Ratio: 16:9 (Horizontal)"
        )
        AlertDialog.Builder(this)
            .setTitle("Project Settings")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> pickAudioLauncher.launch("audio/*")
                    1 -> {
                        exportConfig = exportConfig.copy(aspectRatio = AspectRatioMode.RATIO_9_16)
                        previewView.aspectRatioMode = exportConfig.aspectRatio
                    }
                    2 -> {
                        exportConfig = exportConfig.copy(aspectRatio = AspectRatioMode.RATIO_16_9)
                        previewView.aspectRatioMode = exportConfig.aspectRatio
                    }
                }
            }
            .show()
    }

    private fun showExportDialog() {
        val presets = listOf(
            ExportConfig(AspectRatioMode.RATIO_9_16, ExportResolution.RES_720P, ExportFps.FPS_30),
            ExportConfig(AspectRatioMode.RATIO_9_16, ExportResolution.RES_720P, ExportFps.FPS_60),
            ExportConfig(AspectRatioMode.RATIO_9_16, ExportResolution.RES_1080P, ExportFps.FPS_30),
            ExportConfig(AspectRatioMode.RATIO_9_16, ExportResolution.RES_1080P, ExportFps.FPS_60),
            ExportConfig(AspectRatioMode.RATIO_16_9, ExportResolution.RES_720P, ExportFps.FPS_30),
            ExportConfig(AspectRatioMode.RATIO_16_9, ExportResolution.RES_720P, ExportFps.FPS_60),
            ExportConfig(AspectRatioMode.RATIO_16_9, ExportResolution.RES_1080P, ExportFps.FPS_30),
            ExportConfig(AspectRatioMode.RATIO_16_9, ExportResolution.RES_1080P, ExportFps.FPS_60)
        )
        val labels = presets.map {
            "${it.aspectRatio.label} | ${it.resolution.label} | ${it.fps.fps}fps"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Export Video")
            .setItems(labels) { _, which ->
                exportConfig = presets[which]
                previewView.aspectRatioMode = exportConfig.aspectRatio
                Toast.makeText(
                    this,
                    "Export preset: ${labels[which]}",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .show()
    }

    private fun refreshAll() {
        previewView.selectedLayerId = layers.getOrNull(selectedLayerIndex)?.id
        previewView.submitLayers(layers)
        scheduleRebuildLayerList()
        updateUndoRedoIcons()
    }

    override fun onDestroy() {
        super.onDestroy()
        visualizerManager.release()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
