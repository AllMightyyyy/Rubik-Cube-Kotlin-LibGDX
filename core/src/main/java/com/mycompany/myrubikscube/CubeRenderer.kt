package com.mycompany.myrubikscube

/**
 * Simple interface for drawing squares of a Rubik's cube.
 */
interface CubeRenderer {

    /**
     * Draw square without any rotation
     */
    fun drawSquare(square: Square)

    /**
     * Rotate the square by angleDegrees around the axis defined by (x, y, z).
     */
    fun drawSquare(square: Square, angleDegrees: Float, x: Float, y: Float, z: Float)
}
