import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun ArrowPuzzleScreen(game: ArrowPuzzleGame) {
    // State to trigger recomposition when the grid changes
    var updateTrigger by remember { mutableStateOf(0) }
    
    // Set up callbacks
    LaunchedEffect(Unit) {
        game.onArrowExited = { 
            // In a real app, you'd trigger an animation here
            updateTrigger++ 
        }
        game.onCollision = {
            // Trigger haptic feedback or screen shake
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val cellSize = minOf(maxWidth, maxHeight) / maxOf(game.width, game.height).toFloat()
        
        Canvas(modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val x = (offset.x / cellSize.toPx()).toInt()
                    val y = (offset.y / cellSize.toPx()).toInt()
                    game.handleTap(x, y)
                }
            }
        ) {
            // Draw Grid Lines (Optional)
            for (i in 0..game.width) {
                drawLine(Color.LightGray, Offset(i * cellSize.toPx(), 0f), Offset(i * cellSize.toPx(), game.height * cellSize.toPx()))
            }
            for (j in 0..game.height) {
                drawLine(Color.LightGray, Offset(0f, j * cellSize.toPx()), Offset(game.width * cellSize.toPx(), j * cellSize.toPx()))
            }

            // Draw Arrows
            for (y in 0 until game.height) {
                for (x in 0 until game.width) {
                    val arrow = game.getArrowAt(x, y) ?: continue
                    drawArrow(arrow, x, y, cellSize.toPx())
                }
            }
        }
    }
}

fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArrow(arrow: Arrow, x: Int, y: Int, cellSize: Float) {
    val centerX = x * cellSize + cellSize / 2
    val centerY = y * cellSize + cellSize / 2
    val rotation = when (arrow.direction) {
        Direction.UP -> 0f
        Direction.RIGHT -> 90f
        Direction.DOWN -> 180f
        Direction.LEFT -> 270f
    }

    rotate(rotation, pivot = Offset(centerX, centerY)) {
        // Simple triangle arrow
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(centerX, centerY - cellSize * 0.4f)
            lineTo(centerX - cellSize * 0.2f, centerY + cellSize * 0.2f)
            lineTo(centerX + cellSize * 0.2f, centerY + cellSize * 0.2f)
            close()
        }
        drawPath(path, Color.Blue)
    }
}
