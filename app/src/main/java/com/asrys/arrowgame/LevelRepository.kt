package com.asrys.arrowgame

import kotlin.random.Random

object LevelRepository {
    private const val boardSize = 10

    fun generatePuzzle(puzzleNumber: Int, seed: Int = Random.nextInt()): LevelMask {
        val random = Random(seed)
        val width = boardSize
        val height = boardSize
        val allCells = (0 until height).flatMap { y -> (0 until width).map { x -> Cell(x, y) } }

        val arrows = generateCellBasedArrows(width, height, allCells, random)

        return LevelMask(
            id = "puzzle_$puzzleNumber",
            width = width,
            height = height,
            activeCells = allCells.toSet(),
            arrows = arrows
        )
    }

    private fun generateCellBasedArrows(
        width: Int,
        height: Int,
        cells: List<Cell>,
        random: Random
    ): List<ArrowPiece> {
        val centerX = (width - 1) / 2f
        val centerY = (height - 1) / 2f
        val blocked = mutableSetOf<Cell>()
        val arrows = mutableListOf<ArrowPiece>()

        val orderedCells = cells.sortedBy { c ->
            val dx = c.x - centerX
            val dy = c.y - centerY
            dx * dx + dy * dy
        }

        for (start in orderedCells) {
            if (start in blocked) continue
            val path = buildPathFromStart(start, width, height, centerX, centerY, blocked, random)
            addArrowIfValid(path, blocked, arrows)
        }

        // Second pass: fill remaining tiny gaps with very short arrows.
        for (start in orderedCells) {
            if (start in blocked) continue
            val filler = buildFillerPathFromStart(start, width, height, blocked, random)
            addArrowIfValid(filler, blocked, arrows)
        }

        return arrows
    }

    private fun buildPathFromStart(
        start: Cell,
        width: Int,
        height: Int,
        centerX: Float,
        centerY: Float,
        blocked: Set<Cell>,
        random: Random
    ): List<Cell> {
        val distanceNorm = normalizedDistance(start, width, height, centerX, centerY)
        val primaryRange = when {
            distanceNorm < 0.30f -> 7..10
            distanceNorm < 0.68f -> 4..7
            distanceNorm < 0.90f -> 2..4
            else -> 1..2
        }
        val allRanges = listOf(7..10, 4..7, 2..4, 1..2)
        val rangeOrder = buildList {
            add(primaryRange)
            addAll(allRanges.filter { it != primaryRange }.shuffled(random))
        }.shuffled(random)

        val primary = outwardDirection(start, centerX, centerY)
        val candidateDirs = (Direction.entries.shuffled(random) + primary).distinct()
        var best = emptyList<Cell>()
        var bestScore = Int.MIN_VALUE

        for (targetRange in rangeOrder) {
            for (targetLen in targetRange.last downTo targetRange.first) {
                val turnProfile = turnProfileForLength(targetLen)
                for (firstDir in candidateDirs) {
                    repeat(9) {
                        val candidate = growPath(
                            start = start,
                            firstDir = firstDir,
                            targetLength = targetLen,
                            width = width,
                            height = height,
                            blocked = blocked,
                            minTurns = turnProfile.first,
                            maxTurns = turnProfile.second,
                            random = random
                        )
                        if (candidate.isNotEmpty() && isExitClear(candidate, blocked, width, height)) {
                            val score = candidate.size * 100 + freeNeighborCount(candidate.lastOrNull(), blocked, width, height)
                            if (score > bestScore) {
                                best = candidate
                                bestScore = score
                            }
                            if (best.size >= targetLen) return best
                        }
                    }
                }
            }
        }
        return best
    }

    private fun buildFillerPathFromStart(
        start: Cell,
        width: Int,
        height: Int,
        blocked: Set<Cell>,
        random: Random
    ): List<Cell> {
        var best = emptyList<Cell>()
        var bestScore = Int.MIN_VALUE
        for (firstDir in Direction.entries.shuffled(random)) {
            repeat(7) {
                val candidate = growPath(
                    start = start,
                    firstDir = firstDir,
                    targetLength = 3,
                    width = width,
                    height = height,
                    blocked = blocked,
                    minTurns = 1,
                    maxTurns = 2,
                    random = random
                )
                if (candidate.isNotEmpty() && isExitClear(candidate, blocked, width, height)) {
                    val score = candidate.size * 100 + freeNeighborCount(candidate.lastOrNull(), blocked, width, height)
                    if (score > bestScore) {
                        best = candidate
                        bestScore = score
                    }
                }
            }
        }
        return if (best.size >= 2) best else emptyList()
    }

    private fun growPath(
        start: Cell,
        firstDir: Direction,
        targetLength: Int,
        width: Int,
        height: Int,
        blocked: Set<Cell>,
        minTurns: Int,
        maxTurns: Int,
        random: Random
    ): List<Cell> {
        val path = mutableListOf(start)
        val local = mutableSetOf(start)
        var current = start
        var dir = firstDir
        var turnsUsed = 0

        for (step in 1 until targetLength) {
            val options = mutableListOf(dir)
            if (turnsUsed < maxTurns) {
                options += turnLeft(dir)
                options += turnRight(dir)
            }
            val candidates = options.distinct().map { nextDir ->
                val next = Cell(current.x + nextDir.dx, current.y + nextDir.dy)
                nextDir to next
            }.filter { (_, next) ->
                next.x in 0 until width &&
                    next.y in 0 until height &&
                    next !in blocked &&
                    next !in local
            }
            if (candidates.isEmpty()) break

            val (pickedDir, next) = candidates.minByOrNull { (nextDir, nextCell) ->
                val isTurn = nextDir != dir
                val turnBias = when {
                    turnsUsed < minTurns && isTurn -> -9
                    turnsUsed < minTurns && !isTurn -> 10
                    isTurn -> -3
                    else -> 2
                }
                val openness = freeNeighborCount(nextCell, blocked + local, width, height)
                // Prefer tighter/crowded continuations for more visual tangles.
                turnBias + (openness * 2) + random.nextInt(0, 5)
            } ?: break

            if (pickedDir != dir) turnsUsed++
            dir = pickedDir

            path += next
            local += next
            current = next
        }

        return path
    }

    private fun turnProfileForLength(length: Int): Pair<Int, Int> = when {
        length >= 9 -> 4 to 7   // extra long: heavy zig-zag
        length >= 7 -> 3 to 6   // long: multi-bend
        length >= 4 -> 2 to 4   // mid: visibly bendy
        else -> 1 to 2          // short/tiny: can still kink
    }

    private fun freeNeighborCount(cell: Cell?, blocked: Set<Cell>, width: Int, height: Int): Int {
        if (cell == null) return 0
        var count = 0
        for (dir in Direction.entries) {
            val nx = cell.x + dir.dx
            val ny = cell.y + dir.dy
            if (nx in 0 until width && ny in 0 until height && Cell(nx, ny) !in blocked) {
                count++
            }
        }
        return count
    }

    private fun outwardDirection(start: Cell, centerX: Float, centerY: Float): Direction {
        val dx = start.x - centerX
        val dy = start.y - centerY
        return if (kotlin.math.abs(dx) >= kotlin.math.abs(dy)) {
            if (dx >= 0f) Direction.RIGHT else Direction.LEFT
        } else {
            if (dy >= 0f) Direction.DOWN else Direction.UP
        }
    }

    private fun normalizedDistance(
        cell: Cell,
        width: Int,
        height: Int,
        centerX: Float,
        centerY: Float
    ): Float {
        val dx = cell.x - centerX
        val dy = cell.y - centerY
        val distance = kotlin.math.sqrt(dx * dx + dy * dy)
        val maxDistance = kotlin.math.sqrt(centerX * centerX + centerY * centerY).coerceAtLeast(0.0001f)
        return (distance / maxDistance).coerceIn(0f, 1f)
    }

    private fun directionAtTip(path: List<Cell>): Direction {
        if (path.size < 2) return Direction.UP
        val prev = path[path.lastIndex - 1]
        val tip = path.last()
        return when {
            tip.x > prev.x -> Direction.RIGHT
            tip.x < prev.x -> Direction.LEFT
            tip.y > prev.y -> Direction.DOWN
            else -> Direction.UP
        }
    }

    private fun isExitClear(path: List<Cell>, blocked: Set<Cell>, width: Int, height: Int): Boolean {
        if (path.size < 2) return false
        val tip = path.last()
        val dir = directionAtTip(path)
        var x = tip.x + dir.dx
        var y = tip.y + dir.dy
        while (x in 0 until width && y in 0 until height) {
            if (Cell(x, y) in blocked) return false
            x += dir.dx
            y += dir.dy
        }
        return true
    }

    private fun bendType(path: List<Cell>): ArrowBend {
        if (path.size < 3) return ArrowBend.STRAIGHT
        val first = directionBetween(path[0], path[1]) ?: return ArrowBend.STRAIGHT
        for (i in 1 until path.lastIndex) {
            val next = directionBetween(path[i], path[i + 1]) ?: continue
            if (next != first) {
                return when {
                    turnLeft(first) == next -> ArrowBend.LEFT_90
                    turnRight(first) == next -> ArrowBend.RIGHT_90
                    else -> ArrowBend.STRAIGHT
                }
            }
        }
        return ArrowBend.STRAIGHT
    }

    private fun directionBetween(a: Cell, b: Cell): Direction? = when {
        b.x == a.x + 1 && b.y == a.y -> Direction.RIGHT
        b.x == a.x - 1 && b.y == a.y -> Direction.LEFT
        b.x == a.x && b.y == a.y + 1 -> Direction.DOWN
        b.x == a.x && b.y == a.y - 1 -> Direction.UP
        else -> null
    }

    private fun reservePathWithPadding(path: List<Cell>, blocked: MutableSet<Cell>) {
        blocked += path
    }

    private fun addArrowIfValid(path: List<Cell>, blocked: MutableSet<Cell>, arrows: MutableList<ArrowPiece>) {
        if (path.size < 2) return
        reservePathWithPadding(path, blocked)
        arrows += ArrowPiece(
            id = arrows.size,
            start = path.first(),
            direction = directionAtTip(path),
            tailFactor = ((path.size - 1).coerceIn(1, 9) - 1) / 8f,
            bend = bendType(path),
            path = path
        )
    }

    private fun turnLeft(direction: Direction): Direction = when (direction) {
        Direction.UP -> Direction.LEFT
        Direction.LEFT -> Direction.DOWN
        Direction.DOWN -> Direction.RIGHT
        Direction.RIGHT -> Direction.UP
    }

    private fun turnRight(direction: Direction): Direction = when (direction) {
        Direction.UP -> Direction.RIGHT
        Direction.RIGHT -> Direction.DOWN
        Direction.DOWN -> Direction.LEFT
        Direction.LEFT -> Direction.UP
    }
}
