package com.mycompany.myrubikscube.graphics

import com.mycompany.myrubikscube.cube.Square

interface CubeRenderer {
    /**
     * Draw square without any rotation
     */
    fun drawSquare(square: Square)

    /**
     * Rotate the square by [angleDegrees] along the axis ([x], [y], [z])
     */
    fun drawSquare(square: Square, angleDegrees: Float, x: Float, y: Float, z: Float)
}
