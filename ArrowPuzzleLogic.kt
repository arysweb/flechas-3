/**
 * Core logic for the Arrow Puzzle game in Kotlin.
 * This can be used in a ViewModel or a custom View.
 */

enum class Direction(val dx: Int, val dy: Int) {
    UP(0, -1),
    DOWN(0, 1),
    LEFT(-1, 0),
    RIGHT(1, 0)
}

data class Arrow(
    val x: Int,
    val y: Int,
    val direction: Direction,
    var isExiting: Boolean = false
)

class ArrowPuzzleGame(val width: Int, val height: Int) {
    private val grid = Array(height) { arrayOfNulls<Arrow>(width) }
    
    // Callback for UI updates
    var onArrowExited: ((Arrow) -> Unit)? = null
    var onCollision: ((Arrow) -> Unit)? = null

    fun addArrow(x: Int, y: Int, direction: Direction) {
        if (x in 0 until width && y in 0 until height) {
            grid[y][x] = Arrow(x, y, direction)
        }
    }

    /**
     * Checks if the path from (x, y) in the arrow's direction is clear to the edge.
     */
    fun isPathClear(arrow: Arrow): Boolean {
        var currX = arrow.x + arrow.direction.dx
        var currY = arrow.y + arrow.direction.dy
        
        while (currX in 0 until width && currY in 0 until height) {
            if (grid[currY][currX] != null) {
                return false
            }
            currX += arrow.direction.dx
            currY += arrow.direction.dy
        }
        return true
    }

    /**
     * Handles tapping an arrow at (x, y).
     */
    fun handleTap(x: Int, y: Int) {
        val arrow = grid[y][x] ?: return
        
        if (isPathClear(arrow)) {
            // Success: Remove from grid and notify UI for animation
            grid[y][x] = null
            arrow.isExiting = true
            onArrowExited?.invoke(arrow)
        } else {
            // Failure: Collision
            onCollision?.invoke(arrow)
        }
    }

    fun getArrowAt(x: Int, y: Int): Arrow? = grid[y][x]
    
    fun isGameWon(): Boolean {
        for (row in grid) {
            for (cell in row) {
                if (cell != null) return false
            }
        }
        return true
    }
}
