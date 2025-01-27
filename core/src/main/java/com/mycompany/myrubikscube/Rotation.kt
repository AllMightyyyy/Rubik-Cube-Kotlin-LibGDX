package com.mycompany.myrubikscube

import com.mycompany.myrubikscube.Axis
import com.mycompany.myrubikscube.Direction

/**
 * A rotation step: axis, direction, face/layer, plus angle & faceCount for higher-order cubes.
 *
 * Note that the direction is relative to the positive direction of the mentioned axis,
 * and not the visible side of the face. This is slightly different than standard cube notations.
 */
class Rotation {

    // We store an 'active' flag internally instead of naming it 'status' to avoid signature clash.
    private var active: Boolean = false

    /**
     * Axis to rotate around.
     */
    var axis: Axis = Axis.Z_AXIS

    /**
     * Rotation direction (clockwise or counter-clockwise).
     */
    var direction: Direction = Direction.CLOCKWISE

    /**
     * Which face index to start the rotation on. (For NxN cubes, this can be 0..N-1.)
     */
    var startFace: Int = 0

    /**
     * How many consecutive layers to rotate, e.g. 2 or 3 layers at once on a NxN cube.
     */
    var faceCount: Int = 1

    /**
     * Current angle of rotation (used by the animator).
     */
    var angle: Float = 0f

    // Constructors
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

    /**
     * Returns a new Rotation with the same parameters.
     */
    fun duplicate(): Rotation {
        val r = Rotation(axis, direction, startFace, faceCount)
        r.angle = angle
        r.active = active
        return r
    }

    /**
     * Returns a new Rotation with the opposite direction.
     */
    fun getReverse(): Rotation {
        val rot = duplicate()
        rot.direction = if (rot.direction == Direction.CLOCKWISE) {
            Direction.COUNTER_CLOCKWISE
        } else {
            Direction.CLOCKWISE
        }
        return rot
    }

    /**
     * Sets default values for all fields.
     */
    fun reset() {
        active = false
        axis = Axis.Z_AXIS
        direction = Direction.CLOCKWISE
        startFace = 0
        faceCount = 1
        angle = 0f
    }

    /**
     * Returns a short debug description of the rotation.
     */
    override fun toString(): String {
        val axes = "XYZ"
        return "Axis ${axes[axis.ordinal]}, direction $direction, face $startFace" +
            if (faceCount > 1) " faces $faceCount" else ""
    }

    /**
     * Called during animation to increment the angle of rotation by angleDelta,
     * clamping at maxAngle.
     */
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

    /**
     * Marks this rotation as active.
     */
    fun start() {
        active = true
    }

    /**
     * Returns whether or not this rotation is active/started.
     */
    fun getStatus(): Boolean {
        return active
    }
}
