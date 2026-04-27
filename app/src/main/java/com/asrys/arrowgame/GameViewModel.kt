package com.asrys.arrowgame

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val initialPuzzle = LevelRepository.generatePuzzle(puzzleNumber = 1)
    private val _state = MutableStateFlow(
        GameState(
            puzzle = initialPuzzle,
            remaining = initialPuzzle.arrows
        )
    )
    val state: StateFlow<GameState> = _state

    fun onArrowTap(arrowId: Int) {
        val current = _state.value
        if (current.isGameOver || current.isLevelComplete) return
        if (current.movingArrows.any { it.id == arrowId }) return

        val level = current.puzzle ?: return
        val arrow = current.remaining.firstOrNull { it.id == arrowId } ?: return
        val pathBlocked = isPathObstructed(level, arrow, current.remaining)

        if (pathBlocked) {
            val nextLives = (current.lives - 1).coerceAtLeast(0)
            _state.update {
                it.copy(
                    lives = nextLives,
                    lastBlockedArrowId = arrow.id,
                    isGameOver = nextLives == 0
                )
            }
            return
        }

        startArrowExitAnimation(level, arrow)
    }

    fun nextPuzzle() {
        val nextNumber = _state.value.puzzleNumber + 1
        val puzzle = LevelRepository.generatePuzzle(puzzleNumber = nextNumber)
        _state.update {
            it.copy(
                puzzleNumber = nextNumber,
                puzzle = puzzle,
                remaining = puzzle.arrows,
                movingArrows = emptyList(),
                lastBlockedArrowId = null,
                lives = 5,
                isGameOver = false,
                isLevelComplete = false
            )
        }
    }

    fun startRandomPuzzle() {
        val puzzle = LevelRepository.generatePuzzle(puzzleNumber = _state.value.puzzleNumber)
        _state.update {
            it.copy(
                puzzle = puzzle,
                remaining = puzzle.arrows,
                movingArrows = emptyList(),
                lastBlockedArrowId = null,
                lives = 5,
                isGameOver = false,
                isLevelComplete = false
            )
        }
    }

    fun resetPuzzle() {
        val current = _state.value
        val puzzle = LevelRepository.generatePuzzle(puzzleNumber = current.puzzleNumber)
        _state.update {
            it.copy(
                puzzle = puzzle,
                remaining = puzzle.arrows,
                movingArrows = emptyList(),
                lastBlockedArrowId = null,
                lives = 5,
                isGameOver = false,
                isLevelComplete = false
            )
        }
    }

    private fun isPathObstructed(level: LevelMask, arrow: ArrowPiece, all: List<ArrowPiece>): Boolean {
        val occupied = all
            .filterNot { it.id == arrow.id }
            .flatMap { other -> if (other.path.isNotEmpty()) other.path else listOf(other.start) }
            .toHashSet()
        val origin = arrow.path.lastOrNull() ?: arrow.start
        var x = origin.x + arrow.direction.dx
        var y = origin.y + arrow.direction.dy
        while (x >= 0 && y >= 0 && x < level.width && y < level.height) {
            if (occupied.contains(Cell(x, y))) return true
            x += arrow.direction.dx
            y += arrow.direction.dy
        }
        return false
    }

    private fun startArrowExitAnimation(level: LevelMask, arrow: ArrowPiece) {
        val puzzleId = level.id
        val maxProgress = computeMaxProgressToExit(level, arrow)
        _state.update { state ->
            if (state.movingArrows.any { it.id == arrow.id }) state
            else state.copy(
                movingArrows = state.movingArrows + MovingArrowState(
                    id = arrow.id,
                    progressCells = 0f,
                    maxProgressCells = maxProgress
                ),
                lastBlockedArrowId = null
            )
        }

        viewModelScope.launch {
            val frameMs = 5L
            val speedCellsPerSecond = 12.0f
            var progress = 0f
            while (progress < maxProgress) {
                delay(frameMs)
                progress = (progress + speedCellsPerSecond * (frameMs / 1000f)).coerceAtMost(maxProgress)
                val progressSnapshot = progress
                _state.update { state ->
                    if (state.puzzle?.id != puzzleId) return@update state
                    if (state.movingArrows.none { it.id == arrow.id }) return@update state
                    state.copy(
                        movingArrows = state.movingArrows.map { moving ->
                            if (moving.id == arrow.id) moving.copy(progressCells = progressSnapshot) else moving
                        }
                    )
                }
            }

            _state.update { state ->
                if (state.puzzle?.id != puzzleId) return@update state
                val newRemaining = state.remaining.filterNot { it.id == arrow.id }
                state.copy(
                    remaining = newRemaining,
                    movingArrows = state.movingArrows.filterNot { it.id == arrow.id },
                    lastBlockedArrowId = null,
                    isLevelComplete = newRemaining.isEmpty()
                )
            }
        }
    }

    private fun computeMaxProgressToExit(level: LevelMask, arrow: ArrowPiece): Float {
        val cells = if (arrow.path.isNotEmpty()) arrow.path else listOf(arrow.start)
        var maxSteps = 0
        for (cell in cells) {
            val steps = when (arrow.direction) {
                Direction.RIGHT -> level.width - cell.x
                Direction.LEFT -> cell.x + 1
                Direction.DOWN -> level.height - cell.y
                Direction.UP -> cell.y + 1
            }
            if (steps > maxSteps) maxSteps = steps
        }
        // Add extra travel so the entire arrow body/head fully leaves the visible board.
        val offscreenMarginCells = 2.4f
        return maxSteps.toFloat() + offscreenMarginCells
    }
}
