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
            if (path.size < 2) continue

            reservePathWithPadding(path, blocked)
            arrows += ArrowPiece(
                id = arrows.size,
                start = start,
                direction = directionAtTip(path),
                tailFactor = ((path.size - 1).coerceIn(2, 7) - 2) / 5f,
                bend = bendType(path),
                path = path
            )
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
        val targetRange = when {
            distanceNorm < 0.38f -> 5..7
            distanceNorm < 0.72f -> 3..5
            else -> 2..3
        }

        val primary = outwardDirection(start, centerX, centerY)
        val candidateDirs = listOf(primary) + Direction.entries.filter { it != primary }.shuffled(random)
        val bendCandidates = listOf(ArrowBend.STRAIGHT, ArrowBend.LEFT_90, ArrowBend.RIGHT_90).shuffled(random)
        var best = emptyList<Cell>()

        for (targetLen in targetRange.last downTo targetRange.first) {
            for (firstDir in candidateDirs) {
                for (bend in bendCandidates) {
                    val bendStep = if (bend == ArrowBend.STRAIGHT) Int.MAX_VALUE else (targetLen / 2).coerceAtLeast(1)
                    val candidate = growPath(
                        start = start,
                        firstDir = firstDir,
                        bend = bend,
                        bendStep = bendStep,
                        targetLength = targetLen,
                        width = width,
                        height = height,
                        blocked = blocked
                    )
                    if (candidate.size > best.size) best = candidate
                    if (best.size >= targetLen) return best
                }
            }
        }
        return best
    }

    private fun growPath(
        start: Cell,
        firstDir: Direction,
        bend: ArrowBend,
        bendStep: Int,
        targetLength: Int,
        width: Int,
        height: Int,
        blocked: Set<Cell>
    ): List<Cell> {
        val path = mutableListOf(start)
        val local = mutableSetOf(start)
        var current = start
        var dir = firstDir

        for (step in 1 until targetLength) {
            if (step == bendStep) {
                dir = when (bend) {
                    ArrowBend.STRAIGHT -> dir
                    ArrowBend.LEFT_90 -> turnLeft(dir)
                    ArrowBend.RIGHT_90 -> turnRight(dir)
                }
            }

            val next = Cell(current.x + dir.dx, current.y + dir.dy)
            if (next.x !in 0 until width || next.y !in 0 until height) break
            if (next in blocked || next in local) break
            path += next
            local += next
            current = next
        }

        return path
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
