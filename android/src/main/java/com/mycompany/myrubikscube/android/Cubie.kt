// android/src/main/java/com/mycompany/myrubikscube/android/Cubie.kt
package com.mycompany.myrubikscube.android

data class Cubie(
    val face: Face,
    val position: Int, // 0-8 representing the 3x3 grid
    var color: ColorOption? = null
)
