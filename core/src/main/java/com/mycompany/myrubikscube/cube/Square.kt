package com.mycompany.myrubikscube.cube

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.mycompany.myrubikscube.graphics.Axis
import com.mycompany.myrubikscube.graphics.Point3D

class Square {
    private var mFace: Int = -1
    private var mCenter = Point3D()
    private var mColor = 0
    lateinit var modelInstance: ModelInstance
        private set
    private lateinit var model: Model

    private val centerVector = Vector3()
    private var _radius = 0f

    var face: Int
        get() = mFace
        set(value) {
            mFace = value
        }

    val center: Point3D
        get() = mCenter

    constructor(vertices: FloatArray, color: Int, face: Int) {
        initSquare(vertices, color, face)
    }
    constructor(vertices: FloatArray, color: Int) : this(vertices, color, -1)
    constructor(vertices: FloatArray) : this(vertices, Cube.COLOR_GRAY)
    constructor(points: Array<Point3D>, color: Int) {
        val verts = FloatArray(points.size * 3)
        for (i in points.indices) {
            verts[i*3] = points[i].x
            verts[i*3+1] = points[i].y
            verts[i*3+2] = points[i].z
        }
        initSquare(verts, color, -1)
    }

    private fun initSquare(vertices: FloatArray, color: Int, face: Int) {
        val builder = ModelBuilder()
        val material = Material(ColorAttribute.createDiffuse(Color(color)))
        model = builder.createRect(
            vertices[0],  vertices[1],  vertices[2],
            vertices[3],  vertices[4],  vertices[5],
            vertices[6],  vertices[7],  vertices[8],
            vertices[9],  vertices[10], vertices[11],
            0f, 0f, 0f,
            material, VertexAttributes.Usage.Position.toLong()
        )
        modelInstance = ModelInstance(model)
        mColor = color
        mFace = face
        mCenter.x = (vertices[0] + vertices[3] + vertices[6] + vertices[9]) / 4f
        mCenter.y = (vertices[1] + vertices[4] + vertices[7] + vertices[10]) / 4f
        mCenter.z = (vertices[2] + vertices[5] + vertices[8] + vertices[11]) / 4f

        val box = BoundingBox()
        val dimensions = Vector3()
        modelInstance.calculateBoundingBox(box)
        box.getCenter(centerVector)
        box.getDimensions(dimensions)
        _radius = dimensions.len() / 2f
    }

    fun center(): Vector3 {
        return centerVector
    }

    fun radius(): Float {
        return _radius
    }

    fun colorName(): String {
        return String.format("#%08X", mColor)
    }

    var color: Int
        get() = mColor
        set(value) {
            if (value == mColor) return
            mColor = value
            modelInstance.materials[0].set(ColorAttribute.createDiffuse(Color(value)))
        }

    fun rotateCoordinates(x: Float, y: Float, z: Float, degrees: Int) {
        modelInstance.transform.setToRotation(x, y, z, degrees.toFloat())
    }

    fun rotateCoordinates(axis: Axis, angle: Int) {
        when (axis) {
            Axis.X_AXIS -> rotateCoordinates(1f, 0f, 0f, angle)
            Axis.Y_AXIS -> rotateCoordinates(0f, 1f, 0f, angle)
            Axis.Z_AXIS -> rotateCoordinates(0f, 0f, 1f, angle)
        }
    }
}
