package com.mycompany.myrubikscube.cube

import java.security.InvalidParameterException
import java.util.ArrayList
import java.util.Random

import com.mycompany.myrubikscube.Log
import com.mycompany.myrubikscube.graphics.Axis
import com.mycompany.myrubikscube.graphics.CubeRenderer
import com.mycompany.myrubikscube.graphics.Direction


open class RubiksCube : Cube {

    companion object {
        private const val tag = "rubik-cube"

        // Default values for incrementing angle during rotation
        const val ANGLE_DELTA_SLOW = 2f
        const val ANGLE_DELTA_NORMAL = 4f
        const val ANGLE_DELTA_FAST = 10f

        private const val MAX_UNDO_COUNT = 40
    }

    // Possible cube states
    enum class CubeState {
        IDLE,
        RANDOMIZE,
        SOLVING,
        HELPING,
        TESTING
    }

    // A listener interface to communicate events back to the caller
    protected var mListener: CubeListener? = null
    protected var mState: CubeState = CubeState.IDLE

    // The current rotation being performed
    private var mRotation: Rotation? = null

    private enum class RotateMode {
        NONE,
        MANUAL,
        RANDOM,
        ALGORITHM,
        REPEAT
    }

    private var rotateMode = RotateMode.NONE

    // If an algorithm (sequence of rotations) is running
    private var mCurrentAlgo: Algorithm? = null

    /**
     * The value can be used to measure solving performance
     * (though it may not always be accurate for manual/auto cases).
     */
    var mMoveCount = 0
        protected set

    /**
     * Stores the past [MAX_UNDO_COUNT] moves for UNDO.
     */
    private var mUndoStack: ArrayList<Rotation>? = null

    /**
     * Stores the future moves for REDO (the opposite of undone moves).
     */
    private var mRedoStack: ArrayList<Rotation>? = null

    private var mUndoingFlag = false

    /**
     * Stores the moves performed during randomize(); used for "helpMe()".
     */
    private var mRandomizedMoves: ArrayList<Rotation>? = null

    /**
     * Optional renderer to draw the cube each frame
     */
    private var mRenderer: CubeRenderer? = null

    // For controlling the speed of rotation
    private var mSpeed = 1  // 0=slow,1=medium,2=fast
    private var mAngleDelta = ANGLE_DELTA_NORMAL

    // Common init code
    private fun init() {
        mCurrentAlgo = null
        mRotation = Rotation()
        mUndoStack = ArrayList()
        mRedoStack = ArrayList()
        mRandomizedMoves = ArrayList()
        mMoveCount = 0
    }

    /**
     * Primary constructors
     */
    constructor(x: Int, y: Int, z: Int) : super(x, y, z) {
        init()
    }

    constructor(size: Int) : this(size, size, size)

    /**
     * Set an optional renderer
     */
    fun setRenderer(renderer: CubeRenderer) {
        mRenderer = renderer
    }

    /**
     * For future use: restore a serialized color layout
     */
    fun restoreColors(colors: String) {
        // TODO: Implement serialization logic if needed
    }

    /**
     * For future use: serialize the color layout
     */
    fun getColorString(): String? {
        // not implemented
        return null
    }

    /**
     * Access the current state
     */
    fun getState(): CubeState {
        return mState
    }

    /**
     * Start a new random game (scramble)
     */
    fun newGame(count: Int) {
        reset()
        randomize(count)
    }

    /**
     * Provide a listener for events
     */
    fun setListener(listener: CubeListener) {
        mListener = listener
    }

    /**
     * The “helpMe()” logic: unscramble using the stored random moves
     */
    fun helpMe() {
        if (mRandomizedMoves.isNullOrEmpty()) {
            return
        }
        reset()
        // Re-apply the random moves
        for (r in mRandomizedMoves!!) {
            rotate(r.axis, r.direction, r.startFace)
        }
        // Build an algorithm that is the reverse
        val algorithm = Algorithm()
        for (i in mRandomizedMoves!!.indices.reversed()) {
            algorithm.addStep(mRandomizedMoves!![i].getReverse())
        }
        mState = CubeState.HELPING
        setAlgo(algorithm)
    }

    /**
     * Randomly rotate for [count] moves (instant, no animation)
     */
    fun randomize(count: Int) {
        var lastRotation: Rotation? = null
        val random = Random()
        val axes = arrayOf(Axis.X_AXIS, Axis.Y_AXIS, Axis.Z_AXIS)
        mRandomizedMoves!!.clear()

        repeat(count) {
            val axis = axes[kotlin.math.abs(random.nextInt(3))]
            val direction = if (random.nextBoolean()) Direction.CLOCKWISE else Direction.COUNTER_CLOCKWISE
            val size = getAxisSize(axis)
            val startFace = kotlin.math.abs(random.nextInt(size))

            // Avoid immediate undo pairs (skip if it's a direct opposite)
            if (lastRotation != null &&
                lastRotation!!.axis == axis &&
                lastRotation!!.startFace == startFace &&
                lastRotation!!.direction != direction
            ) {
                return@repeat
            }

            val rotation = Rotation(axis, direction, startFace)
            rotate(axis, direction, startFace)
            mRandomizedMoves!!.add(rotation)

            lastRotation = rotation
        }

        mMoveCount = 0
        clearUndoStack()
    }

    /**
     * Animate a randomize until stopRandomize() is called
     */
    fun randomize() {
        if (mState != CubeState.IDLE) {
            Log.e(tag, "invalid state for randomize $mState")
            return
        }
        clearUndoStack()
        rotateMode = RotateMode.RANDOM
        mState = CubeState.RANDOMIZE
        mRotation?.start()
    }

    /**
     * Stop the animated randomizing
     */
    fun stopRandomize() {
        if (mState != CubeState.RANDOMIZE) {
            Log.e(tag, "No randomize in progress $mState")
            return
        }
        rotateMode = RotateMode.NONE
        finishRotation()
        mRotation?.reset()
        mState = CubeState.IDLE
        mMoveCount = 0
    }

    /**
     * Basic log + listener message utility
     */
    protected fun sendMessage(str: String) {
        try {
            mListener?.handleCubeMessage(str)
        } catch (e: Exception) {
            Log.e(tag, e.toString())
        }
        Log.w(tag, str)
    }

    /**
     * Default solve is not implemented for general cubes
     */
    open fun solve(): Int {
        sendMessage("Robots can solve only 3x3 cubes right now")
        return -1
    }

    /**
     * Called each frame to process partial rotations, etc.
     */
    fun onNextFrame() {
        if (rotateMode == RotateMode.NONE || mRotation?.status == false) {
            return
        }

        val axisSize = getAxisSize(mRotation!!.axis)
        val symmetric = isSymmetricAroundAxis(mRotation!!.axis)
        // normally 180 deg, but if rotating the whole dimension => 90 deg
        var maxAngle = if (symmetric) 90f else 180f
        if (mRotation!!.faceCount == axisSize) {
            maxAngle = 90f
        }

        if (kotlin.math.abs(mRotation!!.angle) > maxAngle - 0.01f) {
            finishRotation()
        } else {
            mRotation!!.increment(mAngleDelta, maxAngle)
        }
    }

    /**
     * The final step of a rotation: rotate the colors, proceed with next step, etc.
     */
    private fun finishRotation() {
        val axis = mRotation!!.axis
        val faceCount = mRotation!!.faceCount
        val symmetric = isSymmetricAroundAxis(axis)
        if (!symmetric && faceCount == getAxisSize(axis)) {
            // reorient the whole cube
            rotate(axis, mRotation!!.direction)
        } else {
            for (face in mRotation!!.startFace until (mRotation!!.startFace + faceCount)) {
                rotate(axis, mRotation!!.direction, face)
            }
        }

        // If not whole-cube, increment move count (unless we are undoing)
        if (!mUndoingFlag && faceCount != getAxisSize(axis)) {
            mMoveCount++
        }
        if (mUndoingFlag) {
            mUndoingFlag = false
            if (faceCount != getAxisSize(axis)) {
                mMoveCount--
            }
        }

        // Next step in the current rotateMode
        when (rotateMode) {
            RotateMode.ALGORITHM -> {
                if (mCurrentAlgo?.isDone() == true) {
                    mRotation?.reset()
                    updateAlgo()
                } else {
                    mRotation = mCurrentAlgo?.getNextStep()
                    mRotation?.start()
                }
            }
            RotateMode.REPEAT -> {
                repeatRotation()
            }
            RotateMode.RANDOM -> {
                rotateRandom()
            }
            else -> {
                // MANUAL or NONE
                mRotation?.reset()
                rotateMode = RotateMode.NONE
                mState = CubeState.IDLE
            }
        }

        // Notify
        mListener?.handleRotationCompleted()

        // If solved, notify
        if (mState == CubeState.IDLE && isSolved && mListener != null) {
            mListener!!.handleCubeSolved()
        }
    }

    /**
     * Called if an algo step finishes
     */
    protected open fun updateAlgo() {
        rotateMode = RotateMode.NONE
        mRotation?.reset()
        mCurrentAlgo = null
        if (mState == CubeState.TESTING || mState == CubeState.HELPING) {
            mState = CubeState.IDLE
        }
    }

    /**
     * Re-start rotation with angle=0 (used by "REPEAT" mode)
     */
    private fun repeatRotation() {
        mRotation?.angle = 0f
        mRotation?.start()
    }

    /**
     * For random mode, pick a random new rotation
     */
    private fun rotateRandom() {
        mRotation?.reset()
        val random = Random()
        val axes = arrayOf(Axis.X_AXIS, Axis.Y_AXIS, Axis.Z_AXIS)
        mRotation?.axis = axes[kotlin.math.abs(random.nextInt(3))]
        mRotation?.direction = if (random.nextBoolean()) Direction.CLOCKWISE else Direction.COUNTER_CLOCKWISE
        val size = getAxisSize(mRotation!!.axis)
        mRotation?.startFace = kotlin.math.abs(random.nextInt(size))
        mRotation?.start()
    }

    /**
     * Render the cube: if there's no partial rotation, just draw squares,
     * otherwise draw the rotating layers with partial angles.
     */
    fun draw() {
        if (rotateMode == RotateMode.NONE || mRotation?.status == false) {
            // Just draw the entire cube plainly
            drawCube()
            return
        }

        val axis = mRotation!!.axis
        val faceList: ArrayList<ArrayList<Piece>> = when (axis) {
            Axis.X_AXIS -> mXaxisLayers
            Axis.Y_AXIS -> mYaxisLayers
            Axis.Z_AXIS -> mZaxisLayers
        }
        val axisSize = getAxisSize(axis)
        val angle = mRotation!!.angle
        var angleX = 0f
        var angleY = 0f
        var angleZ = 0f
        when (axis) {
            Axis.X_AXIS -> angleX = 1f
            Axis.Y_AXIS -> angleY = 1f
            Axis.Z_AXIS -> angleZ = 1f
        }

        try {
            // Layers before the rotating face
            for (i in 0 until mRotation!!.startFace) {
                for (piece in faceList[i]) {
                    for (sq in piece.mSquares) {
                        mRenderer?.drawSquare(sq)
                    }
                }
            }
            // The rotating layers
            for (i in 0 until mRotation!!.faceCount) {
                val pieces = faceList[mRotation!!.startFace + i]
                for (piece in pieces) {
                    for (sq in piece.mSquares) {
                        mRenderer?.drawSquare(sq, angle, angleX, angleY, angleZ)
                    }
                }
            }
            // Layers after
            for (i in (mRotation!!.startFace + mRotation!!.faceCount) until axisSize) {
                for (piece in faceList[i]) {
                    for (sq in piece.mSquares) {
                        mRenderer?.drawSquare(sq)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, String.format(
                "Exception in rotation %s for sizes %d %d %d",
                mRotation.toString(), sizeX, sizeY, sizeY
            ))
            throw e
        }
    }

    /**
     * Draw every square plainly (no partial rotation).
     */
    private fun drawCube() {
        for (sq in mAllSquares) {
            mRenderer?.drawSquare(sq)
        }
    }

    /**
     * Returns true if each face is uniform
     */
    protected val isSolved: Boolean
        get() {
            fun checkFace(faceSquares: ArrayList<Square>): Boolean {
                val centerColor = faceSquares[faceSquares.size / 2].color
                for (sq in faceSquares) {
                    if (sq.color != centerColor) return false
                }
                return true
            }
            return (checkFace(mTopSquares) &&
                checkFace(mLeftSquares) &&
                checkFace(mFrontSquares) &&
                checkFace(mRightSquares) &&
                checkFace(mBackSquares) &&
                checkFace(mBottomSquares))
        }

    /**
     * Provide an algorithm to run
     */
    protected fun setAlgo(algo: Algorithm) {
        if (mCurrentAlgo != null && mCurrentAlgo!!.isDone() == false) {
            throw IllegalStateException("There is already an algorithm running")
        }
        if (mState != CubeState.SOLVING &&
            mState != CubeState.TESTING &&
            mState != CubeState.HELPING
        ) {
            throw IllegalStateException("Invalid state for algos: $mState")
        }
        mCurrentAlgo = algo
        mRotation = algo.getNextStep()
        rotateMode = RotateMode.ALGORITHM
        mRotation?.start()
    }

    /**
     * A single rotation request from the outside
     */
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
            // Instead of throwing, we correct
            rotation.faceCount = size - rotation.startFace
        }
        if (rotation.startFace >= size) {
            return
        }

        // Because a new manual rotation invalidates the “redo” path
        mRedoStack?.clear()

        rotateMode = RotateMode.MANUAL
        mRotation = rotation.duplicate()

        // Push the reverse onto undo
        if (mUndoStack!!.size == MAX_UNDO_COUNT) {
            mUndoStack!!.removeAt(0)
        }
        mUndoStack!!.add(rotation.getReverse())
        mRotation!!.start()
    }

    /**
     * Undo the last manual move
     */
    fun undo() {
        if (mState != CubeState.IDLE) {
            Log.w(tag, "Cannot undo in state $mState")
            return
        }
        if (rotateMode != RotateMode.NONE) {
            Log.w(tag, "Cannot undo in mode $rotateMode")
            return
        }
        if (mUndoStack.isNullOrEmpty()) {
            Log.d(tag, "nothing to undo")
            return
        }
        rotateMode = RotateMode.MANUAL
        mUndoingFlag = true

        val rotation = mUndoStack!!.removeAt(mUndoStack!!.size - 1)

        // The undone rotation's reverse => push onto redo stack
        val redoRotation = rotation.getReverse()
        if (mRedoStack!!.size == MAX_UNDO_COUNT) {
            mRedoStack!!.removeAt(0)
        }
        mRedoStack!!.add(redoRotation)

        mRotation = rotation
        mRotation!!.start()
    }

    /**
     * Redo the last undone move (if any)
     */
    fun redo() {
        if (mState != CubeState.IDLE) {
            Log.w(tag, "Cannot redo in state $mState")
            return
        }
        if (rotateMode != RotateMode.NONE) {
            Log.w(tag, "Cannot redo in mode $rotateMode")
            return
        }
        if (mRedoStack.isNullOrEmpty()) {
            Log.d(tag, "nothing to redo")
            return
        }
        rotateMode = RotateMode.MANUAL

        val rotation = mRedoStack!!.removeAt(mRedoStack!!.size - 1)

        // The redone rotation's reverse => push back onto undo
        val undoRotation = rotation.getReverse()
        if (mUndoStack!!.size == MAX_UNDO_COUNT) {
            mUndoStack!!.removeAt(0)
        }
        mUndoStack!!.add(undoRotation)

        mRotation = rotation
        mRotation!!.start()
    }

    /**
     * Clear the undo/redo stacks
     */
    protected fun clearUndoStack() {
        mUndoStack?.clear()
        mRedoStack?.clear()
    }

    /**
     * Called before machine solving
     */
    protected open fun startSolving() {
        mMoveCount = 0
    }

    /**
     * Cancels the solver
     */
    open fun cancelSolving(): Int {
        if (mState == CubeState.SOLVING) {
            rotateMode = RotateMode.MANUAL
            mCurrentAlgo = null
            // The state will become IDLE in finishRotation on next frame
        }
        return 0
    }

    /**
     * Control the speed of rotation
     */
    fun setSpeed(speed: Int) {
        mSpeed = speed
        mAngleDelta = when (speed) {
            2 -> ANGLE_DELTA_FAST
            0 -> ANGLE_DELTA_SLOW
            else -> ANGLE_DELTA_NORMAL
        }
    }

    /**
     * Sets the entire cube to a single color
     */
    fun setColor(color: Int) {
        for (sq in mAllSquares) {
            sq.color = color
        }
    }

    /**
     * Sets the color of all squares on a given face
     */
    fun setColor(face: Int, color: Int) {
        require(!(face < 0 || face >= FACE_COUNT)) { "Face $face" }
        val faceSquares = mAllFaces[face] ?: return
        for (sq in faceSquares) {
            sq.color = color
        }
    }

    /**
     * If you want to color an entire axis-later or row, etc.
     * For now, we keep them as in the original stubs.
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

    fun setColor(face: Int, row: Int, column: Int, color: Int) {
        // no-op stub
    }

    fun setRowColor(face: Int, row: Int, color: Int) {
        // no-op stub
    }

    fun setColumnColor(face: Int, column: Int, color: Int) {
        // no-op stub
    }

    /**
     * Reset the cube to the base solved state
     */
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

    /**
     * Return all squares
     */
    fun getSquares(): ArrayList<Square> {
        return mAllSquares
    }

    /**
     * Return how many moves have been made
     */
    fun getMoveCount(): Int {
        return mMoveCount
    }

    /**
     * Called by the InputHandler when user swipes across the cube
     */
    fun tryRotate(startIndex: Int, endIndex: Int) {
        if (startIndex < 0 || startIndex >= mAllSquares.size ||
            endIndex < 0 || endIndex >= mAllSquares.size
        ) {
            throw InvalidParameterException(
                "Index values: $startIndex, $endIndex (max ${mAllSquares.size})"
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
        val axis: Axis = when {
            (firstFace != FACE_TOP && firstFace != FACE_BOTTOM &&
                lastFace != FACE_TOP && lastFace != FACE_BOTTOM) -> Axis.Y_AXIS

            (firstFace != FACE_BACK && firstFace != FACE_FRONT &&
                lastFace != FACE_BACK && lastFace != FACE_FRONT) -> Axis.Z_AXIS

            else -> Axis.X_AXIS
        }

        // Find the face indices in the ordered face array
        val faces = getOrderedFaces(axis)
        var firstIndex = -1
        var lastIndex = -1
        for (i in faces.indices) {
            if (firstFace == faces[i]) firstIndex = i
            if (lastFace == faces[i]) lastIndex = i
        }
        if (firstIndex < 0 || lastIndex < 0) {
            throw InvalidParameterException("Indices: $firstIndex, $lastIndex (faces $firstFace, $lastFace, axis $axis)")
        }

        // If ascending except for a difference of 3 => clockwise, else ccw
        var direction = Direction.CLOCKWISE
        if ((lastIndex - firstIndex == CUBE_SIDES - 1) ||
            (firstIndex > lastIndex && firstIndex - lastIndex != CUBE_SIDES - 1)
        ) {
            direction = Direction.COUNTER_CLOCKWISE
        }

        // Which layer are we rotating?
        val layer = findLayerToRotate(axis, firstFace, firstSquare)
        rotate(Rotation(axis, direction, layer))
    }

    /**
     * Returns which layer index holds the [key] square along the [axis].
     */
    private fun findLayerToRotate(axis: Axis, face: Int, key: Square): Int {
        val layers = when (axis) {
            Axis.X_AXIS -> mXaxisLayers
            Axis.Y_AXIS -> mYaxisLayers
            Axis.Z_AXIS -> mZaxisLayers
        }
        for (i in layers.indices) {
            val layer = layers[i]
            for (piece in layer) {
                if (piece.mSquares.contains(key)) {
                    return i
                }
            }
        }
        throw InvalidParameterException("Unreachable: Axis $axis, face $face")
    }

    /**
     * Given a square, find which face array it belongs to
     */
    private fun getFaceFromSquare(square: Square): Int {
        for (i in mAllFaces.indices) {
            val faceList = mAllFaces[i] ?: continue
            if (square in faceList) {
                return i
            }
        }
        throw InvalidParameterException("Square not found")
    }
}
