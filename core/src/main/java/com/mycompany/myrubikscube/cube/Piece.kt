package com.mycompany.myrubikscube.cube

import com.mycompany.myrubikscube.Log

class Piece(val type: PieceType) {

    enum class PieceType {
        CORNER,
        CENTER,
        EDGE
    }

    val mSquares = ArrayList<Square>()

    constructor(center: Square) : this(PieceType.CENTER) {
        mSquares.add(center)
    }

    fun addSquare(sq: Square) {
        if (!mSquares.contains(sq)) {
            if ((type == PieceType.CENTER && mSquares.size >= 2) ||
                (type == PieceType.EDGE && mSquares.size >= 3) ||
                (type == PieceType.CORNER && mSquares.size >= 6)
            ) {
                Log.e("rubik-piece", "Too many squares for PieceType $type: current = ${mSquares.size}")
            }
            mSquares.add(sq)
        }
    }

    fun hasColor(color: Int): Boolean {
        for (sq in mSquares) {
            if (sq.color == color) {
                return true
            }
        }
        return false
    }

    fun getSquare(color: Int): Square? {
        for (sq in mSquares) {
            if (sq.color == color) {
                return sq
            }
        }
        return null
    }

    override fun toString(): String {
        if (mSquares.isEmpty()) return "EmptyPiece"
        val sb = StringBuilder(mSquares[0].colorName())
        for (i in 1 until mSquares.size) {
            sb.append("-").append(mSquares[i].colorName())
        }
        return sb.toString()
    }
}
