package com.mycompany.myrubikscube

import com.mycompany.myrubikscube.Log
import java.util.ArrayList

class Piece {
    companion object {
        private const val tag = "rubik-piece"
    }

    enum class PieceType {
        CORNER,
        CENTER,
        EDGE
    }

    var mSquares: ArrayList<Square>
        private set

    private var mType: PieceType

    constructor(type: PieceType) {
        mSquares = ArrayList()
        mType = type
    }

    constructor(center: Square) {
        mSquares = ArrayList()
        mType = PieceType.CENTER
        mSquares.add(center)
    }

    fun getType(): PieceType {
        return mType
    }

    fun addSquare(sq: Square) {
        if (mSquares.contains(sq)) {
            return
        }
        if ((mType == PieceType.CENTER && mSquares.size >= 2) ||
            (mType == PieceType.EDGE && mSquares.size >= 3) ||
            (mType == PieceType.CORNER && mSquares.size >= 6)
        ) {
            Log.e(
                tag,
                "Too many squares for PieceType $mType, we already have ${mSquares.size}"
            )
        }
        mSquares.add(sq)
    }

    fun hasColor(color: Int): Boolean {
        for (sq in mSquares) {
            if (sq.color == color) return true
        }
        return false
    }

    fun getSquare(color: Int): Square? {
        for (sq in mSquares) {
            if (sq.color == color) return sq
        }
        return null
    }

    override fun toString(): String {
        if (mSquares.isEmpty()) return "(empty)"
        var s = mSquares[0].colorName()
        for (i in 1 until mSquares.size) {
            s += "-" + mSquares[i].colorName()
        }
        return s
    }
}
