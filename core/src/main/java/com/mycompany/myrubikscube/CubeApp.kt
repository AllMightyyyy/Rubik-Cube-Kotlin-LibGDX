package com.mycompany.myrubikscube

import com.badlogic.gdx.Game
import com.mycompany.myrubikscube.cube.CubeListener

class CubeApp(
    val platformBridge: PlatformBridge? = null,
    private val initialCubeString: String? = null
) : Game(), CubeListener {

    lateinit var gameScreen: GameScreen

    override fun create() {
        gameScreen = GameScreen(this, initialCubeString)
        setScreen(gameScreen)
    }

    override fun dispose() {
        gameScreen.dispose()
        super.dispose()
    }

    override fun handleRotationCompleted() {}
    override fun handleCubeMessage(msg: String) {}
    override fun handleCubeSolved() {}
    override fun onAlgorithmCompleted() {}
}
