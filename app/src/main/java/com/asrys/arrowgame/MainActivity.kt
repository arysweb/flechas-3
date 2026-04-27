package com.asrys.arrowgame

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlin.math.min

private val AppBg = Color(0xFF050A1F)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = AppBg,
                    surface = AppBg
                )
            ) {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot(vm: GameViewModel = viewModel()) {
    var inGame by rememberSaveable { mutableStateOf(false) }
    val state by vm.state.collectAsState()
    if (!inGame) {
        MainMenu(
            levelNumber = state.puzzleNumber,
            onPlay = {
                vm.startRandomPuzzle()
                inGame = true
            }
        )
        return
    }
    GameScreen(vm = vm, onReturnToMenu = { inGame = false })
}

@Composable
private fun MainMenu(levelNumber: Int, onPlay: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBg)
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Arrow Puzzle",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 42.sp
            )
            Text(
                text = "Level: $levelNumber",
                color = Color.LightGray,
                fontSize = 24.sp,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
        Button(
            onClick = onPlay,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E5BFF)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .fillMaxWidth(0.8f)
                .height(64.dp)
        ) {
            Text("Play", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}

data class ConfettiParticle(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val color: Color,
    val radius: Float
)

@Composable
private fun SuccessScreen(onAnimationEnd: () -> Unit) {
    val words = listOf("AWESOME!", "NICE!", "BRAVO!", "GENIUS!", "AMAZING!", "SPECTACULAR!", "BRILLIANT!", "SMASHED IT!", "HOLY MOLY!")
    val word = remember { words.random() }
    
    var particles by remember { mutableStateOf<List<ConfettiParticle>>(emptyList()) }
    
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(AppBg)
    ) {
        val density = LocalDensity.current.density
        val screenWidth = maxWidth.value * density
        val screenHeight = maxHeight.value * density
        
        LaunchedEffect(Unit) {
            val colors = listOf(
                Color(0xFF90A4AE), // Muted Blue-Grey
                Color(0xFFAED581), // Muted Light Green
                Color(0xFF7986CB), // Muted Indigo
                Color(0xFFFFB74D), // Muted Orange
                Color(0xFFBA68C8), // Muted Purple
                Color(0xFF4DD0E1)  // Muted Cyan
            )
            var currentParticles = List(150) {
                ConfettiParticle(
                    x = screenWidth * kotlin.random.Random.nextFloat(),
                    y = screenHeight,
                    vx = kotlin.random.Random.nextFloat() * 600 - 300,
                    vy = -(kotlin.random.Random.nextFloat() * screenHeight * 0.45f + screenHeight * 1.0f),
                    color = colors.random(),
                    radius = kotlin.random.Random.nextFloat() * 15f + 10f
                )
            }
            particles = currentParticles
            
            val startTime = System.nanoTime()
            var lastTime = startTime
            while (true) {
                androidx.compose.runtime.withFrameNanos { time ->
                    val dt = (time - lastTime) / 1_000_000_000f
                    lastTime = time
                    
                    currentParticles = currentParticles.map { p ->
                        p.copy(
                            x = p.x + p.vx * dt,
                            y = p.y + p.vy * dt,
                            vy = p.vy + screenHeight * 1.5f * dt
                        )
                    }
                    particles = currentParticles
                }
                if (System.nanoTime() - startTime > 2_500_000_000L) {
                    break
                }
            }
            onAnimationEnd()
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            particles.forEach { p ->
                drawCircle(
                    color = p.color,
                    radius = p.radius,
                    center = Offset(p.x, p.y)
                )
            }
        }
        
        Text(
            text = word,
            color = Color.White,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
private fun GameScreen(vm: GameViewModel, onReturnToMenu: () -> Unit) {
    val state by vm.state.collectAsState()
    val level = state.puzzle ?: return

    var showSuccessScreen by remember(level.id) { mutableStateOf(false) }
    var waveProgress by remember(level.id) { mutableStateOf(0f) }

    if (showSuccessScreen) {
        SuccessScreen(
            onAnimationEnd = {
                vm.incrementLevel()
                onReturnToMenu()
            }
        )
        return
    }

    var scale by remember(level.id) { mutableStateOf(1.0f) }
    var offset by remember(level.id) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(state.isLevelComplete) {
        if (state.isLevelComplete) {
            val startScale = scale
            val startOffset = offset
            
            if (startScale > 1.0f || startOffset != Offset.Zero) {
                androidx.compose.animation.core.Animatable(0f).animateTo(
                    targetValue = 1f,
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 500)
                ) {
                    val t = this.value
                    scale = startScale + (1.0f - startScale) * t
                    offset = androidx.compose.ui.geometry.lerp(startOffset, Offset.Zero, t)
                }
            }

            androidx.compose.animation.core.Animatable(0f).animateTo(
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 1000)
            ) {
                waveProgress = this.value
            }
            delay(100L)
            showSuccessScreen = true
        }
    }

    LaunchedEffect(state.isGameOver) {
        if (state.isGameOver) {
            delay(180L)
            vm.resetPuzzle()
        }
    }

    LaunchedEffect(level.id) {
        scale = 1.0f
        offset = Offset.Zero
        androidx.compose.animation.core.Animatable(1.0f).animateTo(
            targetValue = 2.2f,
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 1500)
        ) {
            scale = this.value
        }
    }

    val density = LocalDensity.current
    val paddingPx = with(density) { 24.dp.toPx() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBg)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1.0f, 2.2f)
                    // The box width is screen width minus left and right padding
                    val boxSize = size.width.toFloat() - paddingPx * 2f
                    // By calculating maxOffset from the box size, the visual edge of the scaled 
                    // content will perfectly align with the padding boundary when fully panned!
                    val maxOffsetX = (boxSize * newScale - boxSize) / 2f
                    val maxOffsetY = (boxSize * newScale - boxSize) / 2f
                    
                    scale = newScale
                    val limitX = maxOf(0f, maxOffsetX)
                    val limitY = maxOf(0f, maxOffsetY)
                    
                    offset = Offset(
                        x = (offset.x + pan.x).coerceIn(-limitX, limitX),
                        y = (offset.y + pan.y).coerceIn(-limitY, limitY)
                    )
                }
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        ) {
            ArrowBoard(
                level = level,
                state = state,
                waveProgress = waveProgress,
                onArrowTap = { id -> vm.onArrowTap(id, scale) }
            )
        }
    }
}

@Composable
private fun ArrowBoard(
    level: LevelMask,
    state: GameState,
    waveProgress: Float,
    onArrowTap: (Int) -> Unit
) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(state.remaining) {
                // onPress fires on finger-DOWN — maximally responsive, no release delay.
                detectTapGestures(
                    onPress = { tapOffset ->
                        if (state.isGameOver || state.isLevelComplete) return@detectTapGestures
                        val cw = size.width / level.width
                        val ch = size.height / level.height
                        val tx = (tapOffset.x / cw).toInt()
                        val ty = (tapOffset.y / ch).toInt()
                        state.remaining.firstOrNull { arrow ->
                            arrow.path.any { it.x == tx && it.y == ty } ||
                                (arrow.path.isEmpty() && arrow.start.x == tx && arrow.start.y == ty)
                        }?.let {
                            onArrowTap(it.id)
                        }
                    }
                )
            }
    ) {
        val cw = size.width / level.width
        val ch = size.height / level.height
        val stroke = min(cw, ch) * 0.13f
        val dotRadius = min(cw, ch) * 0.09f
        val clearedDotColor = Color(0xFF9FB4FF).copy(alpha = 0.16f)
        val movingById = state.movingArrows.associateBy { it.id }
        val allArrowCells = level.arrows.flatMap { it.occupiedCells() }.toSet()
        val remainingCells = state.remaining.flatMap { it.occupiedCells() }.toSet()
        val movingVacatedCells = state.movingArrows.flatMap { moving ->
            val arrow = level.arrows.firstOrNull { it.id == moving.id } ?: return@flatMap emptyList()
            val cells = arrow.occupiedCells()
            val vacatedCount = moving.progressCells.toInt().coerceIn(0, cells.size)
            cells.take(vacatedCount)
        }.toSet()
        val clearedCells = (allArrowCells - remainingCells) + movingVacatedCells

        val waveHeight = size.height * 0.3f
        val waveY = size.height + waveHeight - (size.height + waveHeight * 2) * waveProgress

        for (cell in clearedCells) {
            val cx = cell.x * cw + cw / 2f
            val cy = cell.y * ch + ch / 2f
            
            var currentRadius = dotRadius
            var currentColor = clearedDotColor
            
            if (waveProgress > 0f && waveProgress < 1f) {
                val dist = kotlin.math.abs(cy - waveY)
                if (dist < waveHeight) {
                    val intensity = 1f - (dist / waveHeight)
                    val smoothIntensity = kotlin.math.sin(intensity * kotlin.math.PI / 2.0).toFloat()
                    
                    currentRadius = dotRadius * (1f + 1.5f * smoothIntensity)
                    val alpha = (0.16f + 0.84f * smoothIntensity).coerceIn(0f, 1f)
                    val darkBluePulse = Color(0xFF1E3A8A)
                    currentColor = lerp(clearedDotColor, darkBluePulse, smoothIntensity).copy(alpha = alpha)
                }
            }

            drawCircle(
                color = currentColor,
                radius = currentRadius,
                center = Offset(cx, cy)
            )
        }

        for (arrow in state.remaining) {
            val moving = movingById[arrow.id]
            val movingRatio = if (moving == null || moving.maxProgressCells <= 0f) 0f
            else (moving.progressCells / moving.maxProgressCells).coerceIn(0f, 1f)
            val movingColor = lerp(Color.White, Color(0xFF00E676), movingRatio)
            val color = when {
                state.lastBlockedArrowId == arrow.id -> Color(0xFFE53935)
                moving != null -> movingColor
                else -> Color.White
            }
            drawArrowPath(
                arrow = arrow,
                cellW = cw,
                cellH = ch,
                color = color,
                stroke = stroke,
                progressCells = moving?.progressCells ?: 0f
            )
        }
    }
}

private fun ArrowPiece.occupiedCells(): List<Cell> {
    return if (path.isNotEmpty()) path else listOf(start)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArrowPath(
    arrow: ArrowPiece,
    cellW: Float,
    cellH: Float,
    color: Color,
    stroke: Float,
    progressCells: Float = 0f
) {
    val base = min(cellW, cellH)
    val headLen = base * 0.38f
    val headHalfWidth = base * 0.24f
    val isMoving = progressCells > 0f

    val cells = if (arrow.path.isNotEmpty()) arrow.path else listOf(arrow.start)
    val rawPoints = computeRopeFollowPoints(cells, arrow.direction, progressCells, cellW, cellH)
    val points = rawPoints
    if (points.isEmpty()) return

    val fallbackDir = when (arrow.direction) {
        Direction.UP -> 0f to -1f
        Direction.RIGHT -> 1f to 0f
        Direction.DOWN -> 0f to 1f
        Direction.LEFT -> -1f to 0f
    }

    if (points.size == 1) {
        val end = points.last()
        val (dx, dy) = fallbackDir
        val tailStart = Offset(
            x = end.x - dx * (base * 0.42f),
            y = end.y - dy * (base * 0.42f)
        )
        val shaftEnd = Offset(
            x = end.x - dx * (headLen * 0.70f),
            y = end.y - dy * (headLen * 0.70f)
        )
        drawLine(
            color = color,
            start = tailStart,
            end = shaftEnd,
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }

    if (points.size > 1) {
        val bodyPath = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 1 until points.lastIndex) {
                lineTo(points[i].x, points[i].y)
            }
            val beforeHead = points[points.lastIndex - 1]
            val tip = points.last()
            val vx = tip.x - beforeHead.x
            val vy = tip.y - beforeHead.y
            val mag = kotlin.math.sqrt(vx * vx + vy * vy).coerceAtLeast(0.0001f)
            val ux = vx / mag
            val uy = vy / mag
            val retreat = min(headLen * 0.70f, mag * 0.45f)
            val shaftEnd = Offset(
                x = tip.x - ux * retreat,
                y = tip.y - uy * retreat
            )
            lineTo(shaftEnd.x, shaftEnd.y)
        }
        drawPath(
            path = bodyPath,
            color = color,
            style = Stroke(
                width = stroke,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }

    val end = points.last()
    val (dx, dy) = if (isMoving) {
        // Keep head orientation stable while moving.
        fallbackDir
    } else if (points.size > 1) {
        // Use a slightly longer look-back while moving to reduce direction jitter at corners.
        val lookBack = 1
        val prev = points[(points.lastIndex - lookBack).coerceAtLeast(0)]
        val vx = end.x - prev.x
        val vy = end.y - prev.y
        val mag = kotlin.math.sqrt(vx * vx + vy * vy).coerceAtLeast(0.0001f)
        (vx / mag) to (vy / mag)
    } else {
        fallbackDir
    }
    val nx = -dy
    val ny = dx
    val left = Offset(
        x = end.x - dx * headLen + nx * headHalfWidth,
        y = end.y - dy * headLen + ny * headHalfWidth
    )
    val right = Offset(
        x = end.x - dx * headLen - nx * headHalfWidth,
        y = end.y - dy * headLen - ny * headHalfWidth
    )
    val triangle = Path().apply {
        moveTo(end.x, end.y)
        lineTo(left.x, left.y)
        lineTo(right.x, right.y)
        close()
    }
    drawPath(path = triangle, color = color, style = Stroke(width = stroke * 0.3f))
    drawPath(path = triangle, color = color)
}

private fun computeRopeFollowPoints(
    cells: List<Cell>,
    direction: Direction,
    progressCells: Float,
    cellW: Float,
    cellH: Float
): List<Offset> {
    if (cells.isEmpty()) return emptyList()
    val tip = cells.last()

    fun trackCell(i: Int): Cell {
        return if (i < cells.size) {
            cells[i]
        } else {
            val extra = i - (cells.size - 1)
            Cell(
                x = tip.x + direction.dx * extra,
                y = tip.y + direction.dy * extra
            )
        }
    }

    fun pointAt(t: Float): Offset {
        val i = kotlin.math.floor(t).toInt()
        val frac = t - i
        val a = trackCell(i)
        val b = trackCell(i + 1)
        val x = (a.x + (b.x - a.x) * frac) * cellW + cellW / 2f
        val y = (a.y + (b.y - a.y) * frac) * cellH + cellH / 2f
        return Offset(x, y)
    }

    val points = mutableListOf<Offset>()
    val tTail = progressCells
    val tHead = (cells.size - 1).coerceAtLeast(0).toFloat() + progressCells

    points.add(pointAt(tTail))

    val firstInt = kotlin.math.floor(tTail).toInt() + 1
    val lastInt = kotlin.math.ceil(tHead).toInt() - 1

    for (i in firstInt..lastInt) {
        val c = trackCell(i)
        val p = Offset(c.x * cellW + cellW / 2f, c.y * cellH + cellH / 2f)
        val lastP = points.last()
        val distSq = (p.x - lastP.x) * (p.x - lastP.x) + (p.y - lastP.y) * (p.y - lastP.y)
        if (distSq > 0.1f) {
            points.add(p)
        }
    }

    if (tHead > tTail) {
        val pHead = pointAt(tHead)
        val lastP = points.last()
        val distSq = (pHead.x - lastP.x) * (pHead.x - lastP.x) + (pHead.y - lastP.y) * (pHead.y - lastP.y)
        if (distSq > 0.1f) {
            points.add(pHead)
        }
    }

    return points
}
