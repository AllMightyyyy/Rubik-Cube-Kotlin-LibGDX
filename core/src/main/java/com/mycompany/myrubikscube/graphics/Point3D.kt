package com.mycompany.myrubikscube.graphics

data class Point3D(
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f
) {
    constructor(xyz: FloatArray, offset: Int) : this(xyz[offset], xyz[offset + 1], xyz[offset + 2])
    constructor(xyz: FloatArray) : this(xyz, 0)
    constructor(that: Point3D) : this(that.x, that.y, that.z)

    fun setXYZ(nx: Float, ny: Float, nz: Float) {
        x = nx
        y = ny
        z = nz
    }

    fun dist(ptx: Float, pty: Float, ptz: Float): Float {
        return kotlin.math.sqrt((x - ptx) * (x - ptx) + (y - pty) * (y - pty) + (z - ptz) * (z - ptz))
    }

    fun dist(pt: Point3D): Float {
        return dist(pt.x, pt.y, pt.z)
    }

    override fun toString(): String {
        return String.format("(%f, %f, %f)", x, y, z)
    }
}
