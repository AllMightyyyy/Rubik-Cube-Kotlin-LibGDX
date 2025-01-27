package com.mycompany.myrubikscube

/**
 * Represents a simple 3D point with x, y, z floats.
 */
class Point3D {
    var x: Float = 0f
    var y: Float = 0f
    var z: Float = 0f

    constructor()

    constructor(x: Float, y: Float, z: Float) {
        this.x = x
        this.y = y
        this.z = z
    }

    constructor(xyz: FloatArray, offset: Int) : this(
        xyz[offset],
        xyz[offset + 1],
        xyz[offset + 2]
    )

    constructor(xyz: FloatArray) : this(xyz, 0)

    constructor(that: Point3D) : this(that.x, that.y, that.z)

    fun setXYZ(xVal: Float, yVal: Float, zVal: Float) {
        x = xVal
        y = yVal
        z = zVal
    }

    fun dist(ptx: Float, pty: Float, ptz: Float): Float {
        val dx = x - ptx
        val dy = y - pty
        val dz = z - ptz
        return kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
    }

    fun dist(pt: Point3D): Float {
        return dist(pt.x, pt.y, pt.z)
    }

    override fun toString(): String {
        return "($x, $y, $z)"
    }
}
