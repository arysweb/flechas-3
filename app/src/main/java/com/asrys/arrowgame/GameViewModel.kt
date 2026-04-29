package com.asrys.arrowgame

import android.app.Application
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val api = GameApi.create()
    private val seedPool = mutableListOf<Int>()
    private val deviceId: String by lazy {
        Settings.Secure.getString(
            getApplication<Application>().contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: ""
    }

    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state

    init {
        // Fetch seeds and load remote progression before creating the first puzzle.
        fetchSeeds()
        restoreProgressAndStart()
    }

    private fun fetchSeeds() {
        viewModelScope.launch {
            try {
                val response = api.getPuzzles(20)
                seedPool.addAll(response.seeds)
                Log.d("ArrowGame", "Successfully fetched ${response.seeds.size} seeds from API")
            } catch (e: Exception) {
                val errorBody = (e as? retrofit2.HttpException)?.response()?.errorBody()?.string()
                Log.e("ArrowGame", "Failed to fetch seeds: ${e.message}")
                if (errorBody != null) Log.e("ArrowGame", "Server Error Body: $errorBody")
                // Fallback: local generation works fine if offline
            }
        }
    }

    private fun getNextSeed(): Int {
        if (seedPool.size < 5) fetchSeeds()
        return if (seedPool.isNotEmpty()) seedPool.removeAt(0) else kotlin.random.Random.nextInt()
    }

    fun onArrowTap(arrowId: Int, scale: Float = 1f) {
        val current = _state.value
        if (current.isGameOver || current.isLevelComplete) return
        if (current.movingArrows.any { it.id == arrowId }) return

        val level = current.puzzle ?: return
        val arrow = current.remaining.firstOrNull { it.id == arrowId } ?: return
        
        val collisionDistance = getCollisionDistance(level, arrow, current.remaining, current.movingArrows)

        if (collisionDistance != null) {
            startObstructedArrowAnimation(level, arrow, collisionDistance, scale)
            return
        }

        startArrowExitAnimation(level, arrow, scale)
    }

    fun resumeWithOneLife() {
        _state.update {
            it.copy(
                lives = 1,
                isGameOver = false
            )
        }
    }

    fun submitStats(timeSeconds: Int) {
        val seed = _state.value.currentSeed ?: return
        viewModelScope.launch {
            try {
                api.submitStats(StatsRequest(seed, timeSeconds.toDouble(), deviceId))
                Log.d("ArrowGame", "Successfully submitted stats for seed $seed")
            } catch (e: Exception) {
                val errorBody = (e as? retrofit2.HttpException)?.response()?.errorBody()?.string()
                Log.e("ArrowGame", "Failed to submit stats: ${e.message}")
                if (errorBody != null) Log.e("ArrowGame", "Server Error Body: $errorBody")
            }
        }
    }

    fun nextPuzzle() {
        // Save the level the player just finished.
        val finishedPuzzleNumber = _state.value.puzzleNumber
        val nextNumber = finishedPuzzleNumber + 1
        val seed = getNextSeed()
        val puzzle = LevelRepository.generatePuzzle(puzzleNumber = nextNumber, seed = seed)
        _state.update {
            it.copy(
                puzzleNumber = nextNumber,
                puzzle = puzzle,
                currentSeed = seed,
                remaining = puzzle.arrows,
                movingArrows = emptyList(),
                lastBlockedArrowId = null,
                lives = 3,
                collisionTrigger = 0,
                isGameOver = false,
                isLevelComplete = false
            )
        }
        // Store "last completed level", not "next level".
        persistProgress(finishedPuzzleNumber)
    }

    fun startRandomPuzzle() {
        val currentPuzzleNumber = _state.value.puzzleNumber
        val seed = getNextSeed()
        val puzzle = LevelRepository.generatePuzzle(puzzleNumber = currentPuzzleNumber, seed = seed)
        _state.update {
            it.copy(
                puzzle = puzzle,
                currentSeed = seed,
                remaining = puzzle.arrows,
                movingArrows = emptyList(),
                lastBlockedArrowId = null,
                lives = 3,
                collisionTrigger = 0,
                isGameOver = false,
                isLevelComplete = false
            )
        }
        // Do not persist here: we only persist when the player completes the level.
    }

    private fun restoreProgressAndStart() {
        viewModelScope.launch {
            val remotePuzzleNumber = loadRemotePuzzleNumber()
            _state.update { it.copy(puzzleNumber = remotePuzzleNumber) }
            startRandomPuzzle()
        }
    }

    private suspend fun loadRemotePuzzleNumber(): Int {
        if (deviceId.isBlank()) return 1
        return try {
            val response = api.getProgress(deviceId)
            maxOf(1, response.current_puzzle_number, response.max_puzzle_number)
        } catch (e: Exception) {
            val errorBody = (e as? retrofit2.HttpException)?.response()?.errorBody()?.string()
            Log.e("ArrowGame", "Failed to load progress: ${e.message}")
            if (errorBody != null) Log.e("ArrowGame", "Server Error Body: $errorBody")
            1
        }
    }

    private fun persistProgress(puzzleNumber: Int) {
        if (deviceId.isBlank()) return
        viewModelScope.launch {
            try {
                api.saveProgress(SaveProgressRequest(deviceId, puzzleNumber))
                Log.d("ArrowGame", "Saved progress at puzzle $puzzleNumber")
            } catch (e: Exception) {
                val errorBody = (e as? retrofit2.HttpException)?.response()?.errorBody()?.string()
                Log.e("ArrowGame", "Failed to save progress: ${e.message}")
                if (errorBody != null) Log.e("ArrowGame", "Server Error Body: $errorBody")
            }
        }
    }

    fun resetPuzzle() {
        val current = _state.value
        val seed = current.currentSeed ?: getNextSeed()
        val puzzle = LevelRepository.generatePuzzle(puzzleNumber = current.puzzleNumber, seed = seed)
        _state.update {
            it.copy(
                puzzle = puzzle,
                currentSeed = seed,
                remaining = puzzle.arrows,
                movingArrows = emptyList(),
                lastBlockedArrowId = null,
                lives = 3,
                collisionTrigger = 0,
                isGameOver = false,
                isLevelComplete = false
            )
        }
    }

    private fun getCollisionDistance(level: LevelMask, arrow: ArrowPiece, all: List<ArrowPiece>, moving: List<MovingArrowState>): Float? {
        val movingIds = moving.map { it.id }.toSet()
        val occupied = all
            .filterNot { it.id == arrow.id || movingIds.contains(it.id) }
            .flatMap { other -> if (other.path.isNotEmpty()) other.path else listOf(other.start) }
            .toHashSet()
        val origin = arrow.path.lastOrNull() ?: arrow.start
        var x = origin.x + arrow.direction.dx
        var y = origin.y + arrow.direction.dy
        var distance = 1
        while (x >= 0 && y >= 0 && x < level.width && y < level.height) {
            if (occupied.contains(Cell(x, y))) return distance.toFloat() - 0.7f
            x += arrow.direction.dx
            y += arrow.direction.dy
            distance++
        }
        return null
    }

    private fun startObstructedArrowAnimation(level: LevelMask, arrow: ArrowPiece, distance: Float, scale: Float = 1f) {
        val puzzleId = level.id
        val maxProgress = distance
        
        _state.update { state ->
            if (state.movingArrows.any { it.id == arrow.id }) state
            else state.copy(
                movingArrows = state.movingArrows + MovingArrowState(
                    id = arrow.id,
                    progressCells = 0f,
                    maxProgressCells = maxProgress,
                    isObstructed = true
                ),
                lastBlockedArrowId = null
            )
        }

        viewModelScope.launch {
            val frameMs = 5L
            val clampedScale = scale.coerceIn(0.5f, 4f)
            var currentSpeed = (30.0f / clampedScale)
            val maxSpeed    = (150.0f / clampedScale)
            val acceleration = (400.0f / clampedScale)
            var progress = 0f
            while (progress < maxProgress) {
                delay(frameMs)
                currentSpeed = (currentSpeed + acceleration * (frameMs / 1000f)).coerceAtMost(maxSpeed)
                progress = (progress + currentSpeed * (frameMs / 1000f)).coerceAtMost(maxProgress)
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

            // Hit! Remove arrow from moving (snaps back) and trigger collision effects
            val current = _state.value
            val nextLives = (current.lives - 1).coerceAtLeast(0)
            
            _state.update { state ->
                if (state.puzzle?.id != puzzleId) return@update state
                state.copy(
                    movingArrows = state.movingArrows.filterNot { it.id == arrow.id },
                    lives = nextLives,
                    lastBlockedArrowId = arrow.id,
                    collisionTrigger = state.collisionTrigger + 1,
                    isGameOver = nextLives == 0
                )
            }
        }
    }

    private fun startArrowExitAnimation(level: LevelMask, arrow: ArrowPiece, scale: Float = 1f) {
        val puzzleId = level.id
        val maxProgress = computeExitProgress(level, arrow)
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
            // Speed is in cells/second. Divide by scale so that at 2× zoom,
            // the arrow travels at the same perceived pixel speed as at 1× zoom.
            val clampedScale = scale.coerceIn(0.5f, 4f)
            var currentSpeed = (30.0f / clampedScale)  // fast start
            val maxSpeed    = (150.0f / clampedScale)  // capped top speed
            val acceleration = (400.0f / clampedScale) // ramp-up
            var progress = 0f
            while (progress < maxProgress) {
                delay(frameMs)
                currentSpeed = (currentSpeed + acceleration * (frameMs / 1000f)).coerceAtMost(maxSpeed)
                progress = (progress + currentSpeed * (frameMs / 1000f)).coerceAtMost(maxProgress)
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

    private fun computeExitProgress(level: LevelMask, arrow: ArrowPiece): Float {
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
