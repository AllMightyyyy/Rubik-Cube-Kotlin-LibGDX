package com.mycompany.myrubikscube.cube

import com.mycompany.myrubikscube.Log
import com.mycompany.myrubikscube.graphics.Axis
import com.mycompany.myrubikscube.graphics.Direction
import java.security.InvalidParameterException
import java.util.ArrayList
import kotlin.math.max

/**
 * This class handles cube's definition. It creates all squares, faces and puts them in appropriate
 * lists for each axis. It also takes care of updating the colors of squares according to user
 * specified rotation. It doesn't care about drawing the cube. You should extend this class
 * rather than using it directly.
 */
open class Cube(
    protected var mSizeX: Int,
    protected var mSizeY: Int,
    protected var mSizeZ: Int
) {

    companion object {
        private const val tag = "rubik-struct"

        // Do not change these values. They are used in many places.
        const val FACE_FRONT = 0
        const val FACE_RIGHT = 1
        const val FACE_BACK = 2
        const val FACE_LEFT = 3
        const val FACE_TOP = 4
        const val FACE_BOTTOM = 5

        /**
         * Default colors don't look nice on cube.
         * Note: Colors are in RBGA (for libGDX)
         */
        const val Color_RED = 0xDD2211FF.toInt()
        const val Color_GREEN = 0x22DD11FF.toInt()
        const val Color_ORANGE = 0xFF7F10FF.toInt()
        const val Color_WHITE = 0xFFFFFFFF.toInt()
        const val Color_YELLOW = 0xFFFF00FF.toInt()
        const val Color_BLUE = 0x0000FFFF
        const val Color_GRAY = 0x7F7F7FFF

        @JvmField var COLOR_TOP = Color_WHITE
        @JvmField var COLOR_BOTTOM = Color_YELLOW
        @JvmField var COLOR_LEFT = Color_ORANGE
        @JvmField var COLOR_RIGHT = Color_RED
        @JvmField var COLOR_FRONT = Color_BLUE
        @JvmField var COLOR_BACK = Color_GREEN

        private val faceNames = arrayOf("front", "right", "back", "left", "top", "bottom")

        // We don't support skewed cubes yet.
        const val CUBE_SIDES = 4
        const val FACE_COUNT = 6

        /**
         * To calculate the square size:
         * Screen spans from -1f to +1f.
         * OpenGL won't draw things close to the frustum border, hence we add padding and use
         * 1.2f instead of 2.0f as the total size
         */
        private const val TOTAL_SIZE = 8.0f
        private const val PADDING = 0.8f
        private const val GAP = 0.1f

        // Clockwise list of faces along each axis
        @JvmField val orderedFacesXaxis = intArrayOf(FACE_FRONT, FACE_TOP, FACE_BACK, FACE_BOTTOM)
        @JvmField val orderedFacesYaxis = intArrayOf(FACE_FRONT, FACE_LEFT, FACE_BACK, FACE_RIGHT)
        @JvmField val orderedFacesZaxis = intArrayOf(FACE_TOP, FACE_RIGHT, FACE_BOTTOM, FACE_LEFT)

        @JvmStatic
        fun face2axis(face: Int): Axis {
            return when (face) {
                FACE_BACK, FACE_FRONT -> Axis.Z_AXIS
                FACE_BOTTOM, FACE_TOP -> Axis.Y_AXIS
                FACE_LEFT, FACE_RIGHT -> Axis.X_AXIS
                else -> throw InvalidParameterException("Whats on face $face")
            }
        }

        @JvmStatic
        fun faceName(face: Int): String {
            return faceNames[face]
        }

        @JvmStatic
        fun getOrderedFaces(axis: Axis): IntArray {
            return when (axis) {
                Axis.X_AXIS -> orderedFacesXaxis
                Axis.Y_AXIS -> orderedFacesYaxis
                Axis.Z_AXIS -> orderedFacesZaxis
            }
        }

        /**
         * Rotate the colors in the border. This is the first part of rotating a layer.
         */
        private fun rotateRingColors(squareList: ArrayList<ArrayList<Square>>, dir: Direction, size: Int) {
            val tempColors = ArrayList<Int>(size)
            val workingCopy: ArrayList<ArrayList<Square>>

            if (dir == Direction.COUNTER_CLOCKWISE) {
                // input is in clockwise order
                workingCopy = squareList
            } else {
                // reverse and rotate
                workingCopy = ArrayList(squareList.size)
                for (i in 0 until CUBE_SIDES) {
                    workingCopy.add(squareList[CUBE_SIDES - 1 - i])
                }
            }

            var src = workingCopy[0]
            for (i in 0 until size) {
                tempColors.add(src[i].color)
            }

            for (i in 0 until (CUBE_SIDES - 1)) {
                val dst = workingCopy[i]
                src = workingCopy[i + 1]
                for (j in 0 until size) {
                    dst[j].color = src[j].color
                }
            }

            val dst = workingCopy[CUBE_SIDES - 1]
            for (i in 0 until size) {
                dst[i].color = tempColors[i]
            }
        }

        /**
         * Rotate colors of a given face. This is the second part of rotating a face.
         * This function calls itself recursively to rotate inner squares.
         *
         * We cannot use rotateMatrix functions here as we need an in-place update of colors.
         */
        private fun rotateFaceColors(squares: ArrayList<Square>, direction: Direction, size: Int) {
            val tempColors = ArrayList<Int>(size)
            if (direction == Direction.COUNTER_CLOCKWISE) {
                for (i in 0 until (size - 1)) {
                    tempColors.add(squares[i].color)
                    squares[i].color = squares[i * size + (size - 1)].color
                }
                for (i in 0 until (size - 1)) {
                    squares[i * size + (size - 1)].color =
                        squares[size * size - 1 - i].color
                }
                for (i in 0 until (size - 1)) {
                    squares[size * size - 1 - i].color =
                        squares[size * (size - 1 - i)].color
                }
                for (i in 0 until (size - 1)) {
                    squares[size * (size - 1 - i)].color = tempColors[i]
                }
            } else {
                for (i in 0 until (size - 1)) {
                    tempColors.add(squares[i].color)
                    squares[i].color = squares[size * (size - 1 - i)].color
                }
                for (i in 0 until (size - 1)) {
                    squares[size * (size - 1 - i)].color =
                        squares[size * size - 1 - i].color
                }
                for (i in 0 until (size - 1)) {
                    squares[size * size - 1 - i].color =
                        squares[i * size + (size - 1)].color
                }
                for (i in 0 until (size - 1)) {
                    squares[i * size + (size - 1)].color = tempColors[i]
                }
            }

            if (size > 3) {
                val subset = ArrayList<Square>(size - 2)
                for (i in 1 until size - 1) {
                    for (j in 1 until size - 1) {
                        subset.add(squares[i * size + j])
                    }
                }
                rotateFaceColors(subset, direction, size - 2)
            }
        }

        /**
         * If not symmetric, rotate 180' along the given axis
         */
        private fun skewedRotateRingColors(squareList: ArrayList<ArrayList<Square>>) {
            // swap 0 <-> 2, 1 <-> 3
            val src0 = squareList[0]
            val dst0 = squareList[2]
            for (i in src0.indices) {
                val color = src0[i].color
                src0[i].color = dst0[i].color
                dst0[i].color = color
            }
            val src1 = squareList[1]
            val dst1 = squareList[3]
            for (i in src1.indices) {
                val color = src1[i].color
                src1[i].color = dst1[i].color
                dst1[i].color = color
            }
        }

        private fun skewedRotateFaceColors(squares: ArrayList<Square>, w: Int, h: Int) {
            // If it's a single row/column, just reverse
            if (w == 1 || h == 1) {
                val len = max(w, h)
                for (i in 0 until (len / 2)) {
                    val src = squares[i]
                    val dst = squares[len - 1 - i]
                    val color = src.color
                    src.color = dst.color
                    dst.color = color
                }
                return
            }
            // 2D 180-degree reversal logic
            for (i in 0 until (w - 1)) {
                val src = squares[i]
                val dst = squares[w * h - 1 - i]
                val color = src.color
                src.color = dst.color
                dst.color = color
            }
            for (i in 1 until h) {
                val src = squares[i * w]
                val dst = squares[w * (h - i) - 1]
                val color = src.color
                src.color = dst.color
                dst.color = color
            }
            if (w + h <= 6 || w < 3 || h < 3) return
            val subset = ArrayList<Square>()
            for (i in 1 until (w - 1)) {
                for (j in 1 until (h - 1)) {
                    subset.add(squares[j * w + i])
                }
            }
            skewedRotateFaceColors(subset, w - 2, h - 2)
        }

        /**
         * Utility to rotate a matrix in 90-degree increments (used for rotateCubeX, etc.)
         */
        private fun <T> rotateMatrix(matrix: ArrayList<T>, w: Int, h: Int): ArrayList<T> {
            val rotated = ArrayList<T>(matrix.size)
            for (i in 0 until w) {
                for (j in h downTo 1) {
                    rotated.add(matrix[(j - 1) * w + i])
                }
            }
            return rotated
        }

        private fun <T> rotateMatrixCCW(matrix: ArrayList<T>, w: Int, h: Int): ArrayList<T> {
            val rotated = ArrayList<T>(matrix.size)
            for (i in (w - 1) downTo 0) {
                for (j in 0 until h) {
                    rotated.add(matrix[j * w + i])
                }
            }
            return rotated
        }
    }

    private var squareSize: Float

    // Collections of squares for each face
    protected var mAllSquares = ArrayList<Square>()
    protected var mFrontSquares = ArrayList<Square>()
    protected var mBackSquares = ArrayList<Square>()
    protected var mTopSquares = ArrayList<Square>()
    protected var mBottomSquares = ArrayList<Square>()
    protected var mLeftSquares = ArrayList<Square>()
    protected var mRightSquares = ArrayList<Square>()

    @Suppress("UNCHECKED_CAST")
    protected var mAllFaces = arrayOfNulls<ArrayList<Square>>(FACE_COUNT)

    /**
     * Pieces are used to draw squares during animation. We keep separate sets of layers for each
     * axis and animate pieces from the selected layer of the appropriate set during rotation.
     */
    protected var mAllPieces = ArrayList<Piece>()
    protected var mXaxisLayers = ArrayList<ArrayList<Piece>>()
    protected var mYaxisLayers = ArrayList<ArrayList<Piece>>()
    protected var mZaxisLayers = ArrayList<ArrayList<Piece>>()

    init {
        Log.w(tag, String.format("Cube Dimen: %d %d %d", mSizeX, mSizeY, mSizeZ))
        val maxSize = maxOf(mSizeX, mSizeY, mSizeZ)
        squareSize = (TOTAL_SIZE - PADDING - GAP * (maxSize + 1)) / maxSize
        buildCube()
    }

    private fun buildCube() {
        createArrays()
        createAllSquares()
        createFaces()
    }

    /**
     * We separate creation of ArrayLists from the constructor for clarity
     */
    private fun createArrays() {
        mAllSquares = ArrayList()
        mFrontSquares = ArrayList()
        mBackSquares = ArrayList()
        mTopSquares = ArrayList()
        mBottomSquares = ArrayList()
        mLeftSquares = ArrayList()
        mRightSquares = ArrayList()
        @Suppress("UNCHECKED_CAST")
        mAllFaces = arrayOfNulls<ArrayList<Square>>(FACE_COUNT)
    }

    /**
     * Decides whether a piece is CORNER, EDGE, or CENTER based on row/col
     */
    private fun getPieceType(row: Int, col: Int, totalRows: Int, totalCols: Int): Piece.PieceType {
        return if (row == 0 || row == totalRows - 1) {
            if (col == 0 || col == totalCols - 1) Piece.PieceType.CORNER else Piece.PieceType.EDGE
        } else if (col == 0 || col == totalCols - 1) {
            Piece.PieceType.EDGE
        } else {
            Piece.PieceType.CENTER
        }
    }

    /**
     * Create the squares and store them in their respective lists
     *
     * On each face array, (the respective m***Squares array), the squares are stored from the
     * top-left to the bottom-right order assuming you're looking directly at that face.
     * This order is used in various places and hence should not be changed.
     */
    private fun createAllSquares() {
        createFrontSquares(COLOR_FRONT)
        createBackSquares(COLOR_BACK)
        createLeftSquares(COLOR_LEFT)
        createRightSquares(COLOR_RIGHT)
        createTopSquares(COLOR_TOP)
        createBottomSquares(COLOR_BOTTOM)
    }

    /**
     * Creates the left face squares
     */
    private fun createLeftSquares(color: Int) {
        val startX = leftFaceX
        val startY = (squareSize + GAP) * (mSizeY / 2.0f)
        val startZ = 0f - (squareSize + GAP) * (mSizeZ / 2.0f)

        val vertices = floatArrayOf(
            startX, startY, startZ,
            startX, startY - squareSize, startZ,
            startX, startY - squareSize, startZ + squareSize,
            startX, startY, startZ + squareSize
        )
        for (i in 0 until mSizeY) {
            vertices[1] = startY - i * (squareSize + GAP)
            vertices[4] = vertices[1] - squareSize
            vertices[7] = vertices[1] - squareSize
            vertices[10] = vertices[1]
            for (j in 0 until mSizeZ) {
                vertices[2] = startZ + j * (squareSize + GAP)
                vertices[5] = vertices[2]
                vertices[8] = vertices[2] + squareSize
                vertices[11] = vertices[2] + squareSize
                val sq = Square(vertices, color, FACE_LEFT)
                mAllSquares.add(sq)
                mLeftSquares.add(sq)
            }
        }
    }

    /**
     * Creates the right face squares
     */
    private fun createRightSquares(color: Int) {
        val startX = rightFaceX
        val startY = (squareSize + GAP) * (mSizeY / 2.0f)
        val startZ = (squareSize + GAP) * (mSizeZ / 2.0f)

        val vertices = floatArrayOf(
            startX, startY, startZ,
            startX, startY - squareSize, startZ,
            startX, startY - squareSize, startZ - squareSize,
            startX, startY, startZ - squareSize
        )
        for (i in 0 until mSizeY) {
            vertices[1] = startY - i * (squareSize + GAP)
            vertices[4] = vertices[1] - squareSize
            vertices[7] = vertices[1] - squareSize
            vertices[10] = vertices[1]
            for (j in 0 until mSizeZ) {
                vertices[2] = startZ - j * (squareSize + GAP)
                vertices[5] = vertices[2]
                vertices[8] = vertices[2] - squareSize
                vertices[11] = vertices[2] - squareSize
                val sq = Square(vertices, color, FACE_RIGHT)
                mAllSquares.add(sq)
                mRightSquares.add(sq)
            }
        }
    }

    /**
     * Creates the top face squares
     */
    private fun createTopSquares(color: Int) {
        val startX = -(squareSize + GAP) * (mSizeX / 2.0f)
        val startY = topFaceY
        val startZ = -(squareSize + GAP) * (mSizeZ / 2.0f)

        val vertices = floatArrayOf(
            startX, startY, startZ,
            startX, startY, startZ + squareSize,
            startX + squareSize, startY, startZ + squareSize,
            startX + squareSize, startY, startZ
        )
        for (i in 0 until mSizeZ) {
            vertices[2] = startZ + i * (squareSize + GAP)
            vertices[5] = vertices[2] + squareSize
            vertices[8] = vertices[2] + squareSize
            vertices[11] = vertices[2]
            for (j in 0 until mSizeX) {
                vertices[0] = startX + j * (squareSize + GAP)
                vertices[3] = vertices[0]
                vertices[6] = vertices[0] + squareSize
                vertices[9] = vertices[0] + squareSize
                val sq = Square(vertices, color, FACE_TOP)
                mAllSquares.add(sq)
                mTopSquares.add(sq)
            }
        }
    }

    /**
     * Creates the bottom face squares
     */
    private fun createBottomSquares(color: Int) {
        val startX = -(squareSize + GAP) * (mSizeX / 2.0f)
        val startY = bottomFaceY
        val startZ = (squareSize + GAP) * (mSizeZ / 2.0f)

        val vertices = floatArrayOf(
            startX, startY, startZ,
            startX, startY, startZ - squareSize,
            startX + squareSize, startY, startZ - squareSize,
            startX + squareSize, startY, startZ
        )
        for (i in 0 until mSizeZ) {
            vertices[2] = startZ - i * (squareSize + GAP)
            vertices[5] = vertices[2] - squareSize
            vertices[8] = vertices[2] - squareSize
            vertices[11] = vertices[2]
            for (j in 0 until mSizeX) {
                vertices[0] = startX + j * (squareSize + GAP)
                vertices[3] = vertices[0]
                vertices[6] = vertices[0] + squareSize
                vertices[9] = vertices[0] + squareSize
                val sq = Square(vertices, color, FACE_BOTTOM)
                mAllSquares.add(sq)
                mBottomSquares.add(sq)
            }
        }
    }

    /**
     * Creates the front face squares
     */
    private fun createFrontSquares(color: Int) {
        val startX = 0f - (squareSize + GAP) * (mSizeX / 2.0f)
        val startY = (squareSize + GAP) * (mSizeY / 2.0f)
        val startZ = frontFaceZ

        val vertices = floatArrayOf(
            startX, startY, startZ,
            startX, startY - squareSize, startZ,
            startX + squareSize, startY - squareSize, startZ,
            startX + squareSize, startY, startZ
        )
        for (i in 0 until mSizeY) {
            vertices[1] = startY - i * (squareSize + GAP)
            vertices[4] = vertices[1] - squareSize
            vertices[7] = vertices[1] - squareSize
            vertices[10] = vertices[1]
            for (j in 0 until mSizeX) {
                vertices[0] = startX + j * (squareSize + GAP)
                vertices[3] = vertices[0]
                vertices[6] = vertices[0] + squareSize
                vertices[9] = vertices[0] + squareSize
                val sq = Square(vertices, color, FACE_FRONT)
                mAllSquares.add(sq)
                mFrontSquares.add(sq)
            }
        }
    }

    /**
     * Creates the back face squares
     */
    private fun createBackSquares(color: Int) {
        val startX = (squareSize + GAP) * (mSizeX / 2.0f)
        val startY = (squareSize + GAP) * (mSizeY / 2.0f)
        val startZ = backFaceZ

        val vertices = floatArrayOf(
            startX, startY, startZ,
            startX, startY - squareSize, startZ,
            startX - squareSize, startY - squareSize, startZ,
            startX - squareSize, startY, startZ
        )
        for (i in 0 until mSizeY) {
            vertices[1] = startY - i * (squareSize + GAP)
            vertices[4] = vertices[1] - squareSize
            vertices[7] = vertices[1] - squareSize
            vertices[10] = vertices[1]
            for (j in 0 until mSizeX) {
                vertices[0] = startX - j * (squareSize + GAP)
                vertices[3] = vertices[0]
                vertices[6] = vertices[0] - squareSize
                vertices[9] = vertices[0] - squareSize
                val sq = Square(vertices, color, FACE_BACK)
                mAllSquares.add(sq)
                mBackSquares.add(sq)
            }
        }
    }

    /**
     * Create a new piece or return an existing piece that contains one of the squares. A square
     * can only be part of one piece. This ensures that there are no duplicate or partial pieces.
     */
    private fun createPieceWithSquares(squares: ArrayList<Square>, type: Piece.PieceType): Piece {
        var piece: Piece? = null
        for (p in mAllPieces) {
            for (sq in squares) {
                if (sq in p.mSquares) {
                    piece = p
                    break
                }
            }
            if (piece != null) break
        }
        if (piece == null) {
            piece = Piece(type)
            mAllPieces.add(piece)
        }
        for (sq in squares) {
            piece.addSquare(sq)
        }
        return piece
    }

    /**
     * Create pieces and store them as faces/layers. Three sets of layers are maintained
     * corresponding to each dimension (m*axisFaceList). A piece can have anywhere from
     * one to six squares (in a 1x1x1 cube).
     *
     * To avoid creating duplicate pieces, we store all pieces in a separate list and search it
     * for any existing pieces with the given square before creating a new one.
     *
     * The order of pieces is used in solutions and should not be changed. The outer layers follow
     * the same order as the corresponding face.
     */
    private fun createFaces() {
        mAllFaces[FACE_FRONT] = mFrontSquares
        mAllFaces[FACE_RIGHT] = mRightSquares
        mAllFaces[FACE_BACK] = mBackSquares
        mAllFaces[FACE_LEFT] = mLeftSquares
        mAllFaces[FACE_TOP] = mTopSquares
        mAllFaces[FACE_BOTTOM] = mBottomSquares

        mAllPieces.clear()
        mXaxisLayers.clear()
        mYaxisLayers.clear()
        mZaxisLayers.clear()

        val frontFace = ArrayList<Piece>()
        val rightFace = ArrayList<Piece>()
        val leftFace = ArrayList<Piece>()
        val topFace = ArrayList<Piece>()
        val bottomFace = ArrayList<Piece>()
        val backFace = ArrayList<Piece>()
        val squares = ArrayList<Square>()

        var type: Piece.PieceType

        // Front
        for (i in 0 until mSizeY) {
            for (j in 0 until mSizeX) {
                squares.clear()
                type = getPieceType(i, j, mSizeY, mSizeX)
                squares.add(mFrontSquares[i * mSizeX + j])
                if (i == 0) {
                    squares.add(mTopSquares[(mSizeZ - 1) * mSizeX + j])
                }
                if (i == mSizeY - 1) {
                    squares.add(mBottomSquares[j])
                }
                if (j == 0) {
                    squares.add(mLeftSquares[mSizeZ * (i + 1) - 1])
                }
                if (j == mSizeX - 1) {
                    squares.add(mRightSquares[mSizeZ * i])
                }
                frontFace.add(createPieceWithSquares(squares, type))
            }
        }

        // Right
        for (i in 0 until mSizeY) {
            for (j in 0 until mSizeZ) {
                squares.clear()
                type = getPieceType(i, j, mSizeY, mSizeZ)
                if (j == 0) {
                    squares.add(mFrontSquares[(i + 1) * mSizeX - 1])
                }
                squares.add(mRightSquares[i * mSizeZ + j])
                if (i == 0) {
                    squares.add(mTopSquares[(mSizeZ - j) * mSizeX - 1])
                }
                if (i == mSizeY - 1) {
                    squares.add(mBottomSquares[(j + 1) * mSizeX - 1])
                }
                if (j == mSizeZ - 1) {
                    squares.add(mBackSquares[i * mSizeX])
                }
                rightFace.add(createPieceWithSquares(squares, type))
            }
        }

        // Left
        for (i in 0 until mSizeY) {
            for (j in 0 until mSizeZ) {
                squares.clear()
                if (j == mSizeZ - 1) {
                    squares.add(mFrontSquares[i * mSizeX])
                }
                type = getPieceType(i, j, mSizeY, mSizeZ)
                squares.add(mLeftSquares[i * mSizeZ + j])
                if (i == 0) {
                    squares.add(mTopSquares[j * mSizeX])
                }
                if (i == mSizeY - 1) {
                    squares.add(mBottomSquares[mSizeX * (mSizeZ - j - 1)])
                }
                if (j == 0) {
                    squares.add(mBackSquares[mSizeX * (i + 1) - 1])
                }
                if (mSizeX == 1) {
                    squares.add(mRightSquares[(i + 1) * mSizeZ - 1 - j])
                }
                leftFace.add(createPieceWithSquares(squares, type))
            }
        }

        // Top
        for (i in 0 until mSizeZ) {
            for (j in 0 until mSizeX) {
                squares.clear()
                if (j == 0) {
                    squares.add(mLeftSquares[i])
                }
                if (j == mSizeX - 1) {
                    squares.add(mRightSquares[mSizeZ - 1 - i])
                }
                if (i == mSizeZ - 1) {
                    squares.add(mFrontSquares[j])
                }
                type = getPieceType(i, j, mSizeZ, mSizeX)
                squares.add(mTopSquares[i * mSizeX + j])
                if (i == 0) {
                    squares.add(mBackSquares[mSizeX - 1 - j])
                }
                topFace.add(createPieceWithSquares(squares, type))
            }
        }

        // Bottom
        for (i in 0 until mSizeZ) {
            for (j in 0 until mSizeX) {
                squares.clear()
                if (i == 0) {
                    squares.add(mFrontSquares[mSizeX * (mSizeY - 1) + j])
                }
                if (j == 0) {
                    squares.add(mLeftSquares[mSizeZ * mSizeY - 1 - i])
                }
                if (j == mSizeX - 1) {
                    squares.add(mRightSquares[mSizeZ * (mSizeY - 1) + i])
                }
                type = getPieceType(i, j, mSizeZ, mSizeX)
                squares.add(mBottomSquares[i * mSizeX + j])
                if (i == mSizeZ - 1) {
                    squares.add(mBackSquares[mSizeX * (mSizeY - 1) + mSizeX - 1 - j])
                }
                if (mSizeY == 1) {
                    squares.add(mTopSquares[(mSizeZ - 1 - i) * mSizeX + j])
                }
                bottomFace.add(createPieceWithSquares(squares, type))
            }
        }

        // Back
        for (i in 0 until mSizeY) {
            for (j in 0 until mSizeX) {
                squares.clear()
                if (i == 0) {
                    squares.add(mTopSquares[mSizeX - 1 - j])
                }
                if (i == mSizeY - 1) {
                    squares.add(mBottomSquares[mSizeX * (mSizeZ - 1) + mSizeX - 1 - j])
                }
                if (j == 0) {
                    squares.add(mRightSquares[mSizeZ * (i + 1) - 1])
                }
                if (j == mSizeX - 1) {
                    squares.add(mLeftSquares[i * mSizeZ])
                }
                type = getPieceType(i, j, mSizeY, mSizeX)
                squares.add(mBackSquares[i * mSizeX + j])
                if (mSizeZ == 1) {
                    squares.add(mFrontSquares[(i + 1) * mSizeX - 1 - j])
                }
                backFace.add(createPieceWithSquares(squares, type))
            }
        }

        // X-axis layers
        mXaxisLayers.add(leftFace)
        for (i in 1 until (mSizeX - 1)) {
            val pieces = ArrayList<Piece>()
            for (j in 0 until (mSizeZ - 1)) {
                pieces.add(topFace[j * mSizeX + i])
            }
            for (j in 0 until (mSizeY - 1)) {
                pieces.add(frontFace[j * mSizeX + i])
            }
            for (j in 0 until (mSizeZ - 1)) {
                pieces.add(bottomFace[j * mSizeX + i])
            }
            for (j in 0 until (mSizeY - 1)) {
                pieces.add(backFace[(mSizeX * (mSizeY - 1 - j)) + (mSizeX - 1 - i)])
            }
            mXaxisLayers.add(pieces)
        }
        mXaxisLayers.add(rightFace)

        // Y-axis layers
        mYaxisLayers.add(bottomFace)
        for (i in 1 until (mSizeY - 1)) {
            val pieces = ArrayList<Piece>()
            for (j in 0 until (mSizeX - 1)) {
                pieces.add(frontFace[(mSizeY - 1 - i) * mSizeX + j])
            }
            for (j in 0 until (mSizeZ - 1)) {
                pieces.add(rightFace[(mSizeY - 1 - i) * mSizeZ + j])
            }
            for (j in 0 until (mSizeX - 1)) {
                pieces.add(backFace[(mSizeY - 1 - i) * mSizeX + j])
            }
            for (j in 0 until (mSizeZ - 1)) {
                pieces.add(leftFace[(mSizeY - 1 - i) * mSizeZ + j])
            }
            mYaxisLayers.add(pieces)
        }
        mYaxisLayers.add(topFace)

        // Z-axis layers
        mZaxisLayers.add(backFace)
        for (i in 1 until (mSizeZ - 1)) {
            val pieces = ArrayList<Piece>()
            for (j in 0 until (mSizeX - 1)) {
                pieces.add(topFace[i * mSizeX + j])
            }
            for (j in 0 until (mSizeY - 1)) {
                pieces.add(rightFace[mSizeZ * j + (mSizeZ - 1 - i)])
            }
            for (j in 0 until (mSizeX - 1)) {
                pieces.add(bottomFace[(mSizeZ - 1 - i) * mSizeX + (mSizeX - 1 - j)])
            }
            for (j in 0 until (mSizeY - 1)) {
                pieces.add(leftFace[(mSizeY - 1 - j) * mSizeZ + i])
            }
            mZaxisLayers.add(pieces)
        }
        mZaxisLayers.add(frontFace)

        Log.w(tag, "total pieces: ${mAllPieces.size}")
    }

    val frontFaceZ: Float
        get() = (squareSize + GAP) * (mSizeZ / 2.0f)

    val backFaceZ: Float
        get() = -(squareSize + GAP) * (mSizeZ / 2.0f)

    val leftFaceX: Float
        get() = -(squareSize + GAP) * (mSizeX / 2.0f)

    val rightFaceX: Float
        get() = (squareSize + GAP) * (mSizeX / 2.0f)

    val topFaceY: Float
        get() = (squareSize + GAP) * (mSizeY / 2.0f)

    val bottomFaceY: Float
        get() = -(squareSize + GAP) * (mSizeY / 2.0f)

    protected fun getAxisSize(axis: Axis): Int {
        return when (axis) {
            Axis.X_AXIS -> mSizeX
            Axis.Y_AXIS -> mSizeY
            Axis.Z_AXIS -> mSizeZ
        }
    }

    /**
     * Rotate the face specified by [face] and [axis].
     *
     * Note that the direction is relative to the positive direction of the mentioned axis, and not
     * the visible side of face. This is against the normal cube notation where direction is
     * usually mentioned relative to the face being rotated.
     *
     * For instance, in traditional cube notation, L stands for "Left face clockwise" where the
     * clock is running on the visible face of the left face. It will rotate the left face clockwise
     * around the negative x-axis. In our case, left face is face-0 on the X-axis and to achieve "L",
     * we should call this function with values (X, CCW, 0).
     *
     * Example mappings from traditional notation to this function (assuming 3x3x3 cube):
     * - Front face clockwise: (Z, CW, 2)
     * - Left face clockwise: (X, CCW, 0)
     * - Bottom face clockwise: (Y, CCW, 0)
     */
    protected open fun rotate(axis: Axis, direction: Direction, face: Int) {
        val maxSize = getAxisSize(axis)
        if (face >= maxSize) {
            throw AssertionError(
                String.format(
                    "face mismatch %d %d %d: axis %s, face %d",
                    mSizeX, mSizeY, mSizeZ, axis.toString(), face
                )
            )
        }

        var w = 0
        var h = 0
        var faceSquares: ArrayList<Square>? = null
        var oppositeFace: ArrayList<Square>? = null

        // This list holds the squares from the sides of the layer being rotated
        val squareList = ArrayList<ArrayList<Square>>(CUBE_SIDES)
        repeat(CUBE_SIDES) {
            squareList.add(ArrayList())
        }

        when (axis) {
            Axis.X_AXIS -> {
                for (i in 0 until mSizeY) {
                    squareList[0].add(mFrontSquares[mSizeX * i + face])
                    squareList[2].add(
                        mBackSquares[(mSizeY - 1 - i) * mSizeX + (mSizeX - 1 - face)]
                    )
                }
                for (i in 0 until mSizeZ) {
                    squareList[1].add(mTopSquares[mSizeX * i + face])
                    squareList[3].add(mBottomSquares[mSizeX * i + face])
                }
                if (face == 0) {
                    faceSquares = mLeftSquares
                } else if (face == mSizeX - 1) {
                    faceSquares = mRightSquares
                }
                if (mSizeX == 1) {
                    oppositeFace = mRightSquares
                }
                w = mSizeZ
                h = mSizeY
            }

            Axis.Y_AXIS -> {
                for (i in 0 until mSizeX) {
                    squareList[0].add(
                        mFrontSquares[(mSizeY - 1 - face) * mSizeX + i]
                    )
                    squareList[2].add(
                        mBackSquares[(mSizeY - 1 - face) * mSizeX + i]
                    )
                }
                for (i in 0 until mSizeZ) {
                    squareList[1].add(
                        mLeftSquares[(mSizeY - 1 - face) * mSizeZ + i]
                    )
                    squareList[3].add(
                        mRightSquares[(mSizeY - 1 - face) * mSizeZ + i]
                    )
                }
                if (face == 0) {
                    faceSquares = mBottomSquares
                } else if (face == mSizeY - 1) {
                    faceSquares = mTopSquares
                }
                if (mSizeY == 1) {
                    oppositeFace = mTopSquares
                }
                w = mSizeX
                h = mSizeZ
            }

            Axis.Z_AXIS -> {
                for (i in 0 until mSizeX) {
                    squareList[0].add(mTopSquares[mSizeX * face + i])
                    squareList[2].add(
                        mBottomSquares[
                            mSizeX * (mSizeZ - 1 - face) + (mSizeX - 1 - i)
                        ]
                    )
                }
                for (i in 0 until mSizeY) {
                    squareList[1].add(
                        mRightSquares[mSizeZ * i + (mSizeZ - 1 - face)]
                    )
                    squareList[3].add(
                        mLeftSquares[mSizeZ * (mSizeY - 1 - i) + face]
                    )
                }
                if (face == 0) {
                    faceSquares = mBackSquares
                } else if (face == mSizeZ - 1) {
                    faceSquares = mFrontSquares
                }
                if (mSizeZ == 1) {
                    oppositeFace = mFrontSquares
                }
                w = mSizeX
                h = mSizeY
            }
        }

        val symmetric = isSymmetricAroundAxis(axis)
        if (symmetric) {
            val size = if (axis == Axis.X_AXIS) mSizeY else mSizeX
            rotateRingColors(squareList, direction, size)

            // If the rotating face is on the edge, rotate that face's squares
            if (faceSquares != null) {
                if (face == 0) {
                    // Lower layers store colors in opposite direction, so invert direction
                    val inverseDirection = if (direction == Direction.CLOCKWISE)
                        Direction.COUNTER_CLOCKWISE else Direction.CLOCKWISE
                    rotateFaceColors(faceSquares, inverseDirection, size)
                } else {
                    // It's the positive edge of the axis (front/top/right), rotate as is
                    rotateFaceColors(faceSquares, direction, size)
                }
            }
            // If dimension is 1, the opposite face also needs rotating
            if (oppositeFace != null) {
                rotateFaceColors(oppositeFace, direction, size)
            }
        } else {
            // If not symmetric, effectively rotate 180'
            skewedRotateRingColors(squareList)
            if (faceSquares != null) {
                skewedRotateFaceColors(faceSquares, w, h)
            }
            if (oppositeFace != null) {
                skewedRotateFaceColors(oppositeFace, w, h)
            }
        }
    }

    /**
     * Rotate the whole cube along the given [axis].
     * Can be used for 90-degree rotations in skewed cubes.
     *
     * This function basically reorganizes the internal structure of the cube arrays.
     */
    protected open fun rotate(axis: Axis, direction: Direction) {
        var x = 0
        var y = 0
        var z = 0
        var count = 1
        var angle = -90
        if (direction == Direction.COUNTER_CLOCKWISE) {
            angle = 90
            // Instead of a direct single CCW rotation, rotate thrice clockwise.
            count = 3
        }

        repeat(count) {
            when (axis) {
                Axis.X_AXIS -> {
                    x = 1
                    rotateCubeX()
                }
                Axis.Y_AXIS -> {
                    y = 1
                    rotateCubeY()
                }
                Axis.Z_AXIS -> {
                    z = 1
                    rotateCubeZ()
                }
            }
        }

        // Recreate the faces after re-laying out squares
        createFaces()
        updateSquareFaces()

        // Adjust the squares' transforms
        for (sq in mAllSquares) {
            sq.rotateCoordinates(x.toFloat(), y.toFloat(), z.toFloat(), angle)
        }
    }

    protected fun rotateCubeX() {
        // Copy the top face
        val tempFace = ArrayList(mTopSquares)
        mTopSquares = mFrontSquares
        mFrontSquares = mBottomSquares
        mBottomSquares = ArrayList()
        for (i in (mSizeY - 1) downTo 0) {
            for (j in (mSizeX - 1) downTo 0) {
                mBottomSquares.add(mBackSquares[i * mSizeX + j])
            }
        }
        mBackSquares.clear()
        for (i in (mSizeZ - 1) downTo 0) {
            for (j in (mSizeX - 1) downTo 0) {
                mBackSquares.add(tempFace[i * mSizeX + j])
            }
        }

        // rotateMatrix / rotateMatrixCCW
        mRightSquares = rotateMatrix(mRightSquares, mSizeZ, mSizeY)
        mLeftSquares = rotateMatrixCCW(mLeftSquares, mSizeZ, mSizeY)

        val temp = mSizeY
        mSizeY = mSizeZ
        mSizeZ = temp
    }

    protected fun rotateCubeY() {
        val tempFace = mFrontSquares
        mFrontSquares = mRightSquares
        mRightSquares = mBackSquares
        mBackSquares = mLeftSquares
        mLeftSquares = tempFace

        mTopSquares = rotateMatrix(mTopSquares, mSizeX, mSizeZ)
        mBottomSquares = rotateMatrixCCW(mBottomSquares, mSizeX, mSizeZ)

        val temp = mSizeX
        mSizeX = mSizeZ
        mSizeZ = temp
    }

    protected fun rotateCubeZ() {
        val tempFace = ArrayList(mTopSquares)
        mTopSquares = rotateMatrix(mLeftSquares, mSizeZ, mSizeY)
        mLeftSquares = rotateMatrix(mBottomSquares, mSizeX, mSizeZ)
        mBottomSquares = rotateMatrix(mRightSquares, mSizeZ, mSizeY)
        mRightSquares = rotateMatrix(tempFace, mSizeX, mSizeZ)

        mFrontSquares = rotateMatrix(mFrontSquares, mSizeX, mSizeY)
        mBackSquares = rotateMatrixCCW(mBackSquares, mSizeX, mSizeY)

        val temp = mSizeY
        mSizeY = mSizeX
        mSizeX = temp
    }

    private fun updateSquareFaces() {
        for (i in 0 until FACE_COUNT) {
            val face = mAllFaces[i] ?: continue
            for (sq in face) {
                sq.face = i
            }
        }
    }

    val sizeX: Int
        get() = mSizeX
    val sizeY: Int
        get() = mSizeY
    val sizeZ: Int
        get() = mSizeZ
    val squareSizeValue: Float
        get() = squareSize

    protected fun isSymmetricAroundAxis(axis: Axis): Boolean {
        return when (axis) {
            Axis.X_AXIS -> (mSizeY == mSizeZ)
            Axis.Y_AXIS -> (mSizeX == mSizeZ)
            Axis.Z_AXIS -> (mSizeX == mSizeY)
        }
    }
}
