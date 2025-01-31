package com.mycompany.myrubikscube.cube

import com.mycompany.myrubikscube.Log
import com.mycompany.myrubikscube.graphics.Axis
import com.mycompany.myrubikscube.graphics.Direction
import java.util.ArrayList

class Algorithm {
    companion object {
        private const val TAG = "rubik-algo"

        fun rotateWhole(axis: Axis, direction: Direction, cubeSize: Int, count: Int): Algorithm {
            val algo = Algorithm()
            for (i in 0 until count) {
                val rot = Rotation(axis, direction, 0, cubeSize)
                algo.addStep(rot)
            }
            return algo
        }
    }

    val steps = ArrayList<Rotation>()
    private var currentPosition = 0

    constructor()

    constructor(rotations: List<Rotation>) : this() {
        for (rot in rotations) {
            addStep(rot)
        }
    }

    private fun reset() {
        steps.clear()
        currentPosition = 0
    }

    fun addStep(axis: Axis, direction: Direction, face: Int, faceCount: Int) {
        addStep(Rotation(axis, direction, face, faceCount))
    }

    fun addStep(axis: Axis, direction: Direction, face: Int) {
        addStep(Rotation(axis, direction, face))
    }

    fun addStep(rotation: Rotation) {
        steps.add(rotation)
    }

    fun append(algo: Algorithm?) {
        if (algo == null) return
        for (rot in algo.steps) {
            addStep(rot.duplicate())
        }
    }

    fun repeatLastStep() {
        if (steps.isNotEmpty()) {
            addStep(steps[steps.size - 1].duplicate())
        }
    }

    fun isDone(): Boolean {
        return currentPosition >= steps.size
    }

    fun getNextStep(): Rotation? {
        return if (currentPosition >= steps.size) {
            Log.w(TAG, "No more steps: $currentPosition, ${steps.size}")
            null
        } else steps[currentPosition++].duplicate()
    }
}

