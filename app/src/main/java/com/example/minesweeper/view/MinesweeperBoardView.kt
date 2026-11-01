package com.example.minesweeper.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.OverScroller
import androidx.compose.ui.graphics.toArgb
import com.example.minesweeper.core.MinesweeperEngine
import com.example.minesweeper.model.AppColorTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

class MinesweeperBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var engine: MinesweeperEngine? = null
        set(value) {
            val isNewInstance = (field !== value)
            field = value
            reinitBuffer()
            if (isNewInstance) {
                resetViewport()
            }
            invalidate()
        }

    private var isGeneratingBoard = false

    fun notifyNewGame() {
        isGeneratingBoard = false
        reinitBuffer()
        resetViewport()
        invalidate()
    }

    var longPressDurationMs: Long = 250
    var onGameStateChangedListener: ((status: Int, minesRemaining: Int) -> Unit)? = null

    fun setThemeColors(
        accentColor: Int,
        highlightColor: Int,
        onAccentColor: Int = 0xFF000000.toInt(),
        tileBaseColor: Int = 0xFF2B2D25.toInt()
    ) {
        cellExplodedPaint.color = accentColor
        diggedBorderPaint.color = accentColor
        tileFlaggedPaint.color = accentColor
        mineCenterPaint.color = accentColor
        mineExplodedCenterPaint.color = accentColor
        textPaint.color = highlightColor
        tileBasePaint.color = tileBaseColor
        flagPennantPaint.color = 0xFF000000.toInt()
        flagPolePaint.color = 0xFF000000.toInt()
        flagBasePaint.color = 0xFF000000.toInt()
        mineExplodedBodyPaint.color = 0xFF000000.toInt()
        mineExplodedSpikePaint.color = 0xFF000000.toInt()
        crossPaint.color = 0xFF000000.toInt()
        invalidate()
    }

    // Layout & Dynamic Gesture Zooming/Panning
    private var baseCellSize = 64f
    private var scaleFactor = 1.0f
    private var defaultScale = 1.0f
    private var panX = 0f
    private var panY = 0f
    private var isDragging = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var touchSlop = 0f

    // Reusable byte buffer for zero allocation state synchronization
    private var boardBuffer = ByteArray(0)

    // Touch gesture tracking
    private var downX = 0f
    private var downY = 0f
    private var longPressTriggered = false
    private val handler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null

    // AMOLED Black and Olive Gold (#85804C) Palette
    private val bgPaint = Paint().apply { color = 0xFF000000.toInt() }

    // Unrevealed Keycap Tile: Lighter charcoal-olive tile for clear contrast against black digged cells
    private val tileBasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2B2D25.toInt() }

    // Revealed Cells: Pure AMOLED Black, rounded at exterior corners of digged blocks
    private val cellRevealedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF000000.toInt() }
    private val cellExplodedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF85804C.toInt() }

    // Thin line in Olive Gold (#85804C) drawn only on middle third of sides of digged (revealed) cells
    private val diggedBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF85804C.toInt()
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 2f
    }

    // Typography: Roboto Bold, UNIFIED single color for all numbers (1-8) (Olive Gold Light)
    private val singleNumberColor = 0xFFA6A168.toInt()
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = singleNumberColor
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }

    // Flagged Cell Tile: #85804C accent when marked as mine for high contrast clarity
    private val tileFlaggedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF85804C.toInt() }

    // Solid Black Flag (#000000) drawn inside the #85804C cell
    private val flagPennantPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF000000.toInt()
        style = Paint.Style.FILL
    }
    private val flagPolePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF000000.toInt()
        strokeWidth = 2.5f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val flagBasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF000000.toInt()
        strokeWidth = 2.5f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    // Minimal Modern Mine
    private val mineBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF161713.toInt()
        style = Paint.Style.FILL
    }
    private val mineSpikePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF3D3B30.toInt()
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    private val mineCenterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF85804C.toInt()
        style = Paint.Style.FILL
    }
    private val mineExplodedBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF000000.toInt()
        style = Paint.Style.FILL
    }
    private val mineExplodedSpikePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF000000.toInt()
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    private val mineExplodedCenterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF85804C.toInt()
        style = Paint.Style.FILL
    }

    private val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF000000.toInt()
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    private val textBounds = Rect()
    private val flagPath = Path()
    private val cellPath = Path()
    private val cornerRadii = FloatArray(8)
    private val cellRect = RectF()

    init {
        val currentTheme = AppColorTheme.getSelectedTheme(context)
        setThemeColors(
            accentColor = currentTheme.primary.toArgb(),
            highlightColor = currentTheme.primaryLight.toArgb(),
            onAccentColor = currentTheme.onPrimary.toArgb(),
            tileBaseColor = currentTheme.surfaceTile.toArgb()
        )
    }

    // Dynamic Multi-touch Pinch Zoom & Fluid 2-Finger Pan
    private var prevFocusX = 0f
    private var prevFocusY = 0f
    private var activePointerCount = 0

    // Smooth, controlled momentum scrolling (fling) - calibrated to 6/10
    private val scroller = OverScroller(context).apply {
        setFriction(0.016f)
    }
    private var velocityTracker: VelocityTracker? = null
    private val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity.toFloat()
    private val maxFlingVelocity = ViewConfiguration.get(context).scaledMaximumFlingVelocity.toFloat()

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            if (activePointerCount < 2) return false
            scroller.forceFinished(true)
            prevFocusX = detector.focusX
            prevFocusY = detector.focusY
            isDragging = true
            longPressRunnable?.let { handler.removeCallbacks(it) }
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (activePointerCount < 2) return false
            longPressRunnable?.let { handler.removeCallbacks(it) }
            isDragging = true

            val focusX = detector.focusX
            val focusY = detector.focusY

            // 1. Fluid translation: pan with responsive displacement of 2 fingers
            val dx = (focusX - prevFocusX) * 1.15f
            val dy = (focusY - prevFocusY) * 1.15f
            panX += dx
            panY += dy

            // 2. Controlled responsive scaling around focal point
            val oldScale = scaleFactor
            val scaleDelta = detector.scaleFactor - 1.0f
            val amplifiedFactor = 1.0f + scaleDelta * 1.25f
            scaleFactor = (scaleFactor * amplifiedFactor).coerceIn(0.25f, 5.0f)
            panX = focusX - (focusX - panX) * (scaleFactor / oldScale)
            panY = focusY - (focusY - panY) * (scaleFactor / oldScale)

            prevFocusX = focusX
            prevFocusY = focusY

            clampPan()
            invalidate()
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            prevFocusX = detector.focusX
            prevFocusY = detector.focusY
        }
    })

    init {
        touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
        // Disable single-finger double-tap-and-hold zoom completely
        scaleDetector.isQuickScaleEnabled = false
        diggedBorderPaint.strokeWidth = max(1.5f, context.resources.displayMetrics.density * 0.75f)
    }

    private fun reinitBuffer() {
        val eng = engine ?: return
        val size = eng.getRows() * eng.getCols()
        if (boardBuffer.size != size) {
            boardBuffer = ByteArray(size)
        }
    }

    fun resetViewport() {
        val eng = engine ?: return
        val rows = eng.getRows()
        val cols = eng.getCols()
        if (rows <= 0 || cols <= 0 || width <= 0 || height <= 0) return

        val fitScaleX = width.toFloat() / (cols * baseCellSize)
        val fitScaleY = height.toFloat() / (rows * baseCellSize)
        val fitScale = min(fitScaleX, fitScaleY)

        defaultScale = fitScale
        // Fit vertical board cleanly within screen bounds
        scaleFactor = fitScale.coerceIn(0.25f, 4.0f)

        val boardW = cols * baseCellSize * scaleFactor
        val boardH = rows * baseCellSize * scaleFactor

        panX = (width - boardW) / 2f
        panY = (height - boardH) / 2f
        scroller.forceFinished(true)
        clampPan()
    }

    fun zoomTo(focusX: Float, focusY: Float, targetScale: Float) {
        val oldScale = scaleFactor
        scaleFactor = targetScale.coerceIn(0.25f, 5.0f)
        panX = focusX - (focusX - panX) * (scaleFactor / oldScale)
        panY = focusY - (focusY - panY) * (scaleFactor / oldScale)
        scroller.forceFinished(true)
        clampPan()
        invalidate()
    }

    fun zoomAround(focusX: Float, focusY: Float, factor: Float) {
        val oldScale = scaleFactor
        scaleFactor = (scaleFactor * factor).coerceIn(0.25f, 5.0f)
        panX = focusX - (focusX - panX) * (scaleFactor / oldScale)
        panY = focusY - (focusY - panY) * (scaleFactor / oldScale)
        scroller.forceFinished(true)
        clampPan()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        resetViewport()
    }

    private fun getPanBounds(): FloatArray {
        val eng = engine ?: return floatArrayOf(panX, panX, panY, panY)
        val boardW = eng.getCols() * baseCellSize * scaleFactor
        val boardH = eng.getRows() * baseCellSize * scaleFactor

        // Allow free panning across the board without freezing
        val marginX = max(width * 0.25f, 80f)
        val marginY = max(height * 0.25f, 80f)

        val minX = if (boardW <= width) (width - boardW) / 2f - width * 0.35f else width - boardW - marginX
        val maxX = if (boardW <= width) (width - boardW) / 2f + width * 0.35f else marginX

        val minY = if (boardH <= height) (height - boardH) / 2f - height * 0.35f else height - boardH - marginY
        val maxY = if (boardH <= height) (height - boardH) / 2f + height * 0.35f else marginY

        return floatArrayOf(min(minX, maxX), max(minX, maxX), min(minY, maxY), max(minY, maxY))
    }

    private fun clampPan() {
        val bounds = getPanBounds()
        panX = panX.coerceIn(bounds[0], bounds[1])
        panY = panY.coerceIn(bounds[2], bounds[3])
    }

    override fun computeScroll() {
        super.computeScroll()
        if (scroller.computeScrollOffset()) {
            panX = scroller.currX.toFloat()
            panY = scroller.currY.toFloat()
            clampPan()
            postInvalidateOnAnimation()
        }
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        // Mouse wheel scroll zooming on emulator and desktop
        if (event.action == MotionEvent.ACTION_SCROLL && event.isFromSource(InputDevice.SOURCE_CLASS_POINTER)) {
            val vScroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            if (vScroll != 0f) {
                val factor = if (vScroll > 0) 1.2f else 0.83f
                zoomAround(event.x, event.y, factor)
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)

        val count = event.pointerCount
        activePointerCount = count
        if (count >= 2 || scaleDetector.isInProgress) {
            scaleDetector.onTouchEvent(event)
        }

        val currentTimeMs = System.currentTimeMillis()

        // Compute centroid of all active pointers for seamless 1 or 2 finger panning
        var sumX = 0f
        var sumY = 0f
        for (i in 0 until count) {
            sumX += event.getX(i)
            sumY += event.getY(i)
        }
        val focusX = sumX / count
        val focusY = sumY / count

        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain()
        }
        velocityTracker?.addMovement(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val wasFlinging = !scroller.isFinished
                scroller.forceFinished(true)
                velocityTracker?.clear()
                velocityTracker?.addMovement(event)

                downX = event.x
                downY = event.y
                lastTouchX = event.x
                lastTouchY = event.y
                prevFocusX = event.x
                prevFocusY = event.y
                isDragging = wasFlinging
                longPressTriggered = false

                longPressRunnable?.let { handler.removeCallbacks(it) }
                if (!wasFlinging) {
                    val runnable = Runnable {
                        longPressTriggered = true
                        handleLongPressAt(downX, downY, currentTimeMs)
                    }
                    longPressRunnable = runnable
                    handler.postDelayed(runnable, longPressDurationMs)
                }
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // Multi-finger detected: cancel single-touch long-press immediately
                scroller.forceFinished(true)
                longPressRunnable?.let { handler.removeCallbacks(it) }
                isDragging = true
                lastTouchX = focusX
                lastTouchY = focusY
                prevFocusX = focusX
                prevFocusY = focusY
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = focusX - lastTouchX
                val dy = focusY - lastTouchY

                if (count == 1) {
                    val dist = Math.hypot((event.x - downX).toDouble(), (event.y - downY).toDouble()).toFloat()
                    if (dist > touchSlop && !longPressTriggered) {
                        longPressRunnable?.let { handler.removeCallbacks(it) }
                        isDragging = true
                    }
                } else {
                    longPressRunnable?.let { handler.removeCallbacks(it) }
                    isDragging = true
                }

                // Fast and responsive 1.15x panning: handles 1-finger and 2-finger panning seamlessly
                if (isDragging && !scaleDetector.isInProgress) {
                    panX += dx * 1.15f
                    panY += dy * 1.15f
                    clampPan()
                    invalidate()
                }

                lastTouchX = focusX
                lastTouchY = focusY
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                longPressRunnable?.let { handler.removeCallbacks(it) }
                isDragging = true

                // Calculate centroid of remaining pointers to prevent any pan jumps
                val actionIndex = event.actionIndex
                val remainingCount = count - 1
                if (remainingCount > 0) {
                    var remX = 0f
                    var remY = 0f
                    for (i in 0 until count) {
                        if (i != actionIndex) {
                            remX += event.getX(i)
                            remY += event.getY(i)
                        }
                    }
                    lastTouchX = remX / remainingCount
                    lastTouchY = remY / remainingCount
                    prevFocusX = lastTouchX
                    prevFocusY = lastTouchY
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                longPressRunnable?.let { handler.removeCallbacks(it) }

                if (!isDragging && !longPressTriggered && count == 1) {
                    handleTapAt(event.x, event.y, currentTimeMs)
                }

                velocityTracker?.recycle()
                velocityTracker = null
                isDragging = false
                longPressTriggered = false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                longPressRunnable?.let { handler.removeCallbacks(it) }
                isDragging = false
                longPressTriggered = false
                velocityTracker?.recycle()
                velocityTracker = null
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun screenToBoard(x: Float, y: Float): Pair<Int, Int>? {
        val eng = engine ?: return null
        val curCellSize = baseCellSize * scaleFactor
        val c = ((x - panX) / curCellSize).toInt()
        val r = ((y - panY) / curCellSize).toInt()

        if (r in 0 until eng.getRows() && c in 0 until eng.getCols()) {
            return Pair(r, c)
        }
        return null
    }

    private fun handleTapAt(x: Float, y: Float, currentTimeMs: Long) {
        val eng = engine ?: return
        if (isGeneratingBoard) return
        val coords = screenToBoard(x, y) ?: return
        val (r, c) = coords

        if (eng.isFirstMove()) {
            isGeneratingBoard = true
            CoroutineScope(Dispatchers.Default).launch {
                val changed = eng.handleTap(r, c, currentTimeMs)
                withContext(Dispatchers.Main) {
                    isGeneratingBoard = false
                    if (changed) {
                        onGameStateChangedListener?.invoke(eng.getGameStatus(), eng.getMinesRemaining())
                        invalidate()
                    }
                }
            }
        } else {
            val changed = eng.handleTap(r, c, currentTimeMs)
            if (changed) {
                onGameStateChangedListener?.invoke(eng.getGameStatus(), eng.getMinesRemaining())
                invalidate()
            }
        }
    }

    private fun handleLongPressAt(x: Float, y: Float, currentTimeMs: Long) {
        val eng = engine ?: return
        val coords = screenToBoard(x, y) ?: return
        val (r, c) = coords

        val changed = eng.handleLongPress(r, c, currentTimeMs)
        if (changed) {
            onGameStateChangedListener?.invoke(eng.getGameStatus(), eng.getMinesRemaining())
            invalidate()
        }
    }

    private fun isCellDigged(r: Int, c: Int, rows: Int, cols: Int): Boolean {
        if (r !in 0 until rows || c !in 0 until cols) return false
        val idx = r * cols + c
        if (idx !in boardBuffer.indices) return false
        val b = boardBuffer[idx].toInt() and 0xFF
        val isRevealed = (b and MinesweeperEngine.FLAG_REVEALED) != 0
        val isExploded = (b and MinesweeperEngine.FLAG_EXPLODED) != 0
        return isRevealed || isExploded
    }

    private fun isCellFlagged(r: Int, c: Int, rows: Int, cols: Int): Boolean {
        if (r !in 0 until rows || c !in 0 until cols) return false
        val idx = r * cols + c
        if (idx !in boardBuffer.indices) return false
        val b = boardBuffer[idx].toInt() and 0xFF
        val isRevealed = (b and MinesweeperEngine.FLAG_REVEALED) != 0
        val isFlagged = (b and MinesweeperEngine.FLAG_FLAGGED) != 0
        return !isRevealed && isFlagged
    }

    private fun isCellPartOfBlock(r: Int, c: Int, rows: Int, cols: Int): Boolean {
        return isCellDigged(r, c, rows, cols) || isCellFlagged(r, c, rows, cols)
    }

    private fun isSideTouchingBlockOrBorder(r: Int, c: Int, rows: Int, cols: Int): Boolean {
        if (r !in 0 until rows || c !in 0 until cols) return true
        return isCellPartOfBlock(r, c, rows, cols)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.clipRect(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val eng = engine ?: return
        val rows = eng.getRows()
        val cols = eng.getCols()
        if (rows <= 0 || cols <= 0) return

        reinitBuffer()
        eng.syncBoard(boardBuffer)

        val cellSize = baseCellSize * scaleFactor
        textPaint.textSize = cellSize * 0.65f

        val startCol = max(0, ((-panX) / cellSize).toInt())
        val endCol = min(cols - 1, ((width - panX) / cellSize).toInt() + 1)
        val startRow = max(0, ((-panY) / cellSize).toInt())
        val endRow = min(rows - 1, ((height - panY) / cellSize).toInt() + 1)

        canvas.save()
        canvas.translate(panX, panY)

        // Pass 0: Fill the entire board area with tileBasePaint (#171813).
        // This forms a continuous, seamless backdrop with ZERO gaps between unrevealed cells.
        val boardW = cols * cellSize
        val boardH = rows * cellSize
        canvas.drawRect(0f, 0f, boardW, boardH, tileBasePaint)

        // Pass 1: Draw revealed/flagged cells, numbers, flags, mines
        for (r in startRow..endRow) {
            val top = r * cellSize
            val bottom = (r + 1) * cellSize
            for (c in startCol..endCol) {
                val left = c * cellSize
                val right = (c + 1) * cellSize
                val idx = r * cols + c
                val cellByte = boardBuffer[idx].toInt() and 0xFF

                drawCell(canvas, r, c, rows, cols, left, top, right, bottom, cellSize, cellByte)
            }
        }

        // Pass 2: Draw thin line in #85804C ONLY between adjacent digged cells when zoomed in closer
        val baseScale = if (defaultScale > 0.001f) defaultScale else scaleFactor
        val zoomRatio = scaleFactor / baseScale
        // Invisible at default zoom (1.0x), zoomed out (<1.0x), or a bit closer (up to 1.30x)
        val fadeStart = 1.30f
        val fadeEnd = 1.65f

        if (zoomRatio > fadeStart) {
            val alphaFactor = ((zoomRatio - fadeStart) / (fadeEnd - fadeStart)).coerceIn(0f, 1f)
            val alpha = (alphaFactor * 255).toInt()
            if (alpha > 0) {
                diggedBorderPaint.alpha = alpha

                val s3 = cellSize / 3f
                val s23 = cellSize * 2f / 3f
                for (r in startRow..endRow) {
                    val top = r * cellSize
                    val edgeY = (r + 1) * cellSize
                    for (c in startCol..endCol) {
                        val idx = r * cols + c
                        val cellByte = boardBuffer[idx].toInt() and 0xFF
                        val isRevealed = (cellByte and MinesweeperEngine.FLAG_REVEALED) != 0
                        val isExploded = (cellByte and MinesweeperEngine.FLAG_EXPLODED) != 0

                        if (isRevealed && !isExploded) {
                            val left = c * cellSize
                            val edgeX = (c + 1) * cellSize
                            // Draw middle-third tick line between this digged cell and adjacent digged cells
                            if (isCellDigged(r, c + 1, rows, cols)) {
                                canvas.drawLine(edgeX, top + s3, edgeX, top + s23, diggedBorderPaint)
                            }
                            if (isCellDigged(r + 1, c, rows, cols)) {
                                canvas.drawLine(left + s3, edgeY, left + s23, edgeY, diggedBorderPaint)
                            }
                        }
                    }
                }
            }
        }

        canvas.restore()
    }

    private fun drawCell(
        canvas: Canvas,
        r: Int,
        c: Int,
        rows: Int,
        cols: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        cellSize: Float,
        cellByte: Int
    ) {
        val isRevealed = (cellByte and MinesweeperEngine.FLAG_REVEALED) != 0
        val isFlagged = (cellByte and MinesweeperEngine.FLAG_FLAGGED) != 0
        val isExploded = (cellByte and MinesweeperEngine.FLAG_EXPLODED) != 0
        val isWrongFlag = (cellByte and MinesweeperEngine.FLAG_WRONG_FLAG) != 0
        val value = cellByte and MinesweeperEngine.MASK_VALUE

        // Unrevealed cells are already seamlessly drawn by Pass 0 (board background)
        if (!isRevealed && !isFlagged && !isExploded) {
            return
        }

        val cx = (left + right) * 0.5f
        val cy = (top + bottom) * 0.5f

        // Only round corners that are outer corners of the connected block (not touching board borders)
        val up = isSideTouchingBlockOrBorder(r - 1, c, rows, cols)
        val down = isSideTouchingBlockOrBorder(r + 1, c, rows, cols)
        val leftSide = isSideTouchingBlockOrBorder(r, c - 1, rows, cols)
        val rightSide = isSideTouchingBlockOrBorder(r, c + 1, rows, cols)

        val roundTL = !up && !leftSide
        val roundTR = !up && !rightSide
        val roundBL = !down && !leftSide
        val roundBR = !down && !rightSide

        val cornerRadius = max(4f, cellSize * 0.22f)
        val tl = if (roundTL) cornerRadius else 0f
        val tr = if (roundTR) cornerRadius else 0f
        val br = if (roundBR) cornerRadius else 0f
        val bl = if (roundBL) cornerRadius else 0f

        cornerRadii[0] = tl; cornerRadii[1] = tl
        cornerRadii[2] = tr; cornerRadii[3] = tr
        cornerRadii[4] = br; cornerRadii[5] = br
        cornerRadii[6] = bl; cornerRadii[7] = bl

        // Seamless edge overlap on internal block seams (1px in screen space) to eliminate any subpixel gaps
        val overlap = 1.0f
        val touchRight = (c + 1 < cols) && isCellPartOfBlock(r, c + 1, rows, cols)
        val touchDown = (r + 1 < rows) && isCellPartOfBlock(r + 1, c, rows, cols)

        val drawLeft = left
        val drawTop = top
        val drawRight = if (touchRight) right + overlap else right
        val drawBottom = if (touchDown) bottom + overlap else bottom

        cellPath.reset()
        cellPath.addRoundRect(drawLeft, drawTop, drawRight, drawBottom, cornerRadii, Path.Direction.CW)

        if (isExploded || isRevealed) {
            if (isExploded) {
                // Detonated mine cell (#85804C)
                canvas.drawPath(cellPath, cellExplodedPaint)
                drawMine(canvas, cx, cy, cellSize, isExploded = true)
                return
            }

            // Pure AMOLED Black digged cell (only pure black, no gray color)
            canvas.drawPath(cellPath, cellRevealedPaint)

            if (value == MinesweeperEngine.VALUE_MINE) {
                drawMine(canvas, cx, cy, cellSize, isExploded = false)
            } else if (value in 1..8) {
                // Unified single color for all numbers (Olive Gold Light)
                val text = value.toString()
                textPaint.getTextBounds(text, 0, text.length, textBounds)
                val textY = cy + (textBounds.height() / 2f)
                canvas.drawText(text, cx, textY, textPaint)
            }
        } else if (isFlagged) {
            canvas.drawPath(cellPath, tileFlaggedPaint)
            drawFlag(canvas, cx, cy, cellSize)
            if (isWrongFlag) {
                val crossStroke = max(5.5f, cellSize * 0.12f)
                crossPaint.strokeWidth = crossStroke
                crossPaint.strokeCap = Paint.Cap.ROUND
                val crossExt = cellSize * 0.30f
                canvas.drawLine(cx - crossExt, cy - crossExt, cx + crossExt, cy + crossExt, crossPaint)
                canvas.drawLine(cx + crossExt, cy - crossExt, cx - crossExt, cy + crossExt, crossPaint)
            }
        }
    }

    private fun drawFlag(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        val poleX = cx - size * 0.08f
        val topY = cy - size * 0.28f
        val botY = cy + size * 0.24f
        val baseY = cy + size * 0.26f

        val stroke = max(2f, size * 0.06f)
        flagPolePaint.strokeWidth = stroke
        flagBasePaint.strokeWidth = stroke

        // Flagpole and base in solid Black (#000000)
        canvas.drawLine(poleX, topY, poleX, baseY, flagPolePaint)
        canvas.drawLine(poleX - size * 0.14f, baseY, poleX + size * 0.14f, baseY, flagBasePaint)

        // Clean sharp triangular flag pennant in solid Black (#000000)
        flagPath.reset()
        flagPath.moveTo(poleX, topY)
        flagPath.lineTo(poleX + size * 0.32f, topY + size * 0.15f)
        flagPath.lineTo(poleX, topY + size * 0.30f)
        flagPath.close()
        canvas.drawPath(flagPath, flagPennantPaint)
    }

    private fun drawMine(canvas: Canvas, cx: Float, cy: Float, size: Float, isExploded: Boolean) {
        val bodyPaint = if (isExploded) mineExplodedBodyPaint else mineBodyPaint
        val spikePaint = if (isExploded) mineExplodedSpikePaint else mineSpikePaint
        val centerPaint = if (isExploded) mineExplodedCenterPaint else mineCenterPaint

        val r = size * 0.20f
        val spikeLen = size * 0.29f
        val diagLen = spikeLen * 0.707f

        // 4 Clean crossed spike lines (8 spikes)
        canvas.drawLine(cx - spikeLen, cy, cx + spikeLen, cy, spikePaint)
        canvas.drawLine(cx, cy - spikeLen, cx, cy + spikeLen, spikePaint)
        canvas.drawLine(cx - diagLen, cy - diagLen, cx + diagLen, cy + diagLen, spikePaint)
        canvas.drawLine(cx - diagLen, cy + diagLen, cx + diagLen, cy - diagLen, spikePaint)

        // Core sphere
        canvas.drawCircle(cx, cy, r, bodyPaint)

        // Center dot
        canvas.drawCircle(cx, cy, r * 0.35f, centerPaint)
    }
}
