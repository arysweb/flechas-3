package com.asrys.arrowgame

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

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

        _state.update { state ->
            val newRemaining = state.remaining.filterNot { it.id == arrow.id }
            state.copy(
                remaining = newRemaining,
                lastBlockedArrowId = null,
                isLevelComplete = newRemaining.isEmpty()
            )
        }
    }

    fun nextPuzzle() {
        val nextNumber = _state.value.puzzleNumber + 1
        val puzzle = LevelRepository.generatePuzzle(puzzleNumber = nextNumber)
        _state.update {
            it.copy(
                puzzleNumber = nextNumber,
                puzzle = puzzle,
                remaining = puzzle.arrows,
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
}
