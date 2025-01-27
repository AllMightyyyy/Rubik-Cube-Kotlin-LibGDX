package com.mycompany.myrubikscube

import com.mycompany.myrubikscube.Log
import com.mycompany.myrubikscube.Direction
import com.mycompany.myrubikscube.Axis
import com.mycompany.myrubikscube.CubeRenderer
import java.security.InvalidParameterException
import java.util.ArrayList
import java.util.Random

/**
 * This class handles the rotation and drawing for all cubes.
 *
 * This might be used with a renderer or with a solver, etc.
 */
open class RubiksCube : Cube {
    companion object {
        private const val tag = "rubik-cube"
        const val ANGLE_DELTA_SLOW = 2f
        const val ANGLE_DELTA_NORMAL = 4f
        const val ANGLE_DELTA_FAST = 10f
        private const val MAX_UNDO_COUNT = 40
    }

    enum class CubeState {
        IDLE,
        RANDOMIZE,
        SOLVING,
        HELPING,
        TESTING
    }

    protected var mListener: CubeListener? = null
    protected var mState = CubeState.IDLE
    private var mRotation = Rotation()

    private enum class RotateMode {
        NONE,
        MANUAL,
        RANDOM,
        ALGORITHM,
        REPEAT
    }

    private var rotateMode = RotateMode.NONE
    private var mCurrentAlgo: Algorithm? = null
    var mMoveCount = 0
        protected set

    private var mUndoStack = ArrayList<Rotation>()
    private var mUndoingFlag = false
    private var mRandomizedMoves = ArrayList<Rotation>()
    private var mRenderer: CubeRenderer? = null

    private val SLOW = 0
    private val MEDIUM = 1
    private val FAST = 2

    private var mSpeed = MEDIUM
    protected var mAngleDelta = ANGLE_DELTA_NORMAL

    constructor(x: Int, y: Int, z: Int) : super(x, y, z) {
        init()
    }

    constructor(size: Int) : super(size, size, size) {
        init()
    }

    private fun init() {
        mCurrentAlgo = null
        mRotation = Rotation()
        mUndoStack = ArrayList()
        mRandomizedMoves = ArrayList()
        mMoveCount = 0
    }

    fun setRenderer(renderer: CubeRenderer?) {
        mRenderer = renderer
    }

    /**
     * Not implemented: placeholders for restoring colors
     */
    fun restoreColors(colors: String) {
        // no-op
    }

    /**
     * Not implemented: placeholders for storing colors
     */
    fun getColorString(): String? {
        return null
    }

    fun getState(): CubeState {
        return mState
    }

    fun newGame(count: Int) {
        reset()
        randomize(count)
    }

    /**
     * I give up; how did you do it?
     *
     * 1. Bring the cube to its base state
     * 2. Apply the moves made during scrambling
     * 3. Create an Algorithm with those moves reversed
     * 4. Start executing the algorithm
     */
    fun helpMe() {
        if (mRandomizedMoves.size == 0) {
            return
        }
        reset()
        for (r in mRandomizedMoves) {
            rotate(r.axis, r.direction, r.startFace)
        }
        val algorithm = Algorithm()
        for (i in mRandomizedMoves.indices.reversed()) {
            algorithm.addStep(mRandomizedMoves[i].getReverse())
        }
        mState = CubeState.HELPING
        setAlgo(algorithm)
    }

    /**
     * Rotate randomly for `count` moves. This function just updates the state
     * instantaneously without animating the rotations.
     */
    fun randomize(count: Int) {
        var rotation: Rotation? = null
        val random = Random()
        val axes = arrayOf(Axis.X_AXIS, Axis.Y_AXIS, Axis.Z_AXIS)
        mRandomizedMoves.clear()

        for (i in 0 until count) {
            val axis = axes[Math.abs(random.nextInt() % 3)]
            val direction = if (random.nextBoolean()) Direction.CLOCKWISE else Direction.COUNTER_CLOCKWISE
            val size = getAxisSize(axis)
            val startFace = Math.abs(random.nextInt() % size)

            // Avoid undo-ing moves
            if (i > 0 && rotation != null &&
                rotation.axis == axis && rotation.startFace == startFace &&
                rotation.direction != direction
            ) {
                // re-generate
                continue
            }
            rotation = Rotation(axis, direction, startFace)
            rotate(axis, direction, startFace)
            mRandomizedMoves.add(rotation)
        }
        mMoveCount = 0
        clearUndoStack()
    }

    /**
     * Start scrambling the cube. Random faces will be rotated until stopRandomize is called.
     * This function animates individual rotations.
     */
    fun randomize() {
        if (mState != CubeState.IDLE) {
            Log.e(tag, "invalid state for randomize $mState")
            return
        }
        clearUndoStack()
        rotateMode = RotateMode.RANDOM
        mState = CubeState.RANDOMIZE
        mRotation.start()
    }

    fun stopRandomize() {
        if (mState != CubeState.RANDOMIZE) {
            Log.e(tag, "No randomize in progress $mState")
            return
        }
        rotateMode = RotateMode.NONE
        finishRotation()
        mRotation.reset()
        mState = CubeState.IDLE
        mMoveCount = 0
    }

    protected fun sendMessage(str: String) {
        try {
            mListener?.handleCubeMessage(str)
        } catch (e: Exception) {
            Log.e(tag, e.toString())
        }
        Log.w(tag, str)
    }

    open fun solve(): Int {
        sendMessage("Robots can solve only 3x3 cubes right now")
        return -1
    }

    fun setListener(listener: CubeListener?) {
        mListener = listener
    }

    private fun rotateRandom() {
        mRotation.reset()
        val random = Random()
        val axes = arrayOf(Axis.X_AXIS, Axis.Y_AXIS, Axis.Z_AXIS)
        val axis = axes[Math.abs(random.nextInt() % 3)]
        val direction = if (random.nextBoolean()) Direction.CLOCKWISE else Direction.COUNTER_CLOCKWISE
        val size = getAxisSize(axis)
        val startFace = Math.abs(random.nextInt() % size)

        mRotation.axis = axis
        mRotation.direction = direction
        mRotation.startFace = startFace
        mRotation.start()
    }

    /**
     * So far we changed only the orientation of the pieces. This function updates
     * the colors of squares according to the Rotation in progress.
     */
    private fun finishRotation() {
        val symmetric = isSymmetricAroundAxis(mRotation.axis)
        if (!symmetric && mRotation.faceCount == getAxisSize(mRotation.axis)) {
            rotate(mRotation.axis, mRotation.direction)
        } else {
            for (face in mRotation.startFace until mRotation.startFace + mRotation.faceCount) {
                rotate(mRotation.axis, mRotation.direction, face)
            }
        }

        if (!mUndoingFlag && mRotation.faceCount != getAxisSize(mRotation.axis)) {
            mMoveCount++
        }

        if (mUndoingFlag) {
            mUndoingFlag = false
            if (mRotation.faceCount != getAxisSize(mRotation.axis)) {
                mMoveCount--
            }
        }

        when (rotateMode) {
            RotateMode.ALGORITHM -> {
                if (mCurrentAlgo?.isDone() == true) {
                    mRotation.reset()
                    updateAlgo()
                } else {
                    mRotation = mCurrentAlgo?.getNextStep() ?: Rotation()
                    mRotation.start()
                }
            }
            RotateMode.REPEAT -> {
                repeatRotation()
            }
            RotateMode.RANDOM -> {
                rotateRandom()
            }
            else -> {
                mRotation.reset()
                rotateMode = RotateMode.NONE
                mState = CubeState.IDLE
            }
        }

        mListener?.handleRotationCompleted()

        if (mState == CubeState.IDLE && isSolved()) {
            mListener?.handleCubeSolved()
        }
    }

    protected open fun updateAlgo() {
        rotateMode = RotateMode.NONE
        mRotation.reset()
        mCurrentAlgo = null
        if (mState == CubeState.TESTING || mState == CubeState.HELPING) {
            mState = CubeState.IDLE
        }
    }

    private fun repeatRotation() {
        mRotation.angle = 0f
        mRotation.start()
    }

    /**
     * Called during each render to draw the cube.
     */
    fun draw() {
        if (rotateMode == RotateMode.NONE || !mRotation.getStatus()) {
            drawCube()
            return
        }

        val axisSize = getAxisSize(mRotation.axis)
        val angle = mRotation.angle
        var angleX = 0f
        var angleY = 0f
        var angleZ = 0f

        // Use a single 'val faceList = when(...) {...}' expression
        val faceList = when (mRotation.axis) {
            Axis.X_AXIS -> {
                angleX = 1f
                mXaxisLayers
            }
            Axis.Y_AXIS -> {
                angleY = 1f
                mYaxisLayers
            }
            Axis.Z_AXIS -> {
                angleZ = 1f
                mZaxisLayers
            }
        }

        // Now faceList is properly assigned once in the 'when' expression
        try {
            // draw pieces up to the rotating layer
            for (i in 0 until mRotation.startFace) {
                val pieces = faceList[i]
                for (piece in pieces) {
                    for (square in piece.mSquares) {
                        mRenderer?.drawSquare(square)
                    }
                }
            }

            // draw the rotating layers
            for (i in 0 until mRotation.faceCount) {
                val pieces = faceList[mRotation.startFace + i]
                for (piece in pieces) {
                    for (square in piece.mSquares) {
                        mRenderer?.drawSquare(square, angle, angleX, angleY, angleZ)
                    }
                }
            }

            // draw anything above the rotating layer
            for (i in (mRotation.startFace + mRotation.faceCount) until axisSize) {
                val pieces = faceList[i]
                for (piece in pieces) {
                    for (square in piece.mSquares) {
                        mRenderer?.drawSquare(square)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(
                tag,
                "Exc in rot ${mRotation} for sizes ${getSizeX()} ${getSizeY()} ${getSizeZ()}"
            )
            throw e
        }
    }

    private fun drawCube() {
        for (sq in mAllSquares) {
            mRenderer?.drawSquare(sq)
        }
    }

    fun onNextFrame() {
        if (rotateMode == RotateMode.NONE || !mRotation.getStatus()) {
            return
        }
        val axisSize = getAxisSize(mRotation.axis)
        val symmetric = isSymmetricAroundAxis(mRotation.axis)
        var maxAngle = if (symmetric) 90f else 180f
        if (mRotation.faceCount == axisSize) {
            maxAngle = 90f
        }

        if (kotlin.math.abs(mRotation.angle) > maxAngle - 0.01f) {
            finishRotation()
        } else {
            mRotation.increment(mAngleDelta, maxAngle)
        }
    }

    private fun checkFace(squares: ArrayList<Square>): Boolean {
        val centerColor = squares[squares.size / 2].color
        for (sq in squares) {
            if (sq.color != centerColor) return false
        }
        return true
    }

    protected open fun isSolved(): Boolean {
        return (checkFace(mTopSquares)
            && checkFace(mLeftSquares)
            && checkFace(mFrontSquares)
            && checkFace(mRightSquares)
            && checkFace(mBackSquares)
            && checkFace(mBottomSquares))
    }

    protected fun setAlgo(algo: Algorithm) {
        if (mCurrentAlgo != null && !mCurrentAlgo!!.isDone()) {
            throw IllegalStateException("There is already an algorithm running")
        }
        if (mState != CubeState.SOLVING && mState != CubeState.TESTING && mState != CubeState.HELPING) {
            throw IllegalStateException("Invalid state for algos: $mState")
        }
        mCurrentAlgo = algo
        val nextStep = algo.getNextStep()
        if (nextStep != null) {
            mRotation = nextStep
            rotateMode = RotateMode.ALGORITHM
            mRotation.start()
        }
    }

    fun rotate(rotation: Rotation) {
        if (mState != CubeState.IDLE) {
            Log.w(tag, "Cannot rotate in state $mState")
            return
        }
        if (rotateMode != RotateMode.NONE) {
            Log.w(tag, "Cannot rotate in mode $rotateMode")
            return
        }
        val size = getAxisSize(rotation.axis)
        if (rotation.startFace + rotation.faceCount > size) {
            // Adjust to avoid errors
            rotation.faceCount = size - rotation.startFace
        }
        if (rotation.startFace >= size) {
            return
        }
        rotateMode = RotateMode.MANUAL
        mRotation = rotation.duplicate()
        if (mUndoStack.size == MAX_UNDO_COUNT) {
            mUndoStack.removeAt(0)
        }
        mUndoStack.add(rotation.getReverse())
        mRotation.start()
    }

    fun undo() {
        if (mState != CubeState.IDLE) {
            Log.w(tag, "Cannot undo in state $mState")
            return
        }
        if (rotateMode != RotateMode.NONE) {
            Log.w(tag, "Cannot undo in mode $rotateMode")
            return
        }
        if (mUndoStack.isEmpty()) {
            Log.d(tag, "nothing to undo")
            return
        }
        rotateMode = RotateMode.MANUAL
        mUndoingFlag = true
        val index = mUndoStack.size - 1
        val rotation = mUndoStack[index]
        mUndoStack.removeAt(index)
        mRotation = rotation
        mRotation.start()
    }

    protected fun clearUndoStack() {
        mUndoStack.clear()
    }

    protected open fun startSolving() {
        mMoveCount = 0
    }

    open fun cancelSolving(): Int {
        if (mState == CubeState.SOLVING) {
            rotateMode = RotateMode.MANUAL
            mCurrentAlgo = null
            // Next frame's finishRotation will set state to IDLE
        }
        return 0
    }

    fun setSpeed(speed: Int) {
        mSpeed = speed
        when (speed) {
            FAST -> mAngleDelta = ANGLE_DELTA_FAST
            MEDIUM -> mAngleDelta = ANGLE_DELTA_NORMAL
            SLOW -> mAngleDelta = ANGLE_DELTA_SLOW
        }
    }

    /**
     * Sets the color of the whole cube
     */
    fun setColor(color: Int) {
        for (sq in mAllSquares) {
            sq.color = color
        }
    }

    /**
     * Sets the color of squares on the given face
     * @face One of the FACE_* values
     */
    fun setColor(face: Int, color: Int) {
        require(!(face < 0 || face >= FACE_COUNT)) { "Face $face" }
        for (sq in mAllFaces[face]) {
            sq.color = color
        }
    }

    /**
     * TODO: The below setColor calls that involve row, column, or layers have placeholders for skewed cubes
     */

    fun setColor(axis: Axis, layer: Int, color: Int) {
        val pieces = when (axis) {
            Axis.X_AXIS -> mXaxisLayers[layer]
            Axis.Y_AXIS -> mYaxisLayers[layer]
            Axis.Z_AXIS -> mZaxisLayers[layer]
        }
        for (p in pieces) {
            for (sq in p.mSquares) {
                sq.color = color
            }
        }
    }

    fun reset() {
        if (mState != CubeState.IDLE) {
            sendMessage("cube is in state $mState")
            return
        }
        setColor(FACE_FRONT, COLOR_FRONT)
        setColor(FACE_BACK, COLOR_BACK)
        setColor(FACE_BOTTOM, COLOR_BOTTOM)
        setColor(FACE_TOP, COLOR_TOP)
        setColor(FACE_LEFT, COLOR_LEFT)
        setColor(FACE_RIGHT, COLOR_RIGHT)
        clearUndoStack()
        mMoveCount = 0
    }

    fun getSquares(): ArrayList<Square> {
        return mAllSquares
    }

    fun getMoveCount(): Int {
        return mMoveCount
    }

    /**
     * - User swipes across the cube for playing.
     * - Only one layer is rotated at a time.
     * - The layer is identified from the first and last squares touched by the user.
     * - The direction is estimated from the order of these squares.
     * - The indices correspond to the mAllSquares array, returned by getSquares()
     */
    fun tryRotate(startIndex: Int, endIndex: Int) {
        if (startIndex < 0 || startIndex >= mAllSquares.size ||
            endIndex < 0 || endIndex >= mAllSquares.size
        ) {
            throw InvalidParameterException(
                String.format(
                    "Index values: %d, %d (max %d)",
                    startIndex,
                    endIndex,
                    mAllSquares.size
                )
            )
        }
        val firstSquare = mAllSquares[startIndex]
        val lastSquare = mAllSquares[endIndex]
        val firstFace = getFaceFromSquare(firstSquare)
        val lastFace = getFaceFromSquare(lastSquare)
        if (firstFace == lastFace) {
            Log.w(tag, "drag started and ended in the same face")
            return
        }
        val axis: Axis =
            if (firstFace != FACE_TOP && firstFace != FACE_BOTTOM && lastFace != FACE_TOP && lastFace != FACE_BOTTOM) {
                Axis.Y_AXIS
            } else if (firstFace != FACE_BACK && firstFace != FACE_FRONT && lastFace != FACE_BACK && lastFace != FACE_FRONT) {
                Axis.Z_AXIS
            } else {
                Axis.X_AXIS
            }

        val faces = getOrderedFaces(axis)
        var firstIndex = -1
        var lastIndex = -1
        for (i in faces.indices) {
            if (firstFace == faces[i]) firstIndex = i
            if (lastFace == faces[i]) lastIndex = i
        }
        if (firstIndex < 0 || lastIndex < 0) {
            throw InvalidParameterException(
                String.format(
                    "Indices: %d, %d (faces %d, %d, axis %s)",
                    firstIndex, lastIndex, firstFace, lastFace, axis
                )
            )
        }
        var direction = Direction.CLOCKWISE
        if ((lastIndex - firstIndex == CUBE_SIDES - 1) ||
            (firstIndex > lastIndex && firstIndex - lastIndex != CUBE_SIDES - 1)
        ) {
            direction = Direction.COUNTER_CLOCKWISE
        }

        val layer = findLayerToRotate(axis, firstFace, firstSquare)
        rotate(Rotation(axis, direction, layer))
    }

    private fun findLayerToRotate(axis: Axis, face: Int, key: Square): Int {
        val layers = when (axis) {
            Axis.X_AXIS -> mXaxisLayers
            Axis.Y_AXIS -> mYaxisLayers
            Axis.Z_AXIS -> mZaxisLayers
        }
        for (i in layers.indices) {
            val layer = layers[i]
            for (piece in layer) {
                val index = piece.mSquares.indexOf(key)
                if (index != -1) {
                    return i
                }
            }
        }
        throw InvalidParameterException(
            String.format(
                "Unreachable: Axis %s, face %d",
                axis.name, face
            )
        )
    }

    private fun getFaceFromSquare(square: Square): Int {
        for (i in mAllFaces.indices) {
            if (mAllFaces[i].contains(square)) return i
        }
        throw InvalidParameterException("Square not found")
    }
}
