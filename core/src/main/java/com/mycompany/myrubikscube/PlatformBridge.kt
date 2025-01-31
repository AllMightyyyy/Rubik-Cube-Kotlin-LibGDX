package com.mycompany.myrubikscube

interface PlatformBridge {
    fun sendCubeString(cubeString: String?)
    fun startColorInputActivity()

    fun showMessage(message: String)

    fun onCubeSolved()
    fun onAlgorithmCompleted()
    fun handleMainMenu()
    fun handleCubeSolved()
    fun handleCubeMessage(msg: String)
}
