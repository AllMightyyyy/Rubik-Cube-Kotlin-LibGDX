package com.mycompany.myrubikscube

import com.mycompany.myrubikscube.Log
import com.mycompany.myrubikscube.Axis
import com.mycompany.myrubikscube.Direction

class Algorithm {

    companion object {
        private const val tag = "rubik-algo"

        /**
         * Rotate the entire cube along the given axis and direction, repeated `count` times.
         */
        @JvmStatic
        fun rotateWhole(
            axis: Axis,
            direction: Direction,
            cubeSize: Int,
            count: Int
        ): Algorithm {
            val algo = Algorithm()
            repeat(count) {
                val rot = Rotation(axis, direction, 0, cubeSize)
                algo.addStep(rot)
            }
            return algo
        }
    }

    private val steps = ArrayList<Rotation>()
    private var currentPosition = 0

    /**
     * Primary constructor
     */
    constructor() {
        reset()
    }

    /**
     * Secondary constructor that appends rotations
     */
    constructor(rotations: ArrayList<Rotation>) : this() {
        for (rot in rotations) {
            addStep(rot)
        }
    }

    /**
     * Clears all steps and resets the currentPosition pointer.
     */
    private fun reset() {
        steps.clear()
        currentPosition = 0
    }

    /**
     * Adds a single step with the given axis, direction, face, and faceCount.
     */
    fun addStep(axis: Axis, direction: Direction, face: Int, faceCount: Int) {
        addStep(Rotation(axis, direction, face, faceCount))
    }

    /**
     * Adds a single step with the given axis, direction, and face.
     */
    fun addStep(axis: Axis, direction: Direction, face: Int) {
        addStep(Rotation(axis, direction, face))
    }

    /**
     * Adds a given rotation step to the algorithm.
     */
    fun addStep(rotation: Rotation) {
        steps.add(rotation)
    }

    /**
     * Appends the steps from another algorithm to this one.
     */
    fun append(algo: Algorithm?) {
        if (algo == null) return
        for (rot in algo.steps) {
            addStep(rot.duplicate())
        }
    }

    /**
     * Repeats the last step by duplicating it.
     */
    fun repeatLastStep() {
        if (steps.isEmpty()) return
        val lastRotation = steps[steps.size - 1]
        addStep(lastRotation.duplicate())
    }

    /**
     * Checks if we have exhausted (executed) all steps in this algorithm.
     */
    fun isDone(): Boolean {
        return currentPosition >= steps.size
    }

    /**
     * Returns the next rotation step (duplicated), or `null` if none remain.
     */
    fun getNextStep(): Rotation? {
        return if (currentPosition >= steps.size) {
            Log.w(tag, "No more steps: $currentPosition, ${steps.size}")
            null
        } else {
            steps[currentPosition++].duplicate()
        }
    }
}
