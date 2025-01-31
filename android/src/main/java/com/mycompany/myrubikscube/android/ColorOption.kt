package com.mycompany.myrubikscube.android

import com.mycompany.myrubikscube.R

enum class ColorOption(val displayName: String, val colorResId: Int) {
    WHITE("White", R.color.white),
    RED("Red", R.color.red),
    GREEN("Green", R.color.green),
    YELLOW("Yellow", R.color.yellow),
    ORANGE("Orange", R.color.orange),
    BLUE("Blue", R.color.blue);

    fun getChar(): Char {
        return when (this) {
            WHITE -> 'U' // Up
            RED -> 'R'    // Right
            GREEN -> 'F'  // Front
            YELLOW -> 'D' // Down
            ORANGE -> 'L' // Left
            BLUE -> 'B'   // Back
        }
    }
}
