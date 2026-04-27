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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
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
    if (!inGame) {
        MainMenu(onPlay = {
            vm.startRandomPuzzle()
            inGame = true
        })
        return
    }
    GameScreen(vm = vm)
}

@Composable
private fun MainMenu(onPlay: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBg)
            .padding(20.dp)
    ) {
        Text(
            text = "Arrow Puzzle",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 42.sp,
            modifier = Modifier.align(Alignment.Center)
        )
        Button(
            onClick = onPlay,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E5BFF)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .width(220.dp)
        ) {
            Text("Play", color = Color.White, fontSize = 20.sp)
        }
    }
}

@Composable
private fun GameScreen(vm: GameViewModel) {
    val state by vm.state.collectAsState()
    val level = state.puzzle ?: return

    LaunchedEffect(state.isLevelComplete, state.isGameOver) {
        when {
            state.isLevelComplete -> {
                delay(180L)
                vm.nextPuzzle()
            }
            state.isGameOver -> {
                delay(180L)
                vm.resetPuzzle()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(14.dp)
        ) {
            ArrowBoard(
                level = level,
                state = state,
                onArrowTap = vm::onArrowTap
            )
        }
    }
}

@Composable
private fun ArrowBoard(
    level: LevelMask,
    state: GameState,
    onArrowTap: (Int) -> Unit
) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(state.remaining) {
                detectTapGestures { offset ->
                    if (state.isGameOver || state.isLevelComplete) return@detectTapGestures
                    val cw = size.width / level.width
                    val ch = size.height / level.height
                    val tx = (offset.x / cw).toInt()
                    val ty = (offset.y / ch).toInt()
                    state.remaining.firstOrNull { arrow ->
                        arrow.path.any { it.x == tx && it.y == ty } ||
                            (arrow.path.isEmpty() && arrow.start.x == tx && arrow.start.y == ty)
                    }?.let {
                        onArrowTap(it.id)
                    }
                }
            }
    ) {
        val cw = size.width / level.width
        val ch = size.height / level.height
        val stroke = min(cw, ch) * 0.09f
        val dotRadius = min(cw, ch) * 0.07f
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

        for (cell in clearedCells) {
            drawCircle(
                color = clearedDotColor,
                radius = dotRadius,
                center = Offset(
                    x = cell.x * cw + cw / 2f,
                    y = cell.y * ch + ch / 2f
                )
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
    val headLen = base * 0.24f
    val headHalfWidth = base * 0.12f
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
            x = end.x - dx * (base * 0.34f),
            y = end.y - dy * (base * 0.34f)
        )
        val shaftEnd = Offset(
            x = end.x - dx * (headLen * 0.82f),
            y = end.y - dy * (headLen * 0.82f)
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
            val retreat = min(headLen * 0.82f, mag * 0.45f)
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
    val whole = kotlin.math.floor(progressCells).toInt().coerceAtLeast(0)
    val rawFrac = (progressCells - whole).coerceIn(0f, 1f)
    // Stronger easing inside each tile transition to avoid robotic corner snapping.
    val frac = rawFrac * rawFrac * (3f - 2f * rawFrac)

    fun sampleAt(index: Int): Cell {
        return if (index < cells.size) {
            cells[index]
        } else {
            val extra = index - (cells.size - 1)
            Cell(
                x = tip.x + direction.dx * extra,
                y = tip.y + direction.dy * extra
            )
        }
    }

    return cells.indices.map { i ->
        val a = sampleAt(i + whole)
        val b = sampleAt(i + whole + 1)
        val x = (a.x + (b.x - a.x) * frac) * cellW + cellW / 2f
        val y = (a.y + (b.y - a.y) * frac) * cellH + cellH / 2f
        Offset(x = x, y = y)
    }
}
