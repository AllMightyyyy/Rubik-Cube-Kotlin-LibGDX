package com.mycompany.myrubikscube.cube

import com.mycompany.myrubikscube.Log
import com.mycompany.myrubikscube.graphics.Axis
import com.mycompany.myrubikscube.graphics.Direction
import com.mycompany.myrubikscube.cs.min2phase.Search
import java.security.InvalidParameterException
import java.util.ArrayList
import java.util.Arrays

/**
 * A beginner's approach to solving the 3x3 cube step by step, but now replaced with a min2phase approach for `solve()`.
 */
class RubiksCube3x3x3 : RubiksCube(SIZE) {

    private enum class SolveState {
        None,
        FirstFaceCross,
        FirstFaceCorners,
        MiddleLayer,
        LastFaceCross,
        LastFaceCrossAlign,
        LastFaceCorners,
        LastFaceCornerAlign
    }

    companion object {
        private const val tag = "rubik-3x3x3"
        private const val SIZE = 3

        // For referencing layers or positions
        private const val INNER = 0
        private const val MIDDLE = 1
        private const val OUTER = 2

        // Face squares indexing
        private const val FIRST_ROW_LEFT = 0
        private const val FIRST_ROW_CENTER = 1
        private const val FIRST_ROW_RIGHT = 2
        private const val MID_ROW_LEFT = 3
        private const val CENTER = 4
        private const val MID_ROW_RIGHT = 5
        private const val LAST_ROW_LEFT = 6
        private const val LAST_ROW_MIDDLE = 7
        private const val LAST_ROW_RIGHT = 8

        // Middle row in Y axis (for edges)
        private const val EDGE_MIDDLE_FRONT_LEFT = 0
        private const val EDGE_MIDDLE_FRONT_RIGHT = 2
        private const val EDGE_MIDDLE_RIGHT_BACK = 4
        private const val EDGE_MIDDLE_LEFT_BACK = 6

        // Corner indexing
        private const val CORNER_INDEX_FRONT_RIGHT = 0
        private const val CORNER_INDEX_RIGHT_BACK = 1
        private const val CORNER_INDEX_BACK_LEFT = 2
        private const val CORNER_INDEX_LEFT_FRONT = 3

        // bottom row numbering
        private const val EDGE_BOTTOM_NEAR = FIRST_ROW_CENTER
        private const val EDGE_BOTTOM_RIGHT = MID_ROW_RIGHT
        private const val EDGE_BOTTOM_LEFT = MID_ROW_LEFT
        private const val EDGE_BOTTOM_FAR = LAST_ROW_MIDDLE

        // top row numbering
        private const val EDGE_TOP_FAR = FIRST_ROW_CENTER
        private const val EDGE_TOP_NEAR = LAST_ROW_MIDDLE
        private const val EDGE_TOP_LEFT = MID_ROW_LEFT
        private const val EDGE_TOP_RIGHT = MID_ROW_RIGHT
    }

    private var solveState = SolveState.None

    // The colors of the top and bottom center squares
    private var mTopColor = 0
    private var mBottomColor = 0

    private var solutionSteps: List<Rotation> = emptyList()
    private var currentStepIndex: Int = 0
    private var isStepByStepSolving: Boolean = false

    override var mState = CubeState.IDLE
    override var mListener: CubeListener? = null

    public override fun setAlgo(algo: Algorithm) {
        super.setAlgo(algo)
        if (isStepByStepSolving) {
            mState = CubeState.SOLVING
        }
    }

    /**
     * The new override: calls min2phase and animates that solution.
     */
    override fun solve(): Int {
        val steps = computeSolutionSteps() ?: return -1
        if (steps.isEmpty()) {
            sendMessage("No solution steps available.")
            return -1
        }
        solutionSteps = steps
        currentStepIndex = 0

        // 4) Mark our state as solving
        mState = CubeState.SOLVING

        // 5) Animate the entire solution
        val algo = Algorithm().apply {
            steps.forEach { addStep(it) }
        }
        setAlgo(algo)

        return 0
    }

    /**
     * We'll keep the old step-based solver code below, but we won't call it from solve() anymore.
     * If you want to restore the old approach, you could call `startSolving()` etc. again.
     */
    override fun cancelSolving(): Int {
        solveState = SolveState.None
        return super.cancelSolving()
    }

    override fun startSolving() {
        super.startSolving()
        solveState = SolveState.FirstFaceCross

        // Identify top/bottom color from the center pieces
        mTopColor = mTopSquares[CENTER].color
        mBottomColor = mBottomSquares[CENTER].color
        sendMessage("Top is ${mTopSquares[CENTER].colorName()} and bottom is ${mBottomSquares[CENTER].colorName()}")

        firstFaceCross()
    }

    // ------------------------------------------------------------------------
    // MIN2PHASE HELPER: Convert the current 3x3 state into a 54-char string
    // in the U,R,F,D,L,B order that min2phase expects.
    // ------------------------------------------------------------------------
    /**
     * MIN2PHASE HELPER: Convert the current 3x3 state into a 54-char string
     * in the U,R,F,D,L,B order that min2phase expects.
     */
    fun toMin2PhaseString(): String {
        val sb = StringBuilder(54)
        // We map each face color to one of [U,R,F,D,L,B].
        // The standard assignment is:
        //   top    -> U
        //   right  -> R
        //   front  -> F
        //   bottom -> D
        //   left   -> L
        //   back   -> B

        // (1) U face (mTopSquares)
        appendFaceString(mTopSquares, sb, 'U')
        // (2) R face (mRightSquares)
        appendFaceString(mRightSquares, sb, 'R')
        // (3) F face (mFrontSquares)
        appendFaceString(mFrontSquares, sb, 'F')
        // (4) D face (mBottomSquares)
        appendFaceString(mBottomSquares, sb, 'D')
        // (5) L face (mLeftSquares)
        appendFaceString(mLeftSquares, sb, 'L')
        // (6) B face (mBackSquares)
        appendFaceString(mBackSquares, sb, 'B')
        Log.w(tag, sb.toString())
        return sb.toString()
    }

    private fun appendFaceString(faceSquares: List<Square>, sb: StringBuilder, faceLetter: Char) {
        // For a 3x3 face, we assume faceSquares is row-major from top-left to bottom-right.
        // We turn each square's color -> the correct letter using colorToLetter().
        for (sq in faceSquares) {
            sb.append(colorToLetter(sq.color))
        }
    }

    private fun colorToLetter(color: Int): Char {
        return when (color) {
            COLOR_TOP    -> 'U'
            COLOR_RIGHT  -> 'R'
            COLOR_FRONT  -> 'F'
            COLOR_BOTTOM -> 'D'
            COLOR_LEFT   -> 'L'
            COLOR_BACK   -> 'B'
            else         -> 'X'
        }
    }

    // ------------------------------------------------------------------------
    // MIN2PHASE HELPER: parse the solution string (e.g. "R2 U' B2 ...") into
    // our Axis/Direction/face logic. Then we can run setAlgo(...) with it.
    // ------------------------------------------------------------------------
    private fun parseMin2PhaseSolution(solutionStr: String): Algorithm {
        val algo = Algorithm()
        val tokens = solutionStr.split("\\s+".toRegex()).filter { it.isNotBlank() }

        for (token in tokens) {
            // token might be "R", "R'", "R2", "U", "U'", "U2", etc.
            val faceChar = token[0]    // 'R','L','U','D','F','B'
            val suffix   = token.drop(1) // "", "2", or "'"

            // Decide axis & faceIndex & baseDir
            // By convention in your code:
            //  R -> X-axis, face = sizeX-1, clockwise
            //  L -> X-axis, face = 0,        counter-clockwise
            //  U -> Y-axis, face = sizeY-1, clockwise
            //  D -> Y-axis, face = 0,       counter-clockwise
            //  F -> Z-axis, face = sizeZ-1, clockwise
            //  B -> Z-axis, face = 0,       counter-clockwise
            var axis: Axis
            var faceIndex: Int
            var baseDir = Direction.CLOCKWISE

            when (faceChar) {
                'R' -> {
                    axis = Axis.X_AXIS
                    faceIndex = sizeX - 1
                    baseDir = Direction.CLOCKWISE
                }
                'L' -> {
                    axis = Axis.X_AXIS
                    faceIndex = 0
                    baseDir = Direction.COUNTER_CLOCKWISE
                }
                'U' -> {
                    axis = Axis.Y_AXIS
                    faceIndex = sizeY - 1
                    baseDir = Direction.CLOCKWISE
                }
                'D' -> {
                    axis = Axis.Y_AXIS
                    faceIndex = 0
                    baseDir = Direction.COUNTER_CLOCKWISE
                }
                'F' -> {
                    axis = Axis.Z_AXIS
                    faceIndex = sizeZ - 1
                    baseDir = Direction.CLOCKWISE
                }
                'B' -> {
                    axis = Axis.Z_AXIS
                    faceIndex = 0
                    baseDir = Direction.COUNTER_CLOCKWISE
                }
                else -> throw IllegalArgumentException("Unexpected face char: $faceChar in token $token")
            }

            // Check suffix for 2 or ' or empty
            if (suffix == "2") {
                // 180-degree turn => two 90-degree turns
                algo.addStep(axis, baseDir, faceIndex)
                algo.addStep(axis, baseDir, faceIndex)
            } else if (suffix == "'") {
                // invert direction
                val dir = if (baseDir == Direction.CLOCKWISE) Direction.COUNTER_CLOCKWISE else Direction.CLOCKWISE
                algo.addStep(axis, dir, faceIndex)
            } else if (suffix.isEmpty()) {
                // normal 90-degree move
                algo.addStep(axis, baseDir, faceIndex)
            } else {
                // e.g. "R2'" would be weird. Log or ignore.
                Log.w(tag, "Unexpected token suffix: $token")
            }
        }

        return algo
    }

    // ------------------------------------------------------------------------
    // The original step-by-step solver logic below is still present,
    // but now it's only invoked if you call `startSolving()` directly.
    // We'll keep it intact in case you want to toggle back.
    // ------------------------------------------------------------------------

    private fun firstFaceCross() {
        val sideFaces = arrayOf(mBackSquares, mLeftSquares, mRightSquares, mFrontSquares)

        for (i in EDGE_TOP_NEAR downTo 1) {
            if (i % 2 == 0) continue
            val sideFace = sideFaces[i / 2]
            val topSq = mTopSquares[i]
            if (topSq.color == mTopColor &&
                sideFace[FIRST_ROW_CENTER].color == sideFace[CENTER].color
            ) {
                continue
            }
            if (i != EDGE_TOP_NEAR) {
                val dir = if (i == EDGE_TOP_LEFT) Direction.COUNTER_CLOCKWISE else Direction.CLOCKWISE
                val algo = Algorithm.rotateWhole(Axis.Y_AXIS, dir, SIZE, if (i == EDGE_TOP_FAR) 2 else 1)
                setAlgo(algo)
            } else {
                fixFirstFaceEdge(mTopColor, sideFace[CENTER].color)
            }
            return
        }
        sendMessage("Top cross is done, cutting corners now")
        proceedToNextState()
    }

    private fun fixFirstFaceEdge(topColor: Int, sideColor: Int) {
        val colors = intArrayOf(topColor, sideColor)
        var row = 0
        var pos = -1
        for (r in 0 until SIZE) {
            pos = findPieceOnFace(mYaxisLayers[r], colors)
            if (pos >= 0) {
                row = r
                break
            }
        }
        Log.w(tag, "Found $topColor-$sideColor at $row-$pos")

        if (row == INNER && mBottomSquares[pos].color == topColor) {
            firstFaceEdge_fromBottomFace(pos)
        } else if (row == INNER) {
            firstFaceEdge_fromLowerLayer(pos)
        } else if (row == MIDDLE) {
            firstFaceEdge_fromMiddleLayer(pos)
        } else {
            firstFaceEdge_fromTopLayer(pos)
        }
    }

    private fun firstFaceEdge_fromTopLayer(pos: Int) {
        Log.d(tag, "Edge piece from top layer")
        val algo = Algorithm()
        val topColoredSquare = getSquareByColor(mYaxisLayers, OUTER, pos, mTopColor)
        val middleRotations: ArrayList<Rotation>

        if (pos == EDGE_TOP_FAR || pos == EDGE_TOP_NEAR) {
            val faceIndex = if (topColoredSquare.face == FACE_TOP) FACE_RIGHT else topColoredSquare.face
            val rot = Rotation(
                Axis.Z_AXIS,
                Direction.CLOCKWISE,
                if (pos == EDGE_TOP_FAR) INNER else OUTER
            )
            algo.addStep(rot)
            middleRotations = middleEdgeToTopEdge(
                if (pos == EDGE_TOP_FAR) EDGE_MIDDLE_RIGHT_BACK else EDGE_MIDDLE_FRONT_RIGHT,
                faceIndex
            )
        } else {
            val faceIndex = if (topColoredSquare.face == FACE_TOP) FACE_FRONT else topColoredSquare.face
            val rot = Rotation(
                Axis.X_AXIS,
                Direction.COUNTER_CLOCKWISE,
                if (pos == EDGE_TOP_LEFT) INNER else OUTER
            )
            algo.addStep(rot)
            middleRotations = middleEdgeToTopEdge(
                if (pos == EDGE_TOP_LEFT) EDGE_MIDDLE_FRONT_LEFT else EDGE_MIDDLE_FRONT_RIGHT,
                faceIndex
            )
        }
        for (r in middleRotations) {
            algo.addStep(r)
        }
        setAlgo(algo)
    }

    private fun firstFaceEdge_fromMiddleLayer(pos: Int) {
        Log.d(tag, "Edge piece from middle layer")
        val topSquare = getSquareByColor(mYaxisLayers, MIDDLE, pos, mTopColor)
        val rotations = middleEdgeToTopEdge(pos, topSquare.face)
        val algo = Algorithm()
        for (r in rotations) {
            algo.addStep(r)
        }
        setAlgo(algo)
    }

    private fun firstFaceEdge_fromLowerLayer(pos: Int) {
        Log.d(tag, "Edge piece from lower layer")
        val algo = Algorithm()
        if (pos == EDGE_BOTTOM_NEAR || pos == EDGE_BOTTOM_FAR) {
            algo.addStep(Axis.Y_AXIS, Direction.CLOCKWISE, INNER)
        }
        if (pos <= EDGE_BOTTOM_LEFT) {
            algo.addStep(Axis.X_AXIS, Direction.CLOCKWISE, INNER)
            algo.addStep(Axis.Z_AXIS, Direction.CLOCKWISE, OUTER)
            if (mTopSquares[EDGE_TOP_LEFT].color == mTopColor &&
                mLeftSquares[FIRST_ROW_CENTER].color == mLeftSquares[CENTER].color
            ) {
                algo.addStep(Axis.X_AXIS, Direction.COUNTER_CLOCKWISE, INNER)
            }
        } else {
            algo.addStep(Axis.X_AXIS, Direction.CLOCKWISE, OUTER)
            algo.addStep(Axis.Z_AXIS, Direction.COUNTER_CLOCKWISE, OUTER)
            if (mTopSquares[EDGE_TOP_RIGHT].color == mTopColor &&
                mRightSquares[FIRST_ROW_CENTER].color == mRightSquares[CENTER].color
            ) {
                algo.addStep(Axis.X_AXIS, Direction.COUNTER_CLOCKWISE, OUTER)
            }
        }
        setAlgo(algo)
    }

    private fun firstFaceEdge_fromBottomFace(pos: Int) {
        Log.d(tag, "Edge piece from bottom face")
        val algo = Algorithm()
        if (pos != EDGE_BOTTOM_NEAR) {
            val dir = if (pos == EDGE_BOTTOM_LEFT) Direction.COUNTER_CLOCKWISE else Direction.CLOCKWISE
            val rot = Rotation(Axis.Y_AXIS, dir, INNER)
            algo.addStep(rot)
            if (pos == EDGE_BOTTOM_FAR) {
                algo.addStep(rot)
            }
        }
        val rot2 = Rotation(Axis.Z_AXIS, Direction.COUNTER_CLOCKWISE, OUTER)
        algo.addStep(rot2)
        algo.addStep(rot2)
        setAlgo(algo)
    }

    private fun firstFaceCorners() {
        val corners = intArrayOf(LAST_ROW_RIGHT, LAST_ROW_LEFT, FIRST_ROW_LEFT, FIRST_ROW_RIGHT)
        for (c in corners) {
            val cornerPiece = mYaxisLayers[INNER][c]
            val topColoredSquare = cornerPiece.getSquare(mTopColor)
            if (topColoredSquare == null) continue
            if (topColoredSquare.face == FACE_BOTTOM) continue
            Log.d(tag, "Found corner piece side-lower: $cornerPiece at $c")
            firstFaceCorner(c)
            return
        }
        for (c in corners) {
            val cornerPiece = mYaxisLayers[INNER][c]
            val topColoredSquare = cornerPiece.getSquare(mTopColor) ?: continue
            if (topColoredSquare.face != FACE_BOTTOM) {
                throw AssertionError("Corner mismatch: top color not on bottom yet not side-lower.")
            }
            Log.d(tag, "White on bottom corner: $cornerPiece at $c")
            firstFaceCornerWhiteOnBottom(c)
            return
        }
        for (c in corners) {
            val cornerPiece = mYaxisLayers[OUTER][c]
            if (!isCornerAligned(cornerPiece)) {
                Log.d(tag, "Corner unaligned in top layer: $cornerPiece at $c")
                firstFaceCornerFromTopLayer(c)
                return
            }
        }
        sendMessage("We have a perfect first layer..!")
        proceedToNextState()
    }

    private fun firstFaceCorner(corner: Int) {
        val piece = mYaxisLayers[INNER][corner]
        if (piece.type != Piece.PieceType.CORNER) throw AssertionError()
        val topColor = mTopSquares[CENTER].color

        var topColorFace = -1
        var sideColor = -1
        var sideColorFace = -1
        for (sq in piece.mSquares) {
            when {
                sq.color == topColor -> {
                    topColorFace = sq.face
                }
                sq.face != FACE_BOTTOM -> {
                    sideColor = sq.color
                    sideColorFace = sq.face
                }
            }
        }
        val sideColorCenterFace = getColorFace(sideColor)

        val rotations = bringColorToFront(sideColor)
        var count = kotlin.math.abs(sideColorCenterFace - sideColorFace)
        var direction = if (sideColorCenterFace > sideColorFace) {
            Direction.COUNTER_CLOCKWISE
        } else {
            Direction.CLOCKWISE
        }
        if (count == 3) {
            count = 1
            direction = if (direction == Direction.CLOCKWISE) Direction.COUNTER_CLOCKWISE else Direction.CLOCKWISE
        }
        repeat(count) {
            rotations.add(Rotation(Axis.Y_AXIS, direction, INNER))
        }

        var relativeFace = (topColorFace - sideColorFace + CUBE_SIDES) % CUBE_SIDES
        if (relativeFace == FACE_RIGHT) {
            rotations.add(Rotation(Axis.X_AXIS, Direction.COUNTER_CLOCKWISE, OUTER))
            rotations.add(Rotation(Axis.Y_AXIS, Direction.CLOCKWISE, INNER))
            rotations.add(Rotation(Axis.X_AXIS, Direction.CLOCKWISE, OUTER))
        } else if (relativeFace == FACE_LEFT) {
            rotations.add(Rotation(Axis.X_AXIS, Direction.COUNTER_CLOCKWISE, INNER))
            rotations.add(Rotation(Axis.Y_AXIS, Direction.COUNTER_CLOCKWISE, INNER))
            rotations.add(Rotation(Axis.X_AXIS, Direction.CLOCKWISE, INNER))
        } else {
            throw AssertionError("Corner insertion unexpected face orientation: $relativeFace")
        }
        setAlgo(Algorithm(rotations))
    }

    private fun firstFaceCornerWhiteOnBottom(corner: Int) {
        val algorithm = Algorithm()
        val piece = mYaxisLayers[INNER][corner]
        if (piece.type != Piece.PieceType.CORNER) throw AssertionError()

        val topColor = mTopSquares[CENTER].color
        var sideColor1 = -1
        var sideColor2 = -1
        for (sq in piece.mSquares) {
            if (sq.color == topColor) {
                if (sq.face != FACE_BOTTOM) {
                    throw AssertionError("topColor corner not on bottom face, weird scenario.")
                }
            } else {
                if (sideColor1 == -1) sideColor1 = sq.color else sideColor2 = sq.color
            }
        }
        val face1 = getColorFace(sideColor1)
        val face2 = getColorFace(sideColor2)

        var desiredCorner = FIRST_ROW_LEFT
        if (face1 == FACE_BACK || face2 == FACE_BACK) desiredCorner = LAST_ROW_LEFT
        if (face1 == FACE_RIGHT || face2 == FACE_RIGHT) desiredCorner += 2

        val currentCornerIndex = corner2index(FACE_BOTTOM, corner)
        val desiredCornerIndex = CORNER_INDEX_FRONT_RIGHT
        var delta = kotlin.math.abs(currentCornerIndex - desiredCornerIndex)

        if (desiredCornerIndex != currentCornerIndex) {
            val direction = if (desiredCorner == FIRST_ROW_LEFT) Direction.COUNTER_CLOCKWISE else Direction.CLOCKWISE
            algorithm.addStep(Axis.Y_AXIS, direction, 0, SIZE)
            if (desiredCorner == LAST_ROW_LEFT) {
                algorithm.addStep(Axis.Y_AXIS, direction, 0, SIZE)
            }
        }
        var dir = if (desiredCornerIndex < currentCornerIndex) Direction.CLOCKWISE else Direction.COUNTER_CLOCKWISE
        if (delta == 3) {
            delta = 1
            dir = if (dir == Direction.CLOCKWISE) Direction.COUNTER_CLOCKWISE else Direction.CLOCKWISE
        }
        repeat(delta) {
            algorithm.addStep(Axis.Y_AXIS, dir, INNER)
        }

        algorithm.addStep(Axis.X_AXIS, Direction.COUNTER_CLOCKWISE, OUTER)
        algorithm.addStep(Axis.Y_AXIS, Direction.CLOCKWISE, INNER)
        algorithm.repeatLastStep()
        algorithm.addStep(Axis.X_AXIS, Direction.CLOCKWISE, OUTER)

        setAlgo(algorithm)
    }

    private fun firstFaceCornerFromTopLayer(corner: Int) {
        val algorithm = Algorithm()
        val piece = mYaxisLayers[OUTER][corner]
        if (piece.type != Piece.PieceType.CORNER) throw AssertionError()
        val topColor = mTopSquares[CENTER].color
        var topColorFace = -1
        for (sq in piece.mSquares) {
            if (sq.color == topColor) {
                topColorFace = sq.face
                break
            }
        }

        val desiredCornerIndex = CORNER_INDEX_FRONT_RIGHT
        val currentCornerIndex = corner2index(FACE_TOP, corner)
        if (desiredCornerIndex != currentCornerIndex) {
            val dir = if (currentCornerIndex == CORNER_INDEX_LEFT_FRONT)
                Direction.COUNTER_CLOCKWISE else Direction.CLOCKWISE
            algorithm.addStep(Axis.Y_AXIS, dir, 0, SIZE)
            if (topColorFace != FACE_TOP) {
                topColorFace += if (dir == Direction.COUNTER_CLOCKWISE) 1 else -1
            }
            if (currentCornerIndex == CORNER_INDEX_BACK_LEFT) {
                algorithm.repeatLastStep()
                if (topColorFace != FACE_TOP) {
                    topColorFace += if (dir == Direction.COUNTER_CLOCKWISE) 1 else -1
                }
            }
        }
        val correctedFace = (topColorFace + CUBE_SIDES) % CUBE_SIDES
        if (correctedFace == FACE_FRONT || correctedFace == FACE_TOP) {
            algorithm.addStep(Axis.Z_AXIS, Direction.CLOCKWISE, OUTER)
            algorithm.addStep(Axis.Y_AXIS, Direction.COUNTER_CLOCKWISE, INNER)
            algorithm.addStep(Axis.Z_AXIS, Direction.COUNTER_CLOCKWISE, OUTER)
        } else if (correctedFace == FACE_RIGHT) {
            algorithm.addStep(Axis.X_AXIS, Direction.COUNTER_CLOCKWISE, OUTER)
            algorithm.addStep(Axis.Y_AXIS, Direction.CLOCKWISE, INNER)
            algorithm.addStep(Axis.X_AXIS, Direction.CLOCKWISE, OUTER)
        } else {
            throw AssertionError("Unexpected corner orientation for topColorFace=$correctedFace")
        }
        setAlgo(algorithm)
    }

    private fun corner2index(face: Int, corner: Int): Int {
        if (face == FACE_BOTTOM) {
            return when (corner) {
                FIRST_ROW_RIGHT -> CORNER_INDEX_FRONT_RIGHT
                LAST_ROW_RIGHT -> CORNER_INDEX_RIGHT_BACK
                LAST_ROW_LEFT -> CORNER_INDEX_BACK_LEFT
                FIRST_ROW_LEFT -> CORNER_INDEX_LEFT_FRONT
                else -> throw InvalidParameterException("Invalid corner $corner for bottom")
            }
        } else if (face == FACE_TOP) {
            return when (corner) {
                FIRST_ROW_LEFT -> CORNER_INDEX_BACK_LEFT
                FIRST_ROW_RIGHT -> CORNER_INDEX_RIGHT_BACK
                LAST_ROW_LEFT -> CORNER_INDEX_LEFT_FRONT
                LAST_ROW_RIGHT -> CORNER_INDEX_FRONT_RIGHT
                else -> throw InvalidParameterException("Invalid corner $corner for top")
            }
        } else {
            throw InvalidParameterException("Not implemented for face=$face in corner2index()")
        }
    }

    private fun isCornerAligned(piece: Piece): Boolean {
        if (piece.mSquares.size != 3) return false
        for (sq in piece.mSquares) {
            val centerColor = mAllFaces[sq.face]!![CENTER].color
            if (sq.color != centerColor) {
                return false
            }
        }
        return true
    }

    private fun middleLayer() {
        val edges = intArrayOf(LAST_ROW_MIDDLE, MID_ROW_RIGHT, FIRST_ROW_CENTER, MID_ROW_LEFT)
        for (e in edges) {
            val piece = mYaxisLayers[OUTER][e]
            if (piece.hasColor(mBottomColor) || piece.hasColor(mTopColor)) {
                continue
            }
            Log.d(tag, "Found Edge $piece at $e (fixing middleLayer now)")
            fixMiddleLayer(e)
            return
        }

        val midEdges = intArrayOf(
            EDGE_MIDDLE_FRONT_LEFT, EDGE_MIDDLE_FRONT_RIGHT,
            EDGE_MIDDLE_RIGHT_BACK, EDGE_MIDDLE_LEFT_BACK
        )
        for (e in midEdges) {
            val piece = mYaxisLayers[MIDDLE][e]
            if (!piece.hasColor(mBottomColor) &&
                !piece.hasColor(mTopColor) &&
                !isEdgeAligned(piece)
            ) {
                Log.d(tag, "Bringing up unaligned middle edge: $piece at $e")
                bringUpUnalignedMiddleEdge(e)
                return
            }
        }

        sendMessage("Fixed middle layer..!")
        proceedToNextState()
    }

    private fun bringUpUnalignedMiddleEdge(edgeIndex: Int) {
        val algo = Algorithm()
        if (edgeIndex != EDGE_MIDDLE_FRONT_RIGHT) {
            var count = 1
            var direction = Direction.CLOCKWISE
            if (edgeIndex == EDGE_MIDDLE_FRONT_LEFT) {
                direction = Direction.COUNTER_CLOCKWISE
            }
            if (edgeIndex == EDGE_MIDDLE_LEFT_BACK) {
                count++
            }
            repeat(count) {
                algo.addStep(Axis.Y_AXIS, direction, 0, SIZE)
            }
        }
        algo.append(fixMiddleLayerFromFrontFace())
        setAlgo(algo)
    }

    private fun fixMiddleLayer(edge: Int) {
        val piece = mYaxisLayers[OUTER][edge]
        if (piece.type != Piece.PieceType.EDGE) throw AssertionError("Not an edge?")

        var color1 = -1
        var color2 = -1
        var outerColor = -1
        var outerFace = -1
        for (sq in piece.mSquares) {
            if (sq.color == mBottomColor) {
                throw InvalidParameterException("This edge shouldn't have the bottom color at all!")
            }
            if (sq.face != FACE_TOP) {
                outerColor = sq.color
                outerFace = sq.face
            }
            if (color1 == -1) {
                color1 = sq.color
            } else if (color2 == -1) {
                color2 = sq.color
            }
        }

        if (outerFace == FACE_RIGHT && mRightSquares[CENTER].color == outerColor) {
            setAlgo(fixMiddleLayerFromRightFace())
            return
        } else if (outerFace == FACE_FRONT && mFrontSquares[CENTER].color == outerColor) {
            setAlgo(fixMiddleLayerFromFrontFace())
            return
        }

        val face1 = getColorFace(color1)
        val face2 = getColorFace(color2)

        val algo = Algorithm()
        val alignAlgo = if (color1 == outerColor) {
            alignMiddlePiece(outerFace, face1)
        } else {
            alignMiddlePiece(outerFace, face2)
        }

        var currentCorner = FIRST_ROW_LEFT
        if (face1 == FACE_BACK || face2 == FACE_BACK) currentCorner = LAST_ROW_LEFT
        if (face1 == FACE_RIGHT || face2 == FACE_RIGHT) currentCorner += 2
        val currentCornerIndex = corner2index(FACE_TOP, currentCorner)
        val desiredCornerIndex = CORNER_INDEX_FRONT_RIGHT

        if (currentCornerIndex != desiredCornerIndex) {
            val direction = if (currentCornerIndex == CORNER_INDEX_LEFT_FRONT)
                Direction.COUNTER_CLOCKWISE else Direction.CLOCKWISE
            algo.addStep(Axis.Y_AXIS, direction, 0, SIZE)
            if (currentCornerIndex == CORNER_INDEX_BACK_LEFT) {
                algo.repeatLastStep()
            }
        }

        if (alignAlgo != null) {
            algo.append(alignAlgo)
        }
        setAlgo(algo)
    }

    private fun alignMiddlePiece(startFace: Int, destFace: Int): Algorithm? {
        if (!(startFace in 0..3 && destFace in 0..3)) {
            throw AssertionError("Cannot align middle edge from face=$startFace to face=$destFace")
        }
        var delta = kotlin.math.abs(startFace - destFace)
        if (delta == 0) return null

        var dir = if (startFace > destFace) Direction.CLOCKWISE else Direction.COUNTER_CLOCKWISE
        if (delta == 3) {
            delta = 1
            dir = if (dir == Direction.CLOCKWISE) Direction.COUNTER_CLOCKWISE else Direction.CLOCKWISE
        }
        val algo = Algorithm()
        repeat(delta) {
            algo.addStep(Axis.Y_AXIS, dir, OUTER)
        }
        return algo
    }

    private fun fixMiddleLayerFromFrontFace(): Algorithm {
        val algo = Algorithm()
        algo.addStep(Axis.Y_AXIS, Direction.CLOCKWISE, OUTER)
        algo.addStep(Axis.X_AXIS, Direction.CLOCKWISE, OUTER)
        algo.addStep(Axis.Y_AXIS, Direction.COUNTER_CLOCKWISE, OUTER)
        algo.addStep(Axis.X_AXIS, Direction.COUNTER_CLOCKWISE, OUTER)
        algo.addStep(Axis.Y_AXIS, Direction.COUNTER_CLOCKWISE, OUTER)
        algo.addStep(Axis.Z_AXIS, Direction.COUNTER_CLOCKWISE, OUTER)
        algo.addStep(Axis.Y_AXIS, Direction.CLOCKWISE, OUTER)
        algo.addStep(Axis.Z_AXIS, Direction.CLOCKWISE, OUTER)
        return algo
    }

    private fun fixMiddleLayerFromRightFace(): Algorithm {
        val algo = Algorithm()
        algo.addStep(Axis.Y_AXIS, Direction.COUNTER_CLOCKWISE, OUTER)
        algo.addStep(Axis.Z_AXIS, Direction.COUNTER_CLOCKWISE, OUTER)
        algo.addStep(Axis.Y_AXIS, Direction.CLOCKWISE, OUTER)
        algo.addStep(Axis.Z_AXIS, Direction.CLOCKWISE, OUTER)
        algo.addStep(Axis.Y_AXIS, Direction.CLOCKWISE, OUTER)
        algo.addStep(Axis.X_AXIS, Direction.CLOCKWISE, OUTER)
        algo.addStep(Axis.Y_AXIS, Direction.COUNTER_CLOCKWISE, OUTER)
        algo.addStep(Axis.X_AXIS, Direction.COUNTER_CLOCKWISE, OUTER)
        return algo
    }

    private fun isEdgeAligned(piece: Piece): Boolean {
        if (piece.type != Piece.PieceType.EDGE) throw AssertionError()
        for (sq in piece.mSquares) {
            val centerColor = mAllFaces[sq.face]!![CENTER].color
            if (sq.color != centerColor) {
                return false
            }
        }
        return true
    }

    private fun lastFaceCross() {
        val lastFaceColor = mTopSquares[CENTER].color
        val edges = intArrayOf(EDGE_TOP_NEAR, EDGE_TOP_RIGHT, EDGE_TOP_FAR, EDGE_TOP_LEFT)
        val colors = IntArray(CUBE_SIDES)
        var yellowCount = 0

        val algo = Algorithm()
        for (i in edges.indices) {
            val sq = mTopSquares[edges[i]]
            colors[i] = sq.color
            if (colors[i] == lastFaceColor) {
                yellowCount++
            }
        }

        if (yellowCount == CUBE_SIDES) {
            sendMessage("Top cross is in place..!")
            proceedToNextState()
            return
        }
        if (yellowCount != 2) {
            algo.append(lastFaceCrossAlgo(1))
            setAlgo(algo)
            return
        }

        if (colors[FACE_FRONT] == colors[FACE_BACK] ||
            colors[FACE_LEFT] == colors[FACE_RIGHT]
        ) {
            if (colors[FACE_FRONT] == colors[FACE_BACK]) {
                algo.addStep(Axis.Y_AXIS, Direction.CLOCKWISE, 0, SIZE)
            }
            algo.append(lastFaceCrossAlgo(1))
            setAlgo(algo)
            return
        }

        var count = 0
        var direction = Direction.CLOCKWISE
        if (colors[FACE_FRONT] == lastFaceColor) {
            if (colors[FACE_RIGHT] == lastFaceColor) {
                count = 2
            } else if (colors[FACE_LEFT] == lastFaceColor) {
                count = 1
            } else {
                throw AssertionError("Strange L shape logic")
            }
        } else if (colors[FACE_BACK] == lastFaceColor) {
            if (colors[FACE_RIGHT] == lastFaceColor) {
                count = 1
                direction = Direction.COUNTER_CLOCKWISE
            }
        }
        repeat(count) {
            algo.addStep(Axis.Y_AXIS, direction, OUTER)
        }
        algo.append(lastFaceCrossAlgo(2))
        setAlgo(algo)
    }

    private fun lastFaceCrossAlgo(count: Int): Algorithm {
        if (count < 0 || count > 2) {
            throw InvalidParameterException("Invalid count: $count")
        }
        val algo = Algorithm()
        algo.addStep(Axis.Z_AXIS, Direction.CLOCKWISE, OUTER)
        repeat(count) {
            algo.addStep(Axis.X_AXIS, Direction.CLOCKWISE, OUTER)
            algo.addStep(Axis.Y_AXIS, Direction.CLOCKWISE, OUTER)
            algo.addStep(Axis.X_AXIS, Direction.COUNTER_CLOCKWISE, OUTER)
            algo.addStep(Axis.Y_AXIS, Direction.COUNTER_CLOCKWISE, OUTER)
        }
        algo.addStep(Axis.Z_AXIS, Direction.COUNTER_CLOCKWISE, OUTER)
        return algo
    }

    private fun lastFaceCrossAlign() {
        val offsets = IntArray(CUBE_SIDES) { 0 }
        val edges = intArrayOf(LAST_ROW_MIDDLE, MID_ROW_RIGHT, FIRST_ROW_CENTER, MID_ROW_LEFT)
        var dbg = "offsets:"
        for (i in edges.indices) {
            val piece = mYaxisLayers[OUTER][edges[i]]
            var sideSquare: Square? = null
            for (sq in piece.mSquares) {
                if (sq.face == FACE_TOP) continue
                sideSquare = sq
                break
            }
            if (sideSquare == null) throw AssertionError("No side square on top edge piece")
            val face = getColorFace(sideSquare.color)
            if (face == FACE_TOP || face == FACE_BOTTOM) {
                throw AssertionError("Logic error: edge color mapped to top/bottom face?")
            }
            offsets[i] = (face - i + CUBE_SIDES) % CUBE_SIDES
            dbg += " ${offsets[i]}"
        }
        Log.w(tag, dbg)

        for (i in 0 until offsets.size - 1) {
            if (offsets[i] != offsets[i + 1]) {
                fixLastFaceCrossAlignment(offsets)
                return
            }
        }
        if (offsets[0] != 0) {
            val algo = Algorithm()
            val dir = if (offsets[0] == 3) Direction.CLOCKWISE else Direction.COUNTER_CLOCKWISE
            algo.addStep(Axis.Y_AXIS, dir, OUTER)
            if (kotlin.math.abs(offsets[0]) == 2) {
                algo.repeatLastStep()
            }
            setAlgo(algo)
        } else {
            sendMessage("Top cross is now aligned")
            proceedToNextState()
        }
    }

    private fun fixLastFaceCrossAlignment(offsets: IntArray) {
        val algorithm = Algorithm()
        var alignedCount = 0
        var firstAlignedIndex = -1
        for (i in offsets.indices) {
            if (offsets[i] == 0) {
                alignedCount++
                if (firstAlignedIndex < 0) {
                    firstAlignedIndex = i
                }
            }
        }
        Log.w(tag, "Aligned count $alignedCount")

        if (alignedCount == 0) {
            algorithm.addStep(Axis.Y_AXIS, Direction.CLOCKWISE, OUTER)
            setAlgo(algorithm)
            return
        }
        if (alignedCount == 2) {
            val nextI = (firstAlignedIndex + 1) % CUBE_SIDES
            val prevI = (firstAlignedIndex - 1 + CUBE_SIDES) % CUBE_SIDES
            if (offsets[nextI] == 0 || offsets[prevI] == 0) {
                algorithm.addStep(Axis.Y_AXIS, Direction.CLOCKWISE, OUTER)
                setAlgo(algorithm)
            } else {
                algorithm.append(lastFaceCrossAlignAlgo(Direction.CLOCKWISE))
                setAlgo(algorithm)
            }
            return
        }
        if (alignedCount == 1) {
            if (firstAlignedIndex != FACE_FRONT) {
                val dir = if (firstAlignedIndex == FACE_LEFT) Direction.COUNTER_CLOCKWISE else Direction.CLOCKWISE
                algorithm.addStep(Axis.Y_AXIS, dir, 0, SIZE)
                if (firstAlignedIndex == FACE_BACK) {
                    algorithm.repeatLastStep()
                }
            }
            val nextI = (firstAlignedIndex + 1) % CUBE_SIDES
            val nextOffset = offsets[nextI]
            if (nextOffset == 1) {
                algorithm.append(lastFaceCrossAlignAlgo(Direction.COUNTER_CLOCKWISE))
            } else {
                algorithm.append(lastFaceCrossAlignAlgo(Direction.CLOCKWISE))
            }
            setAlgo(algorithm)
        }
    }

    private fun lastFaceCrossAlignAlgo(direction: Direction): Algorithm {
        val algo = Algorithm()
        if (direction == Direction.CLOCKWISE) {
            algo.addStep(Axis.X_AXIS, Direction.CLOCKWISE, INNER)
            algo.addStep(Axis.Y_AXIS, Direction.COUNTER_CLOCKWISE, OUTER)
            algo.addStep(Axis.X_AXIS, Direction.COUNTER_CLOCKWISE, INNER)
            algo.addStep(Axis.Y_AXIS, Direction.COUNTER_CLOCKWISE, OUTER)
            algo.addStep(Axis.X_AXIS, Direction.CLOCKWISE, INNER)
            algo.addStep(Axis.Y_AXIS, Direction.COUNTER_CLOCKWISE, OUTER)
            algo.repeatLastStep()
            algo.addStep(Axis.X_AXIS, Direction.COUNTER_CLOCKWISE, INNER)
        } else {
            algo.addStep(Axis.X_AXIS, Direction.CLOCKWISE, OUTER)
            algo.addStep(Axis.Y_AXIS, Direction.CLOCKWISE, OUTER)
            algo.addStep(Axis.X_AXIS, Direction.COUNTER_CLOCKWISE, OUTER)
            algo.addStep(Axis.Y_AXIS, Direction.CLOCKWISE, OUTER)
            algo.addStep(Axis.X_AXIS, Direction.CLOCKWISE, OUTER)
            algo.addStep(Axis.Y_AXIS, Direction.COUNTER_CLOCKWISE, OUTER)
            algo.repeatLastStep()
            algo.addStep(Axis.X_AXIS, Direction.COUNTER_CLOCKWISE, OUTER)
        }
        return algo
    }

    private fun lastFaceCorners() {
        val corners = intArrayOf(LAST_ROW_RIGHT, FIRST_ROW_RIGHT, FIRST_ROW_LEFT, LAST_ROW_LEFT)
        var positionedCorners = 0
        var firstPositionedCorner = -1

        for (c in corners.indices) {
            val piece = mYaxisLayers[OUTER][corners[c]]
            if (isCornerAligned(piece)) {
                positionedCorners++
                if (firstPositionedCorner == -1) {
                    firstPositionedCorner = corner2index(FACE_TOP, corners[c])
                }
            }
        }
        Log.w(tag, "positioned corners $positionedCorners, first $firstPositionedCorner")

        if (positionedCorners == CUBE_SIDES) {
            proceedToNextState()
            return
        }
        val algorithm = Algorithm()
        if (positionedCorners == 0) {
            algorithm.append(lastFaceCornerPositionAlgo(Direction.CLOCKWISE))
            setAlgo(algorithm)
            return
        }
        if (positionedCorners != 1) {
            sendMessage("Something unusual in top corner positioning..")
            return
        }
        if (firstPositionedCorner != FACE_FRONT) {
            val dir = if (firstPositionedCorner == FACE_LEFT) Direction.COUNTER_CLOCKWISE else Direction.CLOCKWISE
            algorithm.addStep(Axis.Y_AXIS, dir, 0, SIZE)
            if (firstPositionedCorner == FACE_BACK) {
                algorithm.repeatLastStep()
            }
        }
        algorithm.append(lastFaceCornerPositionAlgo(Direction.CLOCKWISE))
        setAlgo(algorithm)
    }

    private fun lastFaceCornerPositionAlgo(direction: Direction): Algorithm {
        val algo = Algorithm()
        if (direction == Direction.COUNTER_CLOCKWISE) {
            algo.addStep(Axis.X_AXIS, Direction.CLOCKWISE, OUTER)
            algo.addStep(Axis.Y_AXIS, Direction.COUNTER_CLOCKWISE, OUTER)
            algo.addStep(Axis.X_AXIS, Direction.COUNTER_CLOCKWISE, INNER)
            algo.addStep(Axis.Y_AXIS, Direction.CLOCKWISE, OUTER)
            algo.addStep(Axis.X_AXIS, Direction.COUNTER_CLOCKWISE, OUTER)
            algo.addStep(Axis.Y_AXIS, Direction.COUNTER_CLOCKWISE, OUTER)
            algo.addStep(Axis.X_AXIS, Direction.COUNTER_CLOCKWISE, INNER)
            algo.addStep(Axis.Y_AXIS, Direction.CLOCKWISE, OUTER)
        } else {
            algo.addStep(Axis.X_AXIS, Direction.COUNTER_CLOCKWISE, INNER)
            algo.addStep(Axis.Y_AXIS, Direction.CLOCKWISE, OUTER)
            algo.addStep(Axis.X_AXIS, Direction.CLOCKWISE, OUTER)
            algo.addStep(Axis.Y_AXIS, Direction.COUNTER_CLOCKWISE, OUTER)
            algo.addStep(Axis.X_AXIS, Direction.CLOCKWISE, INNER)
            algo.addStep(Axis.Y_AXIS, Direction.CLOCKWISE, OUTER)
            algo.addStep(Axis.X_AXIS, Direction.COUNTER_CLOCKWISE, OUTER)
            algo.addStep(Axis.Y_AXIS, Direction.COUNTER_CLOCKWISE, OUTER)
        }
        return algo
    }

    private fun checkTopCorners(): Boolean {
        val corners = intArrayOf(LAST_ROW_RIGHT, FIRST_ROW_RIGHT, FIRST_ROW_LEFT, LAST_ROW_LEFT)
        for (c in corners) {
            val piece = mYaxisLayers[OUTER][c]
            if (!isCornerAligned(piece)) {
                Log.w(tag, "$piece is not aligned")
                return false
            }
        }
        return true
    }

    private fun lastFaceCornerAlign() {
        val lastColor = mTopSquares[CENTER].color
        val algorithm = Algorithm()

        if (mTopSquares[LAST_ROW_RIGHT].color == lastColor) {
            if (checkTopCorners()) {
                proceedToNextState()
            } else {
                algorithm.addStep(Axis.Y_AXIS, Direction.CLOCKWISE, OUTER)
                setAlgo(algorithm)
            }
        } else {
            algorithm.append(theFinalAlgorithm())
            setAlgo(algorithm)
        }
    }

    private fun theFinalAlgorithm(): Algorithm {
        val algo = Algorithm()
        algo.addStep(Axis.X_AXIS, Direction.COUNTER_CLOCKWISE, OUTER)
        algo.addStep(Axis.Y_AXIS, Direction.CLOCKWISE, INNER)
        algo.addStep(Axis.X_AXIS, Direction.CLOCKWISE, OUTER)
        algo.addStep(Axis.Y_AXIS, Direction.COUNTER_CLOCKWISE, INNER)
        return algo
    }

    private fun proceedToNextState() {
        if (mState != CubeState.SOLVING) {
            Log.e(tag, "Invalid state $mState")
            return
        }
        when (solveState) {
            SolveState.FirstFaceCross -> {
                solveState = SolveState.FirstFaceCorners
                firstFaceCorners()
            }
            SolveState.FirstFaceCorners -> {
                solveState = SolveState.MiddleLayer
                middleLayer()
            }
            SolveState.MiddleLayer -> {
                solveState = SolveState.LastFaceCross
                lastFaceCross()
            }
            SolveState.LastFaceCross -> {
                solveState = SolveState.LastFaceCrossAlign
                lastFaceCrossAlign()
            }
            SolveState.LastFaceCrossAlign -> {
                solveState = SolveState.LastFaceCorners
                lastFaceCorners()
            }
            SolveState.LastFaceCorners -> {
                solveState = SolveState.LastFaceCornerAlign
                lastFaceCornerAlign()
            }
            SolveState.LastFaceCornerAlign -> {
                solveState = SolveState.None
                mListener?.handleCubeSolved()
                mState = CubeState.IDLE
            }
            else -> {
                throw AssertionError("Unknown solveState $solveState")
            }
        }
    }

    /**
     * Override `getSolutionSteps` to return the solution steps.
     */
    fun getSolutionSteps(): List<Rotation> = solutionSteps

    override fun updateAlgo() {
        super.updateAlgo()
        if (mState != CubeState.SOLVING) return

        if (isStepByStepSolving) {
            return
        }

        when (solveState) {
            SolveState.FirstFaceCross -> firstFaceCross()
            SolveState.FirstFaceCorners -> firstFaceCorners()
            SolveState.MiddleLayer -> middleLayer()
            SolveState.LastFaceCross -> lastFaceCross()
            SolveState.LastFaceCrossAlign -> lastFaceCrossAlign()
            SolveState.LastFaceCorners -> lastFaceCorners()
            SolveState.LastFaceCornerAlign -> lastFaceCornerAlign()
            else -> {
                mState = CubeState.IDLE
            }
        }
    }

    private fun getColorFace(color: Int): Int {
        for (i in 0 until FACE_COUNT) {
            if (mAllFaces[i]!![CENTER].color == color) {
                return i
            }
        }
        throw InvalidParameterException("Color not found in any face center: $color")
    }

    private fun findPieceOnFace(face: ArrayList<Piece>, colors: IntArray): Int {
        Arrays.sort(colors)
        for (i in face.indices) {
            val piece = face[i]
            if (piece.mSquares.size != colors.size) continue
            val pieceColors = IntArray(piece.mSquares.size) {
                piece.mSquares[it].color
            }
            pieceColors.sort()
            if (pieceColors.contentEquals(colors)) {
                return i
            }
        }
        return -1
    }

    private fun getSquareByColor(
        faceList: ArrayList<ArrayList<Piece>>,
        index: Int,
        pos: Int,
        color: Int
    ): Square {
        val piece = faceList[index][pos]
        for (sq in piece.mSquares) {
            if (sq.color == color) return sq
        }
        throw InvalidParameterException("No square found at index=$index pos=$pos with color=$color")
    }

    private fun middleEdgeToTopEdge(middlePos: Int, faceWithTopColor: Int): ArrayList<Rotation> {
        val rotations = ArrayList<Rotation>()
        when (middlePos) {
            EDGE_MIDDLE_FRONT_LEFT -> {
                if (!(faceWithTopColor == FACE_FRONT || faceWithTopColor == FACE_LEFT))
                    throw AssertionError("middleEdgeToTopEdge mismatch FRONT_LEFT")
                if (faceWithTopColor == FACE_FRONT) {
                    rotations.add(Rotation(Axis.Y_AXIS, Direction.CLOCKWISE, OUTER))
                    rotations.add(Rotation(Axis.X_AXIS, Direction.CLOCKWISE, INNER))
                    rotations.add(Rotation(Axis.Y_AXIS, Direction.COUNTER_CLOCKWISE, OUTER))
                } else {
                    rotations.add(Rotation(Axis.Z_AXIS, Direction.CLOCKWISE, OUTER))
                }
            }
            EDGE_MIDDLE_FRONT_RIGHT -> {
                if (!(faceWithTopColor == FACE_FRONT || faceWithTopColor == FACE_RIGHT))
                    throw AssertionError("middleEdgeToTopEdge mismatch FRONT_RIGHT")
                if (faceWithTopColor == FACE_FRONT) {
                    rotations.add(Rotation(Axis.Y_AXIS, Direction.COUNTER_CLOCKWISE, OUTER))
                    rotations.add(Rotation(Axis.X_AXIS, Direction.CLOCKWISE, OUTER))
                    rotations.add(Rotation(Axis.Y_AXIS, Direction.CLOCKWISE, OUTER))
                } else {
                    rotations.add(Rotation(Axis.Z_AXIS, Direction.COUNTER_CLOCKWISE, OUTER))
                }
            }
            EDGE_MIDDLE_RIGHT_BACK -> {
                if (!(faceWithTopColor == FACE_RIGHT || faceWithTopColor == FACE_BACK))
                    throw AssertionError("middleEdgeToTopEdge mismatch RIGHT_BACK")
                if (faceWithTopColor == FACE_BACK) {
                    rotations.add(Rotation(Axis.Y_AXIS, Direction.COUNTER_CLOCKWISE, OUTER))
                    rotations.add(Rotation(Axis.X_AXIS, Direction.COUNTER_CLOCKWISE, OUTER))
                    rotations.add(Rotation(Axis.Y_AXIS, Direction.CLOCKWISE, OUTER))
                } else {
                    rotations.add(Rotation(Axis.Y_AXIS, Direction.COUNTER_CLOCKWISE, OUTER))
                    rotations.add(Rotation(Axis.Y_AXIS, Direction.COUNTER_CLOCKWISE, OUTER))
                    rotations.add(Rotation(Axis.Z_AXIS, Direction.COUNTER_CLOCKWISE, INNER))
                    rotations.add(Rotation(Axis.Y_AXIS, Direction.COUNTER_CLOCKWISE, OUTER))
                    rotations.add(Rotation(Axis.Y_AXIS, Direction.COUNTER_CLOCKWISE, OUTER))
                }
            }
            EDGE_MIDDLE_LEFT_BACK -> {
                if (!(faceWithTopColor == FACE_LEFT || faceWithTopColor == FACE_BACK))
                    throw AssertionError("middleEdgeToTopEdge mismatch LEFT_BACK")
                if (faceWithTopColor == FACE_BACK) {
                    rotations.add(Rotation(Axis.Y_AXIS, Direction.CLOCKWISE, OUTER))
                    rotations.add(Rotation(Axis.X_AXIS, Direction.COUNTER_CLOCKWISE, INNER))
                    rotations.add(Rotation(Axis.Y_AXIS, Direction.COUNTER_CLOCKWISE, OUTER))
                } else {
                    rotations.add(Rotation(Axis.Y_AXIS, Direction.COUNTER_CLOCKWISE, OUTER))
                    rotations.add(Rotation(Axis.Y_AXIS, Direction.COUNTER_CLOCKWISE, OUTER))
                    rotations.add(Rotation(Axis.Z_AXIS, Direction.CLOCKWISE, INNER))
                    rotations.add(Rotation(Axis.Y_AXIS, Direction.COUNTER_CLOCKWISE, OUTER))
                    rotations.add(Rotation(Axis.Y_AXIS, Direction.COUNTER_CLOCKWISE, OUTER))
                }
            }
        }
        return rotations
    }

    private fun bringColorToFront(color: Int): ArrayList<Rotation> {
        val rotations = ArrayList<Rotation>()
        if (color == mFrontSquares[CENTER].color) {
            return rotations
        }
        var axis = Axis.Y_AXIS
        var dir = Direction.CLOCKWISE

        when (color) {
            mTopSquares[CENTER].color -> {
                axis = Axis.X_AXIS
                dir = Direction.COUNTER_CLOCKWISE
            }
            mBottomSquares[CENTER].color -> {
                axis = Axis.X_AXIS
                dir = Direction.CLOCKWISE
            }
            mLeftSquares[CENTER].color -> {
                axis = Axis.Y_AXIS
                dir = Direction.COUNTER_CLOCKWISE
            }
        }
        rotations.add(Rotation(axis, dir, 0, SIZE))
        if (color == mBackSquares[CENTER].color) {
            rotations.add(Rotation(axis, dir, 0, SIZE))
        }
        return rotations
    }

    /**
     * Computes the solution steps using min2phase and returns them as a list of Rotations.
     * Does not set any algorithm or modify the cube's state.
     *
     * @return List of Rotations that solve the cube, or null if an error occurs.
     */
    fun computeSolutionSteps(): List<Rotation>? {
        if (mState == CubeState.TESTING) {
            sendMessage("Please wait, the cube is currently being tested.")
            return null
        }
        if (mState != CubeState.IDLE) {
            sendMessage("Invalid state to solve: $mState")
            return null
        }
        clearUndoStack()

        val scrambled = toMin2PhaseString()

        val search = Search()
        val result = search.solution(scrambled, 21, 100_000_000, 0, 0)
        if (result.startsWith("Error")) {
            sendMessage("No solution found or error: $result")
            return null
        }

        val algo = parseMin2PhaseSolution(result)
        Log.d(tag, algo.toString())

        return algo.steps
    }

    /**
     * Allows external classes to set the cube's state.
     */
    fun setState(state: CubeState) {
        mState = state
    }

    /**
     * Sets the flag indicating whether step-by-step solving is active.
     */
    fun setStepByStepSolving(active: Boolean) {
        isStepByStepSolving = active
    }
}
