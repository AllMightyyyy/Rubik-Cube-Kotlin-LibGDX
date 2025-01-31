package com.mycompany.myrubikscube.cube

interface CubeListener {
    fun handleRotationCompleted()
    fun handleCubeMessage(msg: String)
    fun handleCubeSolved()
    fun onAlgorithmCompleted()
}
