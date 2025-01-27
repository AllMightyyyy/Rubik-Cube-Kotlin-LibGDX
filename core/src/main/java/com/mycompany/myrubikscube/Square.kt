package com.mycompany.myrubikscube

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox

/**
 * Represents a single facelet (square) on the cube.
 */
class Square {

    companion object {
        private const val tag = "rubik-square"
    }

    var face: Int = -1

    /**
     * Color is in RBGA format, used by LibGDX.
     */
    var color: Int = Cube.Color_GRAY
        set(value) {
            if (field == value) return
            field = value
            instance.materials[0].set(ColorAttribute.createDiffuse(Color(value)))
        }

    // We'll store the model and instance as lateinit
    private lateinit var model: Model
    lateinit var instance: ModelInstance

    private var mCenter = Point3D()
    private val centerVector = Vector3()
    private var _radius = 0f

    constructor(vertices: FloatArray, colorVal: Int, faceVal: Int) {
        initSquare(vertices, colorVal, faceVal)
    }

    constructor(vertices: FloatArray, colorVal: Int) : this(vertices, colorVal, -1)
    constructor(vertices: FloatArray) : this(vertices, Cube.Color_GRAY)

    constructor(points: Array<Point3D>, colorVal: Int) {
        val verts = FloatArray(points.size * 3)
        for ((i, point) in points.withIndex()) {
            verts[i * 3] = point.x
            verts[i * 3 + 1] = point.y
            verts[i * 3 + 2] = point.z
        }
        initSquare(verts, colorVal, -1)
    }

    private fun initSquare(vertices: FloatArray, colorVal: Int, faceVal: Int) {
        val builder = ModelBuilder()
        val material = Material(ColorAttribute.createDiffuse(Color(colorVal)))

        model = builder.createRect(
            vertices[0], vertices[1], vertices[2],
            vertices[3], vertices[4], vertices[5],
            vertices[6], vertices[7], vertices[8],
            vertices[9], vertices[10], vertices[11],
            0f, 0f, 0f,
            material,
            VertexAttributes.Usage.Position.toLong()
        )
        instance = ModelInstance(model)

        this.face = faceVal
        this.color = colorVal

        // Compute approximate center
        mCenter.x = (vertices[0] + vertices[3] + vertices[6] + vertices[9]) / 4f
        mCenter.y = (vertices[1] + vertices[4] + vertices[7] + vertices[10]) / 4f
        mCenter.z = (vertices[2] + vertices[5] + vertices[8] + vertices[11]) / 4f

        val box = BoundingBox()
        val dims = Vector3()
        instance.calculateBoundingBox(box)
        box.getCenter(centerVector)
        box.getDimensions(dims)
        _radius = dims.len() / 2f
    }

    fun center(): Vector3 {
        return centerVector
    }

    fun radius(): Float {
        return _radius
    }

    fun getCenter(): Point3D {
        return mCenter
    }

    fun rotateCoordinates(x: Float, y: Float, z: Float, degrees: Int) {
        instance.transform.setToRotation(x, y, z, degrees.toFloat())
    }

    fun rotateCoordinates(axis: Axis, angle: Int) {
        var rx = 0f
        var ry = 0f
        var rz = 0f
        when (axis) {
            Axis.X_AXIS -> rx = 1f
            Axis.Y_AXIS -> ry = 1f
            Axis.Z_AXIS -> rz = 1f
        }
        rotateCoordinates(rx, ry, rz, angle)
    }

    fun colorName(): String {
        return String.format("#%08X", color)
    }
}
