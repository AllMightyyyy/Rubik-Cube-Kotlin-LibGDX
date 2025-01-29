package com.mycompany.myrubikscube.cube

import com.mycompany.myrubikscube.graphics.Axis
import com.mycompany.myrubikscube.graphics.Direction

class Rotation {
    var axis: Axis = Axis.Z_AXIS
    var direction: Direction = Direction.CLOCKWISE
    var startFace = 0
    var faceCount = 1
    var angle = 0f
        internal set

    var status = false

    constructor() {
        reset()
    }

    constructor(axis: Axis, dir: Direction, face: Int, faceCount: Int) : this(axis, dir, face) {
        this.faceCount = faceCount
    }

    constructor(axis: Axis, dir: Direction, face: Int) {
        reset()
        this.axis = axis
        this.direction = dir
        this.startFace = face
        this.angle = 0f
    }

    fun duplicate(): Rotation {
        return Rotation(axis, direction, startFace, faceCount)
    }

    fun getReverse(): Rotation {
        val rot = duplicate()
        rot.direction = if (direction == Direction.CLOCKWISE) Direction.COUNTER_CLOCKWISE else Direction.CLOCKWISE
        return rot
    }

    fun reset() {
        status = false
        axis = Axis.Z_AXIS
        direction = Direction.CLOCKWISE
        startFace = 0
        faceCount = 1
        angle = 0f
    }

    override fun toString(): String {
        val axes = "XYZ"
        return "Axis ${axes[axis.ordinal]}, direction $direction, face $startFace" +
            if (faceCount > 1) " faces $faceCount" else ""
    }

    /**
     * Renamed from getStatus() to avoid JVM signature clash with `status` property
     */
    fun isActive(): Boolean {
        return status
    }

    fun start() {
        status = true
    }

    fun increment(angleDelta: Float, maxAngle: Float) {
        if (direction == Direction.CLOCKWISE) {
            angle -= angleDelta
            if (angle < -maxAngle) {
                angle = -maxAngle
            }
        } else {
            angle += angleDelta
            if (angle > maxAngle) {
                angle = maxAngle
            }
        }
    }
}
