package com.mycompany.myrubikscube

import com.badlogic.gdx.Game
import com.mycompany.myrubikscube.cube.CubeListener

class CubeApp(
    val platformBridge: PlatformBridge? = null,
    private val initialCubeString: String? = null
) : Game(), CubeListener {

    lateinit var gameScreen: GameScreen

    override fun create() {
        // Initialize GameScreen with optional initialCubeString
        gameScreen = GameScreen(this, initialCubeString)

        // Set the initial screen to GameScreen
        setScreen(gameScreen)
    }

    override fun dispose() {
        gameScreen.dispose()
        super.dispose()
    }

    // Implement CubeListener methods if needed
    override fun handleRotationCompleted() {}
    override fun handleCubeMessage(msg: String) {}
    override fun handleCubeSolved() {}
    override fun onAlgorithmCompleted() {}
}
