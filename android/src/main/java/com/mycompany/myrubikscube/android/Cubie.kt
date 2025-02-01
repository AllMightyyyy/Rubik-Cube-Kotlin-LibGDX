package com.mycompany.myrubikscube.android

data class Cubie(
    val face: Face,
    val position: Int,
    var color: ColorOption? = null
)
