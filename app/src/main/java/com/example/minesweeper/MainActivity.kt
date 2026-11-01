package com.example.minesweeper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.minesweeper.core.MinesweeperEngine
import com.example.minesweeper.model.AppColorTheme
import com.example.minesweeper.model.GameDifficulty
import com.example.minesweeper.model.GameStateManager
import com.example.minesweeper.model.LeaderboardManager
import com.example.minesweeper.ui.theme.*
import com.example.minesweeper.view.MinesweeperBoardView
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

enum class AppScreen {
    MENU,
    SETTINGS,
    GAME
}

class MainActivity : ComponentActivity() {
    private val engine = MinesweeperEngine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            var currentTheme by remember { mutableStateOf(AppColorTheme.getSelectedTheme(context)) }

            MinesweeperTheme(colorTheme = currentTheme, darkTheme = true) {
                var currentScreen by remember { mutableStateOf(AppScreen.MENU) }
                var previousScreen by remember { mutableStateOf(AppScreen.MENU) }
                var hasActiveGame by remember { mutableStateOf(GameStateManager.hasSavedGame(context)) }
                var sessionKey by remember { mutableIntStateOf(0) }

                LaunchedEffect(Unit) {
                    if (GameStateManager.hasSavedGame(context)) {
                        val loaded = GameStateManager.loadGame(context, engine)
                        hasActiveGame = loaded
                        if (!loaded) {
                            engine.initGame(GameDifficulty.BOARD_ROWS, GameDifficulty.BOARD_COLS, GameDifficulty.BOARD_MINES)
                        }
                    } else {
                        engine.initGame(GameDifficulty.BOARD_ROWS, GameDifficulty.BOARD_COLS, GameDifficulty.BOARD_MINES)
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (currentScreen) {
                        AppScreen.MENU -> MainMenuScreen(
                            hasActiveGame = hasActiveGame,
                            onResume = { currentScreen = AppScreen.GAME },
                            onStartNew = {
                                GameStateManager.clearSavedGame(context)
                                engine.initGame(GameDifficulty.BOARD_ROWS, GameDifficulty.BOARD_COLS, GameDifficulty.BOARD_MINES)
                                sessionKey++
                                hasActiveGame = true
                                currentScreen = AppScreen.GAME
                            },
                            onSettings = {
                                previousScreen = AppScreen.MENU
                                currentScreen = AppScreen.SETTINGS
                            }
                        )
                        AppScreen.SETTINGS -> SettingsScreen(
                            currentTheme = currentTheme,
                            onThemeSelected = { newTheme ->
                                currentTheme = newTheme
                                AppColorTheme.setSelectedTheme(context, newTheme)
                            },
                            onBack = { currentScreen = previousScreen }
                        )
                        AppScreen.GAME -> GameScreen(
                            engine = engine,
                            sessionKey = sessionKey,
                            currentTheme = currentTheme,
                            onBackToMenu = {
                                engine.pauseGame(System.currentTimeMillis())
                                val status = engine.getGameStatus()
                                hasActiveGame = (status == MinesweeperEngine.STATUS_NOT_STARTED || status == MinesweeperEngine.STATUS_PLAYING)
                                if (hasActiveGame) {
                                    GameStateManager.saveGame(context, engine)
                                } else {
                                    GameStateManager.clearSavedGame(context)
                                }
                                currentScreen = AppScreen.MENU
                            },
                            onGameOver = {
                                hasActiveGame = false
                                GameStateManager.clearSavedGame(context)
                            },
                            onSettings = {
                                engine.pauseGame(System.currentTimeMillis())
                                val status = engine.getGameStatus()
                                if (status == MinesweeperEngine.STATUS_NOT_STARTED || status == MinesweeperEngine.STATUS_PLAYING) {
                                    GameStateManager.saveGame(context, engine)
                                }
                                previousScreen = AppScreen.GAME
                                currentScreen = AppScreen.SETTINGS
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        engine.pauseGame(System.currentTimeMillis())
        GameStateManager.saveGame(this, engine)
    }

    override fun onStop() {
        super.onStop()
        engine.pauseGame(System.currentTimeMillis())
        GameStateManager.saveGame(this, engine)
    }
}

@Composable
fun MineGearLogo(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val rOuter = w * 0.46f
        val rInner = w * 0.33f
        val teeth = 8
        val angleStep = (2 * Math.PI / teeth).toFloat()
        val toothAngle = angleStep * 0.50f

        val path = androidx.compose.ui.graphics.Path().apply {
            fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
            for (i in 0 until teeth) {
                val aMid = -Math.PI.toFloat() / 2f + i * angleStep
                val a0 = aMid - toothAngle / 2f
                val a1 = aMid + toothAngle / 2f
                val aNext0 = aMid + angleStep - toothAngle / 2f

                val p0X = cx + rOuter * kotlin.math.cos(a0)
                val p0Y = cy + rOuter * kotlin.math.sin(a0)
                val p1X = cx + rOuter * kotlin.math.cos(a1)
                val p1Y = cy + rOuter * kotlin.math.sin(a1)
                val p2X = cx + rInner * kotlin.math.cos(a1)
                val p2Y = cy + rInner * kotlin.math.sin(a1)
                val p3X = cx + rInner * kotlin.math.cos(aNext0)
                val p3Y = cy + rInner * kotlin.math.sin(aNext0)

                if (i == 0) moveTo(p0X, p0Y) else lineTo(p0X, p0Y)
                lineTo(p1X, p1Y)
                lineTo(p2X, p2Y)
                lineTo(p3X, p3Y)
            }
            close()

            // Center square aperture
            val sq = w * 0.09f
            moveTo(cx - sq, cy - sq)
            lineTo(cx + sq, cy - sq)
            lineTo(cx + sq, cy + sq)
            lineTo(cx - sq, cy + sq)
            close()
        }

        drawPath(
            path = path,
            color = color,
            style = androidx.compose.ui.graphics.drawscope.Fill
        )
    }
}

@Composable
fun MainMenuScreen(
    hasActiveGame: Boolean,
    onResume: () -> Unit,
    onStartNew: () -> Unit,
    onSettings: () -> Unit
) {
    val context = LocalContext.current
    val topScores = remember { LeaderboardManager.getTopScores(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Material 3 Top Actions Row (flat, no highlight/border)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(
                onClick = onSettings,
                shape = CircleShape,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.size(44.dp)
            ) {
                Text(
                    text = "⚙",
                    fontSize = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.weight(0.35f))

        // Large Mine Gear Logo
        MineGearLogo(
            modifier = Modifier.size(104.dp),
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Material 3 Pill Chip for Board Dimensions (flat, no outline highlight)
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = "${GameDifficulty.BOARD_COLS} × ${GameDifficulty.BOARD_ROWS}  •  ${GameDifficulty.BOARD_MINES} MINES",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Material 3 Card for Fastest Games Leaderboard (flat surface, no outline highlight)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.3f, fill = false),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = MaterialTheme.shapes.large,
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "FASTEST GAMES",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)

                if (topScores.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        topScores.take(5).forEachIndexed { index, score ->
                            val m = score.timeSeconds / 60
                            val s = score.timeSeconds % 60
                            val timeFormatted = if (m > 0) String.format("%d:%02d", m, s) else "${score.timeSeconds}s"

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 7.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (index == 0) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "${index + 1}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (index == 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = timeFormatted,
                                        fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = score.dateFormatted,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No recorded games yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(0.45f))

        // Material 3 Flat Action Buttons (zero elevation, no outline highlight)
        if (hasActiveGame) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onResume,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        focusedElevation = 0.dp,
                        hoveredElevation = 0.dp
                    )
                ) {
                    Text(
                        text = "RESUME",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                FilledTonalButton(
                    onClick = onStartNew,
                    modifier = Modifier
                        .weight(1.35f)
                        .height(50.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    elevation = ButtonDefaults.filledTonalButtonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        focusedElevation = 0.dp,
                        hoveredElevation = 0.dp
                    )
                ) {
                    Text(
                        text = "START NEW",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        } else {
            Button(
                onClick = onStartNew,
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(50.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    focusedElevation = 0.dp,
                    hoveredElevation = 0.dp
                )
            ) {
                Text(
                    text = "START GAME",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(
    currentTheme: AppColorTheme,
    onThemeSelected: (AppColorTheme) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    var longPressMs by remember { mutableStateOf(GameDifficulty.getLongPressDuration(context).toFloat()) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        // Material 3 Top Navigation Bar (flat, no highlight)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            FilledTonalIconButton(
                onClick = onBack,
                shape = CircleShape,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.size(40.dp)
            ) {
                Text(
                    text = "←",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Color Theme Selector M3 Card (flat, no outline highlight)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = MaterialTheme.shapes.large,
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "COLOR THEME",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Accent palette for interface and markers",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppColorTheme.entries.forEach { theme ->
                        val isSelected = theme == currentTheme
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(CircleShape)
                                .background(if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                                .clickable { onThemeSelected(theme) }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(theme.primary)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Text(
                                    text = theme.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) theme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (isSelected) {
                                Text(
                                    text = "✓",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = theme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Long Press Duration M3 Card (flat, no outline highlight)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = MaterialTheme.shapes.large,
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LONG PRESS DELAY",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "${longPressMs.toInt()} ms",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = longPressMs,
                    onValueChange = {
                        longPressMs = it
                        GameDifficulty.setLongPressDuration(context, it.toInt())
                    },
                    valueRange = 100f..600f,
                    steps = 9,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.outline
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Hold duration required to uncover or flag cells",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Gesture Tips M3 Card (flat, no outline highlight)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = MaterialTheme.shapes.large,
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "CONTROLS & GESTURES",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "• 2 Fingers: Fluid simultaneous pinch zoom and pan\n• 1 Finger: Smooth drag to pan across the board\n• Tap: Fast flag placement or chord revealed numbers\n• Long Press: Uncover / reveal cells\n• Safe 1st move: Guaranteed never to hit a mine on opening",
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun GameScreen(
    engine: MinesweeperEngine,
    sessionKey: Int,
    currentTheme: AppColorTheme,
    onBackToMenu: () -> Unit,
    onGameOver: () -> Unit,
    onSettings: () -> Unit = {}
) {
    BackHandler { onBackToMenu() }
    val context = LocalContext.current
    var boardViewRef by remember { mutableStateOf<MinesweeperBoardView?>(null) }

    var gameStatus by remember { mutableIntStateOf(engine.getGameStatus()) }
    var minesRemaining by remember { mutableIntStateOf(engine.getMinesRemaining()) }
    var elapsedSeconds by remember { mutableIntStateOf(engine.getElapsedSeconds(System.currentTimeMillis())) }
    var scoreSaved by remember { mutableStateOf(false) }

    fun resetGame() {
        GameStateManager.clearSavedGame(context)
        engine.initGame(GameDifficulty.BOARD_ROWS, GameDifficulty.BOARD_COLS, GameDifficulty.BOARD_MINES)
        gameStatus = engine.getGameStatus()
        minesRemaining = engine.getMinesRemaining()
        elapsedSeconds = 0
        scoreSaved = false

        boardViewRef?.let {
            it.engine = engine
            it.longPressDurationMs = GameDifficulty.getLongPressDuration(context)
            it.setThemeColors(
                accentColor = currentTheme.primary.toArgb(),
                highlightColor = currentTheme.primaryLight.toArgb(),
                onAccentColor = currentTheme.onPrimary.toArgb(),
                tileBaseColor = currentTheme.surfaceTile.toArgb()
            )
            it.notifyNewGame()
        }
    }

    LaunchedEffect(currentTheme) {
        boardViewRef?.setThemeColors(
            accentColor = currentTheme.primary.toArgb(),
            highlightColor = currentTheme.primaryLight.toArgb(),
            onAccentColor = currentTheme.onPrimary.toArgb(),
            tileBaseColor = currentTheme.surfaceTile.toArgb()
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                engine.resumeGame(System.currentTimeMillis())
                elapsedSeconds = engine.getElapsedSeconds(System.currentTimeMillis())
            } else if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                engine.pauseGame(System.currentTimeMillis())
                GameStateManager.saveGame(context, engine)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        engine.resumeGame(System.currentTimeMillis())
        elapsedSeconds = engine.getElapsedSeconds(System.currentTimeMillis())

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            engine.pauseGame(System.currentTimeMillis())
        }
    }

    LaunchedEffect(sessionKey) {
        if (engine.getRows() != GameDifficulty.BOARD_ROWS || engine.getCols() != GameDifficulty.BOARD_COLS) {
            resetGame()
        } else {
            gameStatus = engine.getGameStatus()
            minesRemaining = engine.getMinesRemaining()
            elapsedSeconds = engine.getElapsedSeconds(System.currentTimeMillis())
            scoreSaved = false
            boardViewRef?.let {
                it.engine = engine
                it.longPressDurationMs = GameDifficulty.getLongPressDuration(context)
                it.setThemeColors(
                    accentColor = currentTheme.primary.toArgb(),
                    highlightColor = currentTheme.primaryLight.toArgb(),
                    onAccentColor = currentTheme.onPrimary.toArgb(),
                    tileBaseColor = currentTheme.surfaceTile.toArgb()
                )
                it.notifyNewGame()
            }
        }
    }

    // Timer tick loop: counts only while playing and not paused
    LaunchedEffect(gameStatus) {
        while (gameStatus == MinesweeperEngine.STATUS_PLAYING) {
            delay(1000)
            if (!engine.isPaused()) {
                elapsedSeconds = engine.getElapsedSeconds(System.currentTimeMillis())
            }
        }
    }

    // Save score when won
    LaunchedEffect(gameStatus) {
        if (gameStatus == MinesweeperEngine.STATUS_WON && !scoreSaved) {
            LeaderboardManager.recordGameWin(context, max(1, elapsedSeconds))
            scoreSaved = true
            onGameOver()
        } else if (gameStatus == MinesweeperEngine.STATUS_LOST) {
            onGameOver()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Material 3 Floating Top HUD Bar (flat, no outline highlight)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(10f)
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Back button & Mines counter pill (flat, no highlight/border)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalIconButton(
                    onClick = onBackToMenu,
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.size(40.dp)
                ) {
                    Text(
                        text = "←",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.height(40.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 14.dp)
                    ) {
                        MineGearLogo(
                            modifier = Modifier.size(15.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = max(0, minesRemaining).toString(),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Center: Minimal Timer Pill (flat, no highlight/border)
            val m = min(999, elapsedSeconds) / 60
            val s = min(999, elapsedSeconds) % 60
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.height(40.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = String.format("%02d:%02d", m, s),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Right: Settings gear (flat, no highlight/border)
            FilledTonalIconButton(
                onClick = onSettings,
                shape = CircleShape,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.size(40.dp)
            ) {
                Text(
                    text = "⚙",
                    fontSize = 18.sp
                )
            }
        }

        // Board Area: Vertical Gesture-only zoom/pan Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clipToBounds()
        ) {
            AndroidView(
                factory = { ctx ->
                    MinesweeperBoardView(ctx).apply {
                        boardViewRef = this
                        this.engine = engine
                        longPressDurationMs = GameDifficulty.getLongPressDuration(ctx)
                        setThemeColors(
                            accentColor = currentTheme.primary.toArgb(),
                            highlightColor = currentTheme.primaryLight.toArgb(),
                            onAccentColor = currentTheme.onPrimary.toArgb(),
                            tileBaseColor = currentTheme.surfaceTile.toArgb()
                        )
                        onGameStateChangedListener = { newStatus, newRemaining ->
                            gameStatus = newStatus
                            minesRemaining = newRemaining
                            if (newStatus == MinesweeperEngine.STATUS_PLAYING || newStatus == MinesweeperEngine.STATUS_NOT_STARTED) {
                                GameStateManager.saveGame(ctx, engine)
                            } else {
                                GameStateManager.clearSavedGame(ctx)
                            }
                        }
                    }
                },
                update = { view ->
                    boardViewRef = view
                    view.engine = engine
                    view.setThemeColors(
                        accentColor = currentTheme.primary.toArgb(),
                        highlightColor = currentTheme.primaryLight.toArgb(),
                        onAccentColor = currentTheme.onPrimary.toArgb(),
                        tileBaseColor = currentTheme.surfaceTile.toArgb()
                    )
                },
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
            )

            // Material 3 Bottom Sheet Style Result Floating Overlay (flat, no outline highlight)
            if (gameStatus == MinesweeperEngine.STATUS_WON || gameStatus == MinesweeperEngine.STATUS_LOST) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            color = MaterialTheme.colorScheme.surface,
                            shape = MaterialTheme.shapes.extraLarge
                        )
                        .clip(MaterialTheme.shapes.extraLarge)
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 18.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Material 3 Drag Handle Pill
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.outline)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = if (gameStatus == MinesweeperEngine.STATUS_WON) "PUZZLE SOLVED" else "GAME OVER",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (gameStatus == MinesweeperEngine.STATUS_WON)
                                "Time: ${elapsedSeconds}s • Recorded to fastest games"
                            else
                                "Mine detonated. Tap new game to try again.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { resetGame() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = CircleShape,
                                elevation = ButtonDefaults.buttonElevation(
                                    defaultElevation = 0.dp,
                                    pressedElevation = 0.dp,
                                    focusedElevation = 0.dp,
                                    hoveredElevation = 0.dp
                                )
                            ) {
                                Text(
                                    text = "NEW GAME",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                            FilledTonalButton(
                                onClick = { onBackToMenu() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = CircleShape,
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                elevation = ButtonDefaults.filledTonalButtonElevation(
                                    defaultElevation = 0.dp,
                                    pressedElevation = 0.dp,
                                    focusedElevation = 0.dp,
                                    hoveredElevation = 0.dp
                                )
                            ) {
                                Text(
                                    text = "MAIN MENU",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}