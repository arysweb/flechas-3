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
        for (arrow in state.remaining) {
            val color = if (state.lastBlockedArrowId == arrow.id) Color(0xFFE53935) else Color.White
            drawArrowPath(arrow = arrow, cellW = cw, cellH = ch, color = color, stroke = stroke)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArrowPath(
    arrow: ArrowPiece,
    cellW: Float,
    cellH: Float,
    color: Color,
    stroke: Float
) {
    val base = min(cellW, cellH)
    val headLen = base * 0.24f
    val headHalfWidth = base * 0.12f

    val cells = if (arrow.path.isNotEmpty()) arrow.path else listOf(arrow.start)
    val points = cells.map { cell ->
        Offset(
            x = cell.x * cellW + cellW / 2f,
            y = cell.y * cellH + cellH / 2f
        )
    }
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
            cap = StrokeCap.Butt
        )
    }

    if (points.size > 1) {
        for (i in 0 until points.lastIndex) {
            val rawStart = points[i]
            val rawEnd = points[i + 1]
            val isLastSegment = i == points.lastIndex - 1
            val segEnd = if (isLastSegment) {
                val vx = rawEnd.x - rawStart.x
                val vy = rawEnd.y - rawStart.y
                val mag = kotlin.math.sqrt(vx * vx + vy * vy).coerceAtLeast(0.0001f)
                val ux = vx / mag
                val uy = vy / mag
                val retreat = min(headLen * 0.82f, mag * 0.45f)
                Offset(
                    x = rawEnd.x - ux * retreat,
                    y = rawEnd.y - uy * retreat
                )
            } else {
                rawEnd
            }
            drawLine(
                color = color,
                start = rawStart,
                end = segEnd,
                strokeWidth = stroke,
                cap = StrokeCap.Butt
            )
        }
    }

    val end = points.last()
    val (dx, dy) = if (points.size > 1) {
        val prev = points[points.lastIndex - 1]
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
