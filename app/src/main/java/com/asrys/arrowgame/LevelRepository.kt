package com.asrys.arrowgame

import kotlin.random.Random

enum class Difficulty { EASY, NORMAL, HARD, NIGHTMARE }

object LevelRepository {
    private const val boardSize = 20

    private fun getDifficulty(puzzleNumber: Int, random: Random): Difficulty = when {
        puzzleNumber <= 3 -> Difficulty.EASY
        puzzleNumber > 0 && puzzleNumber % 10 == 0 -> Difficulty.NIGHTMARE
        else -> if (random.nextBoolean()) Difficulty.HARD else Difficulty.NORMAL
    }

    fun generatePuzzle(puzzleNumber: Int, seed: Int = Random.nextInt()): LevelMask {
        val random = Random(seed)
        val difficulty = getDifficulty(puzzleNumber, random)
        val width = when (difficulty) {
            Difficulty.NIGHTMARE -> 24
            Difficulty.HARD -> 22
            else -> 20
        }
        val height = width
        val allCells = generateRandomShape(width, height, random, difficulty)

        val arrows = generateCellBasedArrows(width, height, allCells, random, difficulty)

        return LevelMask(
            id = "puzzle_${puzzleNumber}_${random.nextInt()}",
            width = width,
            height = height,
            activeCells = allCells.toSet(),
            arrows = arrows
        )
    }

    private fun generateRandomShape(width: Int, height: Int, random: Random, difficulty: Difficulty): List<Cell> {
        val shapeType = when (difficulty) {
            Difficulty.EASY   -> random.nextInt(0, 5)
            Difficulty.NORMAL -> random.nextInt(0, 16)
            Difficulty.HARD   -> random.nextInt(5, 16)
            Difficulty.NIGHTMARE -> 9
        }
        val cells = mutableSetOf<Cell>()
        when (shapeType) {
            0, 1 -> {
                val minPct = 0.4
                val maxPct = 0.7
                val targetSize = random.nextInt((width * height * minPct).toInt(), (width * height * maxPct).toInt())
                cells.add(Cell(width / 2, height / 2))
                val queue = mutableListOf(Cell(width / 2, height / 2))
                while (cells.size < targetSize && queue.isNotEmpty()) {
                    val current = queue.random(random)
                    val neighbors = Direction.entries.map { Cell(current.x + it.dx, current.y + it.dy) }
                        .filter { it.x in 2 until width - 2 && it.y in 2 until height - 2 && it !in cells }
                    if (neighbors.isEmpty()) {
                        queue.remove(current)
                    } else {
                        val next = neighbors.random(random)
                        cells.add(next)
                        queue.add(next)
                    }
                }
            }
            2 -> {
                val cx = width / 2
                val cy = height / 2
                val radius = width / 2 - 2
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        if (kotlin.math.abs(x - cx) + kotlin.math.abs(y - cy) <= radius) {
                            cells.add(Cell(x, y))
                        }
                    }
                }
            }
            3 -> {
                val cx = width / 2
                val cy = height / 2
                val thickness = width / 3 - 1
                for (y in 2 until height - 2) {
                    for (x in 2 until width - 2) {
                        if (kotlin.math.abs(x - cx) < thickness || kotlin.math.abs(y - cy) < thickness) {
                            cells.add(Cell(x, y))
                        }
                    }
                }
            }
            4 -> {
                for (y in 2 until height - 2) {
                    for (x in 2 until width - 2) {
                        if (x < 6 || x > width - 7 || y < 6 || y > height - 7) {
                            cells.add(Cell(x, y))
                        }
                    }
                }
            }
            5 -> { // Hollow Circle (Donut)
                val cx = width / 2
                val cy = height / 2
                val outerR = (width / 2) - 2
                val innerR = outerR / 2
                for (x in 2 until width - 2) {
                    for (y in 2 until height - 2) {
                        val dx = x - cx
                        val dy = y - cy
                        val dist = kotlin.math.sqrt((dx * dx + dy * dy).toDouble())
                        if (dist <= outerR && dist >= innerR) {
                            cells.add(Cell(x, y))
                        }
                    }
                }
            }
            6 -> { // Cross / Plus symbol
                val cx = width / 2
                val cy = height / 2
                val thickness = width / 5
                for (x in 2 until width - 2) {
                    for (y in 2 until height - 2) {
                        if (kotlin.math.abs(x - cx) <= thickness || kotlin.math.abs(y - cy) <= thickness) {
                            cells.add(Cell(x, y))
                        }
                    }
                }
            }
            7 -> { // X shape
                val cx = width / 2
                val cy = height / 2
                val thickness = width / 5
                for (x in 2 until width - 2) {
                    for (y in 2 until height - 2) {
                        val dist1 = kotlin.math.abs((x - cx) - (y - cy))
                        val dist2 = kotlin.math.abs((x - cx) + (y - cy))
                        if (dist1 <= thickness || dist2 <= thickness) {
                            cells.add(Cell(x, y))
                        }
                    }
                }
            }
            8 -> { // Hollow Diamond
                val cx = width / 2
                val cy = height / 2
                val outerR = (width / 2) - 2
                val innerR = outerR - 4
                for (x in 2 until width - 2) {
                    for (y in 2 until height - 2) {
                        val dist = kotlin.math.abs(x - cx) + kotlin.math.abs(y - cy)
                        if (dist in innerR..outerR) {
                            cells.add(Cell(x, y))
                        }
                    }
                }
            }
            9 -> { // Pure solid block for HARD / NIGHTMARE
                for (x in 2 until width - 2) {
                    for (y in 2 until height - 2) {
                        cells.add(Cell(x, y))
                    }
                }
            }
            10 -> { // Heart shape
                val cx = width / 2.0
                val cy = height / 2.0
                val scale = (width / 2.0) - 2.0
                for (x in 0 until width) {
                    for (y in 0 until height) {
                        val nx = (x - cx) / scale
                        val ny = (y - cy + scale * 0.2) / scale
                        // Heart curve: (x² + y² - 1)³ - x²*y³ ≤ 0
                        val val_ = (nx * nx + ny * ny - 1.0).let { it * it * it } - nx * nx * ny * ny * ny
                        if (val_ <= 0.04) cells.add(Cell(x, y))
                    }
                }
            }
            11 -> { // Apple shape (circle with a dent at top and stem notch)
                val cx = width / 2.0
                val cy = height / 2.0 + 1.0
                val r = (width / 2.0) - 2.5
                for (x in 1 until width - 1) {
                    for (y in 1 until height - 1) {
                        val dx = x - cx
                        val dy = y - cy
                        val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                        // Base circle, excluding a small bite at the top-center (leaf indent)
                        val inCircle = dist <= r
                        val inTopDent = dy < -r * 0.5 && kotlin.math.abs(dx) < r * 0.25
                        if (inCircle && !inTopDent) cells.add(Cell(x, y))
                    }
                }
                // Add a small stem at the top
                val stemX = width / 2
                for (sy in 1..2) cells.add(Cell(stemX, sy))
            }
            12 -> { // Banana shape — curved arc of cells
                val cx = width / 2.0
                val cy = height / 2.0
                val thickness = width / 6
                for (x in 1 until width - 1) {
                    // Parabolic arc: y = cy - r*cos(t) where t maps x across the board
                    val t = (x - 2.0) / (width - 4.0) * kotlin.math.PI
                    val arcY = cy - (height / 2.5) * kotlin.math.cos(t) + height * 0.1
                    for (y in 1 until height - 1) {
                        if (kotlin.math.abs(y - arcY) <= thickness) {
                            cells.add(Cell(x, y))
                        }
                    }
                }
            }
            13 -> { // 5-pointed Star
                val cx = width / 2.0
                val cy = height / 2.0
                val outerR = (width / 2.0) - 2.0
                val innerR = outerR * 0.4
                val points = 5
                for (x in 1 until width - 1) {
                    for (y in 1 until height - 1) {
                        val dx = x - cx
                        val dy = y - cy
                        val angle = kotlin.math.atan2(dy, dx)
                        val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                        // Interpolate between inner and outer radius based on angle
                        val sectorAngle = (2 * kotlin.math.PI / points)
                        val normalizedAngle = ((angle + kotlin.math.PI * 0.5) % sectorAngle + sectorAngle) % sectorAngle
                        val t = kotlin.math.abs(normalizedAngle / sectorAngle - 0.5) * 2.0  // 0 at tips, 1 at valleys
                        val starRadius = innerR + (outerR - innerR) * (1.0 - t)
                        if (dist <= starRadius + 0.7) cells.add(Cell(x, y))
                    }
                }
            }
            14 -> { // Lightning Bolt
                val cx = width / 2
                val thickness = (width / 5).coerceAtLeast(2)
                // Top-right leaning block
                for (x in cx - thickness until cx + thickness * 2) {
                    for (y in 1 until height / 2) {
                        if (x in 1 until width - 1 && y in 1 until height - 1) {
                            val diagOffset = (y * 0.6).toInt()
                            if (x - diagOffset in (cx - thickness)..(cx + thickness)) {
                                cells.add(Cell(x, y))
                            }
                        }
                    }
                }
                // Bottom-right leaning block
                for (x in cx - thickness * 2 until cx + thickness) {
                    for (y in height / 2 until height - 1) {
                        if (x in 1 until width - 1 && y in 1 until height - 1) {
                            val diagOffset = ((y - height / 2) * 0.6).toInt()
                            if (x + diagOffset in (cx - thickness)..(cx + thickness)) {
                                cells.add(Cell(x, y))
                            }
                        }
                    }
                }
            }
            15 -> { // Arrow pointer (pointing right)
                val cx = width / 2
                val cy = height / 2
                val halfH = height / 3
                // Rectangular shaft
                for (x in 2 until cx) {
                    for (y in cy - halfH / 2 until cy + halfH / 2) {
                        if (y in 1 until height - 1) cells.add(Cell(x, y))
                    }
                }
                // Triangle arrowhead
                for (x in cx until width - 2) {
                    val spread = (width - 2 - x)
                    for (y in cy - spread until cy + spread) {
                        if (y in 1 until height - 1) cells.add(Cell(x, y))
                    }
                }
            }
        }
        return cells.toList()
    }

    private fun generateCellBasedArrows(
        width: Int,
        height: Int,
        cells: List<Cell>,
        random: Random,
        difficulty: Difficulty
    ): List<ArrowPiece> {
        val centerX = (width - 1) / 2f
        val centerY = (height - 1) / 2f
        val blocked = mutableSetOf<Cell>()
        val arrows = mutableListOf<ArrowPiece>()
        val activeShape = cells.toSet()

        val orderedCells = cells.sortedBy { c ->
            val dx = c.x - centerX
            val dy = c.y - centerY
            dx * dx + dy * dy
        }

        for (start in orderedCells) {
            if (start in blocked) continue
            val path = buildPathFromStart(start, width, height, centerX, centerY, blocked, activeShape, random, difficulty)
            addArrowIfValid(path, blocked, arrows)
        }

        // Helper: count placed arrows adjacent to `cell` that share a direction
        fun neighborDirCount(cell: Cell, dir: Direction): Int {
            var count = 0
            for (d in Direction.entries) {
                val neighbor = Cell(cell.x + d.dx, cell.y + d.dy)
                arrows.firstOrNull { it.start == neighbor || it.path.firstOrNull() == neighbor }
                    ?.let { if (it.direction == dir) count++ }
            }
            return count
        }

        // Sort directions for a cell: prefer ones with fewer same-dir neighbors
        fun diverseDirs(cell: Cell): List<Direction> =
            Direction.entries.shuffled(random).sortedBy { neighborDirCount(cell, it) }

        // Second pass: fill remaining tiny gaps with very short arrows.
        for (start in orderedCells.shuffled(random)) {
            if (start in blocked) continue
            val filler = buildFillerPathFromStart(start, width, height, blocked, activeShape, random, difficulty)
            addArrowIfValid(filler, blocked, arrows)
        }

        // Third pass: aggressive edge patching — try directions with fewest same-dir neighbors first.
        for (start in orderedCells.shuffled(random)) {
            if (start in blocked) continue
            for (dir in diverseDirs(start)) {
                val candidate = listOf(start, Cell(start.x + dir.dx, start.y + dir.dy))
                if (candidate.all { it in activeShape && it !in blocked } && isExitClear(candidate, blocked, width, height)) {
                    addArrowIfValid(candidate, blocked, arrows)
                    break
                }
            }
        }

        // Fourth pass: 1x1 micro-arrows with diversity-first direction selection.
        // IMPORTANT: Never place an arrow when no direction has a clear exit lane — doing so
        // creates unsolvable puzzles. Simply skip the cell if no valid direction is found.
        for (start in orderedCells.shuffled(random)) {
            if (start in blocked) continue
            for (dir in diverseDirs(start)) {
                if (isSingleCellExitClear(start, dir, blocked, width, height)) {
                    arrows.add(ArrowPiece(id = arrows.size, start = start, direction = dir, tailFactor = 0.2f, path = listOf(start)))
                    blocked.add(start)
                    break
                }
            }
            // If no direction has a clear exit, we intentionally leave this cell unoccupied.
            // A cell with no valid exit direction cannot have a solvable arrow — placing one
            // here (as the old "last resort" did) guaranteed an unsolvable puzzle.
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
        activeShape: Set<Cell>,
        random: Random,
        difficulty: Difficulty
    ): List<Cell> {
        val distanceNorm = normalizedDistance(start, width, height, centerX, centerY)
        
        // Higher chance to force short arrows near the edges (distanceNorm ~ 1.0) to neatly fill in shape boundaries
        // Short chance: moderate and capped so adjacent edge cells don't ALL become short
        val shortChance = 0.15f + distanceNorm * 0.25f  // 15% center → 40% edge (was 60%)
        val forceShort = random.nextFloat() < shortChance

        val primaryRange = when {
            forceShort -> 2..3
            difficulty == Difficulty.NIGHTMARE -> when {
                distanceNorm < 0.4f -> 9..12
                distanceNorm < 0.7f -> 6..8
                else -> 4..5
            }
            difficulty == Difficulty.HARD -> when {
                distanceNorm < 0.4f -> 8..11
                distanceNorm < 0.7f -> 5..7
                else -> 3..5
            }
            else -> when {
                distanceNorm < 0.30f -> 7..10
                distanceNorm < 0.68f -> 4..7
                distanceNorm < 0.90f -> 2..4
                else -> 1..2
            }
        }
        val allRanges = when (difficulty) {
            Difficulty.NIGHTMARE -> listOf(9..12, 6..8, 4..5, 2..3, 1..2)
            Difficulty.HARD -> listOf(8..11, 5..7, 3..5, 2..3, 1..2)
            else -> listOf(7..10, 4..7, 2..4, 1..2)
        }
        val rangeOrder = buildList {
            add(primaryRange)
            addAll(allRanges.filter { it != primaryRange }.shuffled(random))
        }

        // For edge cells: try ALL directions in random order first.
        // Outward is the last resort, not the default, so adjacent edge cells get variety.
        val outward = outwardDirection(start, centerX, centerY)
        val candidateDirs = when {
            // For inner cells keep the original outward bias to keep shapes coherent
            distanceNorm < 0.5f -> (listOf(outward) + Direction.entries.shuffled(random)).distinct()
            // For edge cells: fully random order so adjacent cells can face any direction
            else -> Direction.entries.shuffled(random)
        }
        var best = emptyList<Cell>()
        var bestScore = Int.MIN_VALUE

        for (targetRange in rangeOrder) {
            for (targetLen in targetRange.last downTo targetRange.first) {
                val turnProfile = turnProfileForLength(targetLen, difficulty)
                for (firstDir in candidateDirs) {
                    repeat(9) {
                        val candidate = growPath(
                            start = start,
                            firstDir = firstDir,
                            targetLength = targetLen,
                            blocked = blocked,
                            activeShape = activeShape,
                            minTurns = turnProfile.first,
                            maxTurns = turnProfile.second,
                            random = random
                        )
                        if (candidate.isNotEmpty() && isExitClear(candidate, blocked, width, height)) {
                            val score = candidate.size * 100 + freeNeighborCount(candidate.lastOrNull(), blocked, activeShape)
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
        activeShape: Set<Cell>,
        random: Random,
        difficulty: Difficulty
    ): List<Cell> {
        var best = emptyList<Cell>()
        var bestScore = Int.MIN_VALUE
        for (firstDir in Direction.entries.shuffled(random)) {
            repeat(7) {
                val candidate = growPath(
                    start = start,
                    firstDir = firstDir,
                    targetLength = if (difficulty == Difficulty.NIGHTMARE || difficulty == Difficulty.HARD) 4 else 3,
                    blocked = blocked,
                    activeShape = activeShape,
                    minTurns = 1,
                    maxTurns = if (difficulty == Difficulty.NIGHTMARE || difficulty == Difficulty.HARD) 3 else 2,
                    random = random,
                    difficulty = difficulty
                )
                if (candidate.isNotEmpty() && isExitClear(candidate, blocked, width, height)) {
                    val score = candidate.size * 100 + freeNeighborCount(candidate.lastOrNull(), blocked, activeShape)
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
        blocked: Set<Cell>,
        activeShape: Set<Cell>,
        minTurns: Int,
        maxTurns: Int,
        random: Random,
        difficulty: Difficulty = Difficulty.NORMAL
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
                next in activeShape &&
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
                val openness = freeNeighborCount(nextCell, blocked + local, activeShape)
                
                // Keep default crowding preference of 2. Higher values drive paths into unresolvable dead-ends, causing massive CPU lag.
                val opennessWeight = 2 
                
                turnBias + (openness * opennessWeight) + random.nextInt(0, 5)
            } ?: break

            if (pickedDir != dir) turnsUsed++
            dir = pickedDir

            path += next
            local += next
            current = next
        }

        return path
    }

    private fun turnProfileForLength(length: Int, difficulty: Difficulty): Pair<Int, Int> {
        if (difficulty == Difficulty.NIGHTMARE) {
            return when {
                length >= 9 -> 6 to 9
                length >= 7 -> 5 to 7
                length >= 4 -> 3 to 5
                else -> 1 to 3
            }
        }
        if (difficulty == Difficulty.HARD) {
            return when {
                length >= 9 -> 5 to 8
                length >= 7 -> 4 to 6
                length >= 4 -> 3 to 5
                else -> 1 to 3
            }
        }
        return when {
            length >= 9 -> 4 to 7   // extra long: heavy zig-zag
            length >= 7 -> 3 to 6   // long: multi-bend
            length >= 4 -> 2 to 4   // mid: visibly bendy
            else -> 1 to 2          // short/tiny: can still kink
        }
    }

    private fun freeNeighborCount(cell: Cell?, blocked: Set<Cell>, activeShape: Set<Cell>): Int {
        if (cell == null) return 0
        var count = 0
        for (dir in Direction.entries) {
            val nx = cell.x + dir.dx
            val ny = cell.y + dir.dy
            val nextCell = Cell(nx, ny)
            if (nextCell in activeShape && nextCell !in blocked) {
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

    private fun isSingleCellExitClear(start: Cell, dir: Direction, blocked: Set<Cell>, width: Int, height: Int): Boolean {
        var x = start.x + dir.dx
        var y = start.y + dir.dy
        // Require at least one step outside the board (i.e. the exit lane must reach the edge).
        // Any blocked cell anywhere along the lane means the arrow can never exit.
        var steps = 0
        while (x in 0 until width && y in 0 until height) {
            if (Cell(x, y) in blocked) return false
            x += dir.dx
            y += dir.dy
            steps++
        }
        // If the arrow's tip is already at the edge (0 steps to exit), allow it —
        // it will slide off immediately. If no path to the edge exists, reject.
        return true
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

    private fun doesPointTowardEachOther(cell: Cell, dir: Direction, arrows: List<ArrowPiece>): Boolean {
        for (arrow in arrows) {
            val tip = arrow.path.last()
            val arrowDir = arrow.direction
            
            if (dir == Direction.LEFT && arrowDir == Direction.RIGHT) {
                if (cell.y == tip.y && tip.x < cell.x) return true
            } else if (dir == Direction.RIGHT && arrowDir == Direction.LEFT) {
                if (cell.y == tip.y && cell.x < tip.x) return true
            } else if (dir == Direction.UP && arrowDir == Direction.DOWN) {
                if (cell.x == tip.x && tip.y < cell.y) return true
            } else if (dir == Direction.DOWN && arrowDir == Direction.UP) {
                if (cell.x == tip.x && cell.y < tip.y) return true
            }
        }
        return false
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
