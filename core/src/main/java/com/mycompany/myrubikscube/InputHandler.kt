package com.mycompany.myrubikscube

import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.math.Intersector
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.Ray
import java.util.ArrayList

class InputHandler(
    private val cube: RubiksCube,
    private val camera: Camera
) : InputAdapter() {

    companion object {
        private const val tag = "rubik-touch"
    }

    private var touchStartIndex = -1
    private var touchDragIndex = -1
    private var touchStartX = 0
    private var touchStartY = 0

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        touchStartIndex = getObject(screenX, screenY)
        if (touchStartIndex >= 0) {
            touchStartX = screenX
            touchStartY = screenY
            touchDragIndex = touchStartIndex
        }
        return touchStartIndex >= 0
    }

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (touchStartIndex < 0) return false
        val current = getObject(screenX, screenY)
        if (current >= 0) {
            touchDragIndex = current
        }
        Log.w(tag, String.format("Touch: %d, %d", touchStartIndex, touchDragIndex))
        cube.tryRotate(touchStartIndex, touchDragIndex)
        touchStartIndex = -1
        touchDragIndex = -1
        return true
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        if (touchStartIndex == -1) return false
        val index = getObject(screenX, screenY)
        if (index >= 0) {
            touchDragIndex = index
        }
        return true
    }

    private fun getObject(x: Int, y: Int): Int {
        val position = Vector3()
        val ray: Ray = camera.getPickRay(x.toFloat(), y.toFloat())
        var result = -1
        var distance = -1f
        val squares: ArrayList<Square> = cube.getSquares()
        for (i in squares.indices) {
            val square = squares[i]
            val inst = square.instance
            inst.transform.getTranslation(position)
            position.add(square.center())
            val dist2 = ray.origin.dst2(position)
            if (distance > 0 && dist2 > distance) continue
            if (Intersector.intersectRaySphere(ray, position, square.radius(), null)) {
                result = i
                distance = dist2
            }
        }
        return result
    }
}
