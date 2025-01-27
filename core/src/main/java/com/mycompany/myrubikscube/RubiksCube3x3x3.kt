package com.mycompany.myrubikscube

import com.mycompany.myrubikscube.Log
import com.mycompany.myrubikscube.Direction
import com.mycompany.myrubikscube.Axis
import java.security.InvalidParameterException
import java.util.ArrayList

/**
 * An algorithm can't be smarter than the human who devised it.
 *
 * This is a beginner's approach to solving 3x3 cube. You will see many easy to think of
 * improvements in the logic. Like how it takes many moves for a cube that could have
 * been solved in fewer steps manually.
 */
class RubiksCube3x3x3 : RubiksCube {
    companion object {
        private const val tag = "rubik-3x3x3"
        private const val SIZE = 3
        private const val INNER = 0
        private const val MIDDLE = 1
        private const val OUTER = 2

        private const val FIRST_ROW_LEFT = 0
        private const val FIRST_ROW_CENTER = 1
        private const val FIRST_ROW_RIGHT = 2
        private const val MID_ROW_LEFT = 3
        private const val CENTER = 4
        private const val MID_ROW_RIGHT = 5
        private const val LAST_ROW_LEFT = 6
        private const val LAST_ROW_MIDDLE = 7
        private const val LAST_ROW_RIGHT = 8

        private const val EDGE_MIDDLE_FRONT_LEFT = 0
        private const val EDGE_MIDDLE_FRONT_RIGHT = 2
        private const val EDGE_MIDDLE_RIGHT_BACK = 4
        private const val EDGE_MIDDLE_LEFT_BACK = 6

        private const val CORNER_INDEX_FRONT_RIGHT = 0
        private const val CORNER_INDEX_RIGHT_BACK = 1
        private const val CORNER_INDEX_BACK_LEFT = 2
        private const val CORNER_INDEX_LEFT_FRONT = 3

        private const val EDGE_BOTTOM_NEAR = FIRST_ROW_CENTER
        private const val EDGE_BOTTOM_RIGHT = MID_ROW_RIGHT
        private const val EDGE_BOTTOM_LEFT = MID_ROW_LEFT
        private const val EDGE_BOTTOM_FAR = LAST_ROW_MIDDLE

        private const val EDGE_TOP_FAR = FIRST_ROW_CENTER
        private const val EDGE_TOP_NEAR = LAST_ROW_MIDDLE
        private const val EDGE_TOP_LEFT = MID_ROW_LEFT
        private const val EDGE_TOP_RIGHT = MID_ROW_RIGHT
    }

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

    private var solveState = SolveState.None
    private var mTopColor = 0
    private var mBottomColor = 0

    constructor() : super(SIZE)

    /**
     * Overridden so we can handle only 3x3 solutions
     */
    override fun solve(): Int {
        if (mState == CubeState.TESTING) {
            sendMessage("wait please")
            return -1
        }
        if (mState != CubeState.IDLE) {
            sendMessage("Invalid state to solve: $mState")
            return -1
        }
        clearUndoStack()
        mState = CubeState.SOLVING
        startSolving()
        return 0
    }

    override fun cancelSolving(): Int {
        solveState = SolveState.None
        return super.cancelSolving()
    }

    override fun startSolving() {
        super.startSolving()
        solveState = SolveState.FirstFaceCross
        mTopColor = mTopSquares[RubiksCube3x3x3.CENTER].color
        mBottomColor = mBottomSquares[RubiksCube3x3x3.CENTER].color
        sendMessage("Top is " + mTopSquares[RubiksCube3x3x3.CENTER].colorName() +
            " and bottom is " + mBottomSquares[RubiksCube3x3x3.CENTER].colorName())
        firstFaceCross()
    }

    private fun firstFaceCross() {
        val sideFaces = arrayOf(mBackSquares, mLeftSquares, mRightSquares, mFrontSquares)
        for (i in EDGE_TOP_NEAR downTo 1) {
            if (i % 2 == 0) continue
            val sideFace = sideFaces[i / 2]
            if (mTopSquares[i].color == mTopColor &&
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
        val middleRotations: ArrayList<Rotation>
        var rot: Rotation? = null

        val topColoredSquare = getSquareByColor(mYaxisLayers, OUTER, pos, mTopColor)
        if (pos == EDGE_TOP_FAR || pos == EDGE_TOP_NEAR) {
            val faceIndex = if (topColoredSquare.face == FACE_TOP) FACE_RIGHT else topColoredSquare.face
            rot = Rotation(Axis.Z_AXIS, Direction.CLOCKWISE, if (pos == EDGE_TOP_FAR) INNER else OUTER)
            algo.addStep(rot)
            middleRotations = middleEdgeToTopEdge(
                if (pos == EDGE_TOP_FAR) EDGE_MIDDLE_RIGHT_BACK else EDGE_MIDDLE_FRONT_RIGHT,
                faceIndex
            )
        } else {
            val faceIndex = if (topColoredSquare.face == FACE_TOP) FACE_FRONT else topColoredSquare.face
            rot = Rotation(Axis.X_AXIS, Direction.COUNTER_CLOCKWISE, if (pos == EDGE_TOP_LEFT) INNER else OUTER)
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

    private fun middleEdgeToTopEdge(middlePos: Int, faceWithTopColor: Int): ArrayList<Rotation> {
        val rotations = ArrayList<Rotation>()
        when (middlePos) {
            EDGE_MIDDLE_FRONT_LEFT -> {
                if (!(faceWithTopColor == FACE_FRONT || faceWithTopColor == FACE_LEFT)) {
                    throw AssertionError()
                }
                if (faceWithTopColor == FACE_FRONT) {
                    rotations.add(Rotation(Axis.Y_AXIS, Direction.CLOCKWISE, OUTER))
                    rotations.add(Rotation(Axis.X_AXIS, Direction.CLOCKWISE, INNER))
                    rotations.add(Rotation(Axis.Y_AXIS, Direction.COUNTER_CLOCKWISE, OUTER))
                } else {
                    rotations.add(Rotation(Axis.Z_AXIS, Direction.CLOCKWISE, OUTER))
                }
            }
            EDGE_MIDDLE_FRONT_RIGHT -> {
                if (!(faceWithTopColor == FACE_FRONT || faceWithTopColor == FACE_RIGHT)) {
                    throw AssertionError()
                }
                if (faceWithTopColor == FACE_FRONT) {
                    rotations.add(Rotation(Axis.Y_AXIS, Direction.COUNTER_CLOCKWISE, OUTER))
                    rotations.add(Rotation(Axis.X_AXIS, Direction.CLOCKWISE, OUTER))
                    rotations.add(Rotation(Axis.Y_AXIS, Direction.CLOCKWISE, OUTER))
                } else {
                    rotations.add(Rotation(Axis.Z_AXIS, Direction.COUNTER_CLOCKWISE, OUTER))
                }
            }
            EDGE_MIDDLE_RIGHT_BACK -> {
                if (!(faceWithTopColor == FACE_RIGHT || faceWithTopColor == FACE_BACK)) {
                    throw AssertionError()
                }
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
                if (!(faceWithTopColor == FACE_LEFT || faceWithTopColor == FACE_BACK)) {
                    throw AssertionError()
                }
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

    private fun getSquareByColor(
        faceList: ArrayList<ArrayList<Piece>>,
        index: Int,
        pos: Int,
        color: Int
    ): Square {
        val piece = faceList[index][pos]
        for (sq in piece.mSquares) {
            if (sq.color == color) {
                return sq
            }
        }
        throw InvalidParameterException(
            "Square not found: Index $index, pos $pos, color $color"
        )
    }

    private fun firstFaceEdge_fromMiddleLayer(pos: Int) {
        Log.d(tag, "Edge piece from middle layer")
        val topColorSquare = getSquareByColor(mYaxisLayers, MIDDLE, pos, mTopColor)
        val faceIndex = topColorSquare.face
        val rotations = middleEdgeToTopEdge(pos, faceIndex)
        val algo = Algorithm(rotations)
        setAlgo(algo)
    }

    private fun firstFaceEdge_fromLowerLayer(pos: Int) {
        Log.d(tag, "Edge piece from lower layer")
        val algorithm = Algorithm()
        if (pos == EDGE_BOTTOM_NEAR || pos == EDGE_BOTTOM_FAR) {
            algorithm.addStep(Axis.Y_AXIS, Direction.CLOCKWISE, INNER)
        }
        if (pos <= EDGE_BOTTOM_LEFT) {
            algorithm.addStep(Axis.X_AXIS, Direction.CLOCKWISE, INNER)
            algorithm.addStep(Axis.Z_AXIS, Direction.CLOCKWISE, OUTER)
            if (mTopSquares[EDGE_TOP_LEFT].color == mTopColor &&
                mLeftSquares[FIRST_ROW_CENTER].color == mLeftSquares[CENTER].color
            ) {
                algorithm.addStep(Axis.X_AXIS, Direction.COUNTER_CLOCKWISE, INNER)
            }
        } else {
            algorithm.addStep(Axis.X_AXIS, Direction.CLOCKWISE, OUTER)
            algorithm.addStep(Axis.Z_AXIS, Direction.COUNTER_CLOCKWISE, OUTER)
            if (mTopSquares[EDGE_TOP_RIGHT].color == mTopColor &&
                mRightSquares[FIRST_ROW_CENTER].color == mRightSquares[CENTER].color
            ) {
                algorithm.addStep(Axis.X_AXIS, Direction.COUNTER_CLOCKWISE, OUTER)
            }
        }
        setAlgo(algorithm)
    }

    private fun firstFaceEdge_fromBottomFace(pos: Int) {
        val algo = Algorithm()
        Log.d(tag, "Edge piece from bottom face")
        if (pos != EDGE_BOTTOM_NEAR) {
            val dir = if (pos == EDGE_BOTTOM_LEFT) Direction.COUNTER_CLOCKWISE else Direction.CLOCKWISE
            val rot = Rotation(Axis.Y_AXIS, dir, INNER)
            algo.addStep(rot)
            if (pos == EDGE_BOTTOM_FAR) {
                algo.addStep(rot)
            }
        }
        val rot = Rotation(Axis.Z_AXIS, Direction.COUNTER_CLOCKWISE, OUTER)
        algo.addStep(rot)
        algo.addStep(rot)
        setAlgo(algo)
    }

    private fun firstFaceCorners() {
        val corners = intArrayOf(LAST_ROW_RIGHT, LAST_ROW_LEFT, FIRST_ROW_LEFT, FIRST_ROW_RIGHT)
        for (i in corners.indices) {
            val cornerPiece = mYaxisLayers[INNER][corners[i]]
            val topColoredSquare = cornerPiece.getSquare(mTopColor)
            if (topColoredSquare == null) continue
            if (topColoredSquare.face == FACE_BOTTOM) continue
            Log.d(tag, "Found $cornerPiece at ${corners[i]}")
            firstFaceCorner(corners[i])
            return
        }
        for (i in corners.indices) {
            val cornerPiece = mYaxisLayers[INNER][corners[i]]
            val topColoredSquare = cornerPiece.getSquare(mTopColor)
            if (topColoredSquare == null) continue
            if (topColoredSquare.face != FACE_BOTTOM) {
                throw AssertionError("white faces " + topColoredSquare.face + " at " + corners[i])
            }
            Log.d(tag, "White faces down in $cornerPiece at ${corners[i]}")
            firstFaceCornerWhiteOnBottom(corners[i])
            return
        }
        for (i in corners.indices) {
            val cornerPiece = mYaxisLayers[OUTER][corners[i]]
            if (!isCornerAligned(cornerPiece)) {
                Log.d(tag, "unaligned at top row $cornerPiece at ${corners[i]}")
                firstFaceCornerFromTopLayer(corners[i])
                return
            }
        }
        sendMessage("We have a perfect first layer..!")
        proceedToNextState()
    }

    private fun isCornerAligned(piece: Piece): Boolean {
        if (piece.mSquares.size != 3) throw AssertionError()
        for (sq in piece.mSquares) {
            if (sq.color != mAllFaces[sq.face][CENTER].color) {
                return false
            }
        }
        return true
    }

    private fun firstFaceCornerFromTopLayer(corner: Int) {
        val algorithm = Algorithm()
        val piece = mYaxisLayers[OUTER][corner]
        if (piece.getType() != Piece.PieceType.CORNER) throw AssertionError()
        val topColor = mTopSquares[CENTER].color
        var topColorFace = -1
        for (sq in piece.mSquares) {
            if (sq.color == topColor) {
                topColorFace = sq.face
            }
        }
        val desiredCornerIndex = CORNER_INDEX_FRONT_RIGHT
        val currentCornerIndex = corner2index(FACE_TOP, corner)
        if (desiredCornerIndex != currentCornerIndex) {
            val direction = if (currentCornerIndex == CORNER_INDEX_LEFT_FRONT)
                Direction.COUNTER_CLOCKWISE
            else
                Direction.CLOCKWISE
            algorithm.addStep(Axis.Y_AXIS, direction, 0, SIZE)
            if (topColorFace != FACE_TOP) {
                topColorFace += if (direction == Direction.COUNTER_CLOCKWISE) 1 else -1
            }
            if (currentCornerIndex == CORNER_INDEX_BACK_LEFT) {
                algorithm.repeatLastStep()
                if (topColorFace != FACE_TOP) {
                    topColorFace += if (direction == Direction.COUNTER_CLOCKWISE) 1 else -1
                }
            }
        }
        val modFace = (topColorFace + 4) % 4
        when (modFace) {
            FACE_FRONT, FACE_TOP -> {
                algorithm.addStep(Axis.Z_AXIS, Direction.CLOCKWISE, OUTER)
                algorithm.addStep(Axis.Y_AXIS, Direction.COUNTER_CLOCKWISE, INNER)
                algorithm.addStep(Axis.Z_AXIS, Direction.COUNTER_CLOCKWISE, OUTER)
            }
            FACE_RIGHT -> {
                algorithm.addStep(Axis.X_AXIS, Direction.COUNTER_CLOCKWISE, OUTER)
                algorithm.addStep(Axis.Y_AXIS, Direction.CLOCKWISE, INNER)
                algorithm.addStep(Axis.X_AXIS, Direction.CLOCKWISE, OUTER)
            }
            else -> throw AssertionError("white should not be facing $modFace")
        }
        setAlgo(algorithm)
    }

    private fun firstFaceCornerWhiteOnBottom(corner: Int) {
        val algorithm = Algorithm()
        val piece = mYaxisLayers[INNER][corner]
        if (piece.getType() != Piece.PieceType.CORNER) throw AssertionError()
        val topColor = mTopSquares[CENTER].color
        var sideColor1 = -1
        var sideColor2 = -1
        for (sq in piece.mSquares) {
            if (sq.color == topColor) {
                if (sq.face != FACE_BOTTOM) throw AssertionError()
                continue
            }
            if (sideColor1 == -1) {
                sideColor1 = sq.color
            } else if (sideColor2 == -1) {
                sideColor2 = sq.color
            }
        }
        val face1 = getColorFace(sideColor1)
        val face2 = getColorFace(sideColor2)
        var desiredCorner = FIRST_ROW_LEFT
        if (face1 == FACE_BACK || face2 == FACE_BACK) {
            desiredCorner = LAST_ROW_LEFT
        }
        if (face1 == FACE_RIGHT || face2 == FACE_RIGHT) {
            desiredCorner += 2
        }
        val currentCornerIndex = corner2index(FACE_BOTTOM, corner)
        val desiredCornerIndex = corner2index(FACE_BOTTOM, desiredCorner)
        var delta = kotlin.math.abs(currentCornerIndex - desiredCornerIndex)
        if (desiredCornerIndex != CORNER_INDEX_FRONT_RIGHT) {
            val direction = if (desiredCorner == FIRST_ROW_LEFT)
                Direction.COUNTER_CLOCKWISE
            else
                Direction.CLOCKWISE
            algorithm.addStep(Axis.Y_AXIS, direction, 0, SIZE)
            if (desiredCorner == LAST_ROW_LEFT) {
                algorithm.addStep(Axis.Y_AXIS, direction, 0, SIZE)
            }
        }
        val direction = if (desiredCornerIndex < currentCornerIndex) Direction.CLOCKWISE else Direction.COUNTER_CLOCKWISE
        if (delta == 3) {
            delta = 1
        }
        for (i in 0 until delta) {
            algorithm.addStep(Axis.Y_AXIS, direction, INNER)
        }
        algorithm.addStep(Axis.X_AXIS, Direction.COUNTER_CLOCKWISE, OUTER)
        algorithm.addStep(Axis.Y_AXIS, Direction.CLOCKWISE, INNER)
        algorithm.repeatLastStep()
        algorithm.addStep(Axis.X_AXIS, Direction.CLOCKWISE, OUTER)
        setAlgo(algorithm)
    }

    private fun firstFaceCorner(corner: Int) {
        val piece = mYaxisLayers[INNER][corner]
        if (piece.getType() != Piece.PieceType.CORNER) throw AssertionError()
        val topColor = mTopSquares[CENTER].color
        var topColorFace = -1
        var sideColor = -1
        var bottomColor = -1
        var sideFace = -1
        for (sq in piece.mSquares) {
            when {
                sq.color == topColor -> {
                    topColorFace = sq.face
                    if (topColorFace == FACE_BOTTOM) throw AssertionError()
                }
                sq.face == FACE_BOTTOM -> bottomColor = sq.color
                else -> {
                    sideColor = sq.color
                    sideFace = sq.face
                }
            }
        }
        val sideColorCenterFace = getColorFace(sideColor)
        if (sideColorCenterFace > FACE_LEFT) throw AssertionError()
        val rotations = ArrayList<Rotation>()
        rotations.addAll(bringColorToFront(sideColor))

        var faceDelta = sideColorCenterFace - sideFace
        faceDelta = (faceDelta + 4) % 4
        val dir = if (sideColorCenterFace > sideFace) Direction.COUNTER_CLOCKWISE else Direction.CLOCKWISE
        if (faceDelta == 3) {
            faceDelta = 1
        }

        for (i in 0 until faceDelta) {
            rotations.add(Rotation(Axis.Y_AXIS, dir, INNER))
        }
        topColorFace -= sideFace
        topColorFace = (topColorFace + 4) % 4
        if (topColorFace == FACE_RIGHT) {
            rotations.add(Rotation(Axis.X_AXIS, Direction.COUNTER_CLOCKWISE, OUTER))
            rotations.add(Rotation(Axis.Y_AXIS, Direction.CLOCKWISE, INNER))
            rotations.add(Rotation(Axis.X_AXIS, Direction.CLOCKWISE, OUTER))
        } else if (topColorFace == FACE_LEFT) {
            rotations.add(Rotation(Axis.X_AXIS, Direction.COUNTER_CLOCKWISE, INNER))
            rotations.add(Rotation(Axis.Y_AXIS, Direction.COUNTER_CLOCKWISE, INNER))
            rotations.add(Rotation(Axis.X_AXIS, Direction.CLOCKWISE, INNER))
        } else {
            throw AssertionError("topColorFace should be left or right, not: $topColorFace")
        }
        val algorithm = Algorithm(rotations)
        setAlgo(algorithm)
    }

    private fun getColorFace(color: Int): Int {
        for (i in 0 until FACE_COUNT) {
            if (mAllFaces[i][CENTER].color == color) {
                return i
            }
        }
        throw InvalidParameterException("Color not found: $color")
    }

    private fun bringColorToFront(color: Int): ArrayList<Rotation> {
        val rotations = ArrayList<Rotation>()
        if (color == mFrontSquares[CENTER].color) {
            return rotations
        }
        var axis = Axis.Y_AXIS
        var dir = Direction.CLOCKWISE
        if (color == mTopSquares[CENTER].color) {
            axis = Axis.X_AXIS
            dir = Direction.COUNTER_CLOCKWISE
        } else if (color == mBottomSquares[CENTER].color) {
            axis = Axis.X_AXIS
        } else if (color == mLeftSquares[CENTER].color) {
            dir = Direction.COUNTER_CLOCKWISE
        }
        rotations.add(Rotation(axis, dir, 0, SIZE))
        if (color == mBackSquares[CENTER].color) {
            rotations.add(Rotation(axis, dir, 0, SIZE))
        }
        return rotations
    }

    private fun findPieceOnFace(face: ArrayList<Piece>, colors: IntArray): Int {
        colors.sort()
        for (i in face.indices) {
            val piece = face[i]
            if (piece.mSquares.size != colors.size) continue
            val pieceColors = piece.mSquares.map { it.color }.toIntArray()
            pieceColors.sort()
            var found = true
            for (j in colors.indices) {
                if (colors[j] != pieceColors[j]) {
                    found = false
                    break
                }
            }
            if (found) return i
        }
        return -1
    }

    private fun corner2index(face: Int, corner: Int): Int {
        if (face == FACE_BOTTOM) {
            return when (corner) {
                FIRST_ROW_RIGHT -> CORNER_INDEX_FRONT_RIGHT
                LAST_ROW_RIGHT -> CORNER_INDEX_RIGHT_BACK
                LAST_ROW_LEFT -> CORNER_INDEX_BACK_LEFT
                FIRST_ROW_LEFT -> CORNER_INDEX_LEFT_FRONT
                else -> throw InvalidParameterException("Invalid corner $corner")
            }
        } else if (face == FACE_TOP) {
            return when (corner) {
                FIRST_ROW_LEFT -> CORNER_INDEX_BACK_LEFT
                FIRST_ROW_RIGHT -> CORNER_INDEX_RIGHT_BACK
                LAST_ROW_LEFT -> CORNER_INDEX_LEFT_FRONT
                LAST_ROW_RIGHT -> CORNER_INDEX_FRONT_RIGHT
                else -> throw InvalidParameterException("Invalid corner $corner")
            }
        } else {
            throw InvalidParameterException("not implemented for $face")
        }
    }

    private fun middleLayer() {
        val edges = intArrayOf(LAST_ROW_MIDDLE, MID_ROW_RIGHT, FIRST_ROW_CENTER, MID_ROW_LEFT)
        for (i in edges.indices) {
            val piece = mYaxisLayers[OUTER][edges[i]]
            if (piece.hasColor(mBottomColor)) continue
            Log.d(tag, "Found Edge $piece at ${edges[i]}")
            fixMiddleLayer(edges[i])
            return
        }
        val edges2 = intArrayOf(
            EDGE_MIDDLE_FRONT_LEFT, EDGE_MIDDLE_FRONT_RIGHT,
            EDGE_MIDDLE_RIGHT_BACK, EDGE_MIDDLE_LEFT_BACK
        )
        for (i in edges2.indices) {
            val piece = mYaxisLayers[MIDDLE][edges2[i]]
            if (!isEdgeAligned(piece)) {
                Log.d(tag, "bring to top $piece")
                bringUpUnalignedMiddleEdge(edges2[i])
                return
            }
        }
        sendMessage("Fixed middle layer..!")
        proceedToNextState()
    }

    private fun isEdgeAligned(piece: Piece): Boolean {
        if (piece.getType() != Piece.PieceType.EDGE) throw AssertionError()
        for (sq in piece.mSquares) {
            if (sq.color != mAllFaces[sq.face][CENTER].color) {
                return false
            }
        }
        return true
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
            for (i in 0 until count) {
                algo.addStep(Axis.Y_AXIS, direction, 0, SIZE)
            }
        }
        algo.append(fixMiddleLayerFromFrontFace())
        setAlgo(algo)
    }

    private fun fixMiddleLayer(edge: Int) {
        val piece = mYaxisLayers[OUTER][edge]
        if (piece.getType() != Piece.PieceType.EDGE) throw AssertionError()
        val algo = Algorithm()
        var color1 = -1
        var color2 = -1
        var outerColor = -1
        var outerFace = -1
        for (sq in piece.mSquares) {
            if (sq.color == mBottomColor) {
                throw InvalidParameterException("Yellow shouldn't be there")
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
        var alignPiece: Algorithm? = null
        if (outerFace == FACE_RIGHT && mRightSquares[CENTER].color == outerColor) {
            alignPiece = fixMiddleLayerFromRightFace()
            setAlgo(alignPiece)
            return
        } else if (outerFace == FACE_FRONT && mFrontSquares[CENTER].color == outerColor) {
            alignPiece = fixMiddleLayerFromFrontFace()
            setAlgo(alignPiece)
            return
        }
        val face1 = getColorFace(color1)
        val face2 = getColorFace(color2)
        val faceDeltaColor =
            if (color1 == outerColor) face1 - outerFace else face2 - outerFace
        alignPiece = alignMiddlePiece(outerFace, if (color1 == outerColor) face1 else face2)

        var faceDelta = faceDeltaColor
        faceDelta = (faceDelta + 4) % 4

        algo.append(alignPiece)

        setAlgo(algo)
    }

    private fun alignMiddlePiece(startFace: Int, destFace: Int): Algorithm {
        if (!(startFace in 0..3 && destFace in 0..3)) return Algorithm()
        var delta = kotlin.math.abs(startFace - destFace)
        if (delta == 0) {
            return Algorithm()
        }
        var dir = if (startFace > destFace) Direction.CLOCKWISE else Direction.COUNTER_CLOCKWISE
        if (delta == 3) {
            delta = 1
            dir = if (dir == Direction.CLOCKWISE) Direction.COUNTER_CLOCKWISE else Direction.CLOCKWISE
        }
        val algo = Algorithm()
        for (i in 0 until delta) {
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

    private fun lastFaceCross() {
        val lastFaceColor = mTopSquares[CENTER].color
        val edges = intArrayOf(EDGE_TOP_NEAR, EDGE_TOP_RIGHT, EDGE_TOP_FAR, EDGE_TOP_LEFT)
        val colors = IntArray(CUBE_SIDES)
        var yellowCount = 0
        val front = mTopSquares[LAST_ROW_MIDDLE].color
        val right = mTopSquares[MID_ROW_RIGHT].color
        val back = mTopSquares[FIRST_ROW_CENTER].color
        val left = mTopSquares[MID_ROW_LEFT].color
        val algo = Algorithm()

        for (i in edges.indices) {
            val sq = mTopSquares[edges[i]]
            colors[i] = sq.color
            if (colors[i] == lastFaceColor) {
                yellowCount++
            }
        }
        if (yellowCount == CUBE_SIDES) {
            sendMessage("top cross is in place..!")
            proceedToNextState()
            return
        }
        if (yellowCount != 2) {
            algo.append(lastFaceCrossAlgo(1))
            setAlgo(algo)
            return
        }
        if (colors[FACE_FRONT] == colors[FACE_BACK] || colors[FACE_LEFT] == colors[FACE_RIGHT]) {
            if (colors[FACE_FRONT] == colors[FACE_BACK]) {
                algo.addStep(Axis.Y_AXIS, Direction.CLOCKWISE, OUTER)
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
                throw AssertionError()
            }
        } else if (colors[FACE_BACK] == lastFaceColor) {
            if (colors[FACE_RIGHT] == lastFaceColor) {
                count = 1
                direction = Direction.COUNTER_CLOCKWISE
            }
        }
        for (i in 0 until count) {
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
        for (i in 0 until count) {
            algo.addStep(Axis.X_AXIS, Direction.CLOCKWISE, OUTER)
            algo.addStep(Axis.Y_AXIS, Direction.CLOCKWISE, OUTER)
            algo.addStep(Axis.X_AXIS, Direction.COUNTER_CLOCKWISE, OUTER)
            algo.addStep(Axis.Y_AXIS, Direction.COUNTER_CLOCKWISE, OUTER)
        }
        algo.addStep(Axis.Z_AXIS, Direction.COUNTER_CLOCKWISE, OUTER)
        return algo
    }

    private fun lastFaceCrossAlign() {
        val offsets = IntArray(4)
        val edges = intArrayOf(EDGE_TOP_NEAR, EDGE_TOP_RIGHT, EDGE_TOP_FAR, EDGE_TOP_LEFT)
        var dbg = "offsets:"
        for (i in edges.indices) {
            val piece = mYaxisLayers[OUTER][edges[i]]
            var sideSquare: Square? = null
            for (sq in piece.mSquares) {
                if (sq.face != FACE_TOP) {
                    sideSquare = sq
                    break
                }
            }
            if (sideSquare == null) {
                throw AssertionError("side square null at $i for piece: $piece")
            }
            val face = getColorFace(sideSquare.color)
            if (face == FACE_TOP || face == FACE_BOTTOM) {
                throw AssertionError("color and face mismatch: " + sideSquare.color + ", face: " + face)
            }
            offsets[i] = (face - i + 4) % 4
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
            if (offsets[0] == 3) {
                algo.addStep(Axis.Y_AXIS, Direction.CLOCKWISE, OUTER)
            } else {
                algo.addStep(Axis.Y_AXIS, Direction.COUNTER_CLOCKWISE, OUTER)
            }
            if (kotlin.math.abs(offsets[0]) == 2) {
                algo.repeatLastStep()
            }
            setAlgo(algo)
        } else {
            sendMessage("top cross is now aligned")
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
                if (firstAlignedIndex == -1) firstAlignedIndex = i
            }
        }
        Log.w(tag, "Aligned count $alignedCount")

        if (alignedCount == 0) {
            algorithm.addStep(Axis.Y_AXIS, Direction.CLOCKWISE, OUTER)
            setAlgo(algorithm)
            return
        }
        if (alignedCount == 2) {
            if (offsets[(firstAlignedIndex + 1) % 4] == 0 ||
                offsets[(firstAlignedIndex - 1 + 4) % 4] == 0
            ) {
                // case 1
                algorithm.addStep(Axis.Y_AXIS, Direction.CLOCKWISE, OUTER)
                setAlgo(algorithm)
            } else {
                // case 2
                algorithm.append(lastFaceCrossAlignAlgo(Direction.CLOCKWISE))
                setAlgo(algorithm)
            }
            return
        }
        if (alignedCount == 1) {
            val color = mAllFaces[firstAlignedIndex][CENTER].color
            if (mAllFaces[firstAlignedIndex][FIRST_ROW_CENTER].color != color) {
                throw AssertionError("color mismatch at $firstAlignedIndex: $color - " +
                    mAllFaces[firstAlignedIndex][FIRST_ROW_CENTER].color)
            }
            if (firstAlignedIndex != FACE_FRONT) {
                val direction = if (firstAlignedIndex == FACE_LEFT)
                    Direction.COUNTER_CLOCKWISE
                else
                    Direction.CLOCKWISE
                algorithm.addStep(Axis.Y_AXIS, direction, 0, SIZE)
                if (firstAlignedIndex == FACE_BACK) {
                    algorithm.repeatLastStep()
                }
            }
            if (offsets[(firstAlignedIndex + 1) % 4] == 1) {
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
        for (i in corners.indices) {
            val piece = mYaxisLayers[OUTER][corners[i]]
            if (isCornerPositioned(piece)) {
                positionedCorners++
                if (firstPositionedCorner == -1) {
                    firstPositionedCorner = corner2index(FACE_TOP, corners[i])
                }
            }
        }
        Log.w(tag, "positioned corners $positionedCorners first $firstPositionedCorner")
        if (positionedCorners == 4) {
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
            sendMessage("Something went wrong in top corner positioning")
            return
        }
        if (firstPositionedCorner != FACE_FRONT) {
            val direction = if (firstPositionedCorner == FACE_LEFT) Direction.COUNTER_CLOCKWISE else Direction.CLOCKWISE
            algorithm.addStep(Axis.Y_AXIS, direction, 0, SIZE)
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
            algo.addStep(Axis.X_AXIS, Direction.CLOCKWISE, INNER)
            algo.addStep(Axis.Y_AXIS, Direction.CLOCKWISE, OUTER)
            algo.addStep(Axis.X_AXIS, Direction.COUNTER_CLOCKWISE, OUTER)
            algo.addStep(Axis.Y_AXIS, Direction.COUNTER_CLOCKWISE, OUTER)
            algo.addStep(Axis.X_AXIS, Direction.COUNTER_CLOCKWISE, INNER)
            algo.addStep(Axis.Y_AXIS, Direction.CLOCKWISE, OUTER)
        } else {
            algo.addStep(Axis.X_AXIS, Direction.CLOCKWISE, INNER)
            algo.addStep(Axis.Y_AXIS, Direction.CLOCKWISE, OUTER)
            algo.addStep(Axis.X_AXIS, Direction.CLOCKWISE, OUTER)
            algo.addStep(Axis.Y_AXIS, Direction.COUNTER_CLOCKWISE, OUTER)
            algo.addStep(Axis.X_AXIS, Direction.COUNTER_CLOCKWISE, INNER)
            algo.addStep(Axis.Y_AXIS, Direction.CLOCKWISE, OUTER)
            algo.addStep(Axis.X_AXIS, Direction.COUNTER_CLOCKWISE, OUTER)
            algo.addStep(Axis.Y_AXIS, Direction.COUNTER_CLOCKWISE, OUTER)
        }
        return algo
    }

    private fun isCornerPositioned(piece: Piece): Boolean {
        if (piece.getType() != Piece.PieceType.CORNER) throw AssertionError()
        val faces = IntArray(3)
        for (i in 0..2) {
            faces[i] = piece.mSquares[i].face
        }
        for (sq in piece.mSquares) {
            var found = false
            for (face in faces) {
                if (sq.color == mAllFaces[face][CENTER].color) {
                    found = true
                    break
                }
            }
            if (!found) {
                return false
            }
        }
        return true
    }

    private fun checkTopCorners(): Boolean {
        val corners = intArrayOf(LAST_ROW_RIGHT, FIRST_ROW_RIGHT, FIRST_ROW_LEFT, LAST_ROW_LEFT)
        for (corner in corners) {
            val piece = mYaxisLayers[OUTER][corner]
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
            Log.e(tag, "invalid state $mState")
            return
        }
        when (solveState) {
            SolveState.FirstFaceCross -> {
                solveState = SolveState.FirstFaceCorners
                firstFaceCorners()
            }
            SolveState.FirstFaceCorners -> {
                solveState = SolveState.MiddleLayer
                setAlgo(Algorithm.rotateWhole(Axis.Z_AXIS, Direction.CLOCKWISE, SIZE, 2))
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
            else -> throw AssertionError()
        }
    }
}
