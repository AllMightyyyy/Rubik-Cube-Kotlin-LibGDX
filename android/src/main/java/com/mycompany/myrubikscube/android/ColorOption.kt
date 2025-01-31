package com.mycompany.myrubikscube.android

import com.mycompany.myrubikscube.R

enum class ColorOption(val displayName: String, val colorResId: Int) {
    WHITE("White", R.color.white),    // UP face (U)
    RED("Red", R.color.red),          // RIGHT face (R)
    GREEN("Green", R.color.green),    // FRONT face (F)
    YELLOW("Yellow", R.color.yellow), // DOWN face (D)
    ORANGE("Orange", R.color.orange), // LEFT face (L)
    BLUE("Blue", R.color.blue);       // BACK face (B)

    fun getChar(): Char {
        return when (this) {
            WHITE -> 'U'
            RED -> 'R'
            GREEN -> 'F'
            YELLOW -> 'D'
            ORANGE -> 'L'
            BLUE -> 'B'
        }
    }
}
