// core/src/main/java/com/mycompany/myrubikscube/CubeApp.kt
package com.mycompany.myrubikscube

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.kotcrab.vis.ui.VisUI
import com.kotcrab.vis.ui.widget.VisTable
import com.kotcrab.vis.ui.widget.VisTextButton

import com.mycompany.myrubikscube.cs.min2phase.Search
import com.mycompany.myrubikscube.cube.RubiksCube3x3x3
import com.mycompany.myrubikscube.cube.RubiksCube
import com.mycompany.myrubikscube.cube.Cube
import com.mycompany.myrubikscube.cube.Square
import com.mycompany.myrubikscube.graphics.CubeRenderer

class CubeApp(
    private val platformBridge: PlatformBridge? = null
) : ApplicationAdapter() {

    companion object {
        private const val TAG = "CubeApp"
    }

    private lateinit var cube: RubiksCube3x3x3
    private lateinit var batch: ModelBatch
    private lateinit var camera: PerspectiveCamera
    private lateinit var env: Environment
    private lateinit var cameraController: CameraInputController

    private lateinit var stage: Stage

    override fun create() {
        Gdx.app.logLevel = com.badlogic.gdx.Application.LOG_DEBUG

        // Initialize min2phase only once
        Search.init()

        batch = ModelBatch()
        env = Environment().apply {
            set(ColorAttribute(ColorAttribute.AmbientLight, 0.4f, 0.4f, 0.4f, 1f))
            add(DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f))
        }

        camera = PerspectiveCamera(67f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        camera.position.set(10f, 10f, 10f)
        camera.lookAt(0f, 0f, 0f)
        camera.near = 1f
        camera.far = 300f
        camera.update()

        cameraController = CameraInputController(camera)

        // Use a specialized 3x3 cube with min2phase solver
        cube = RubiksCube3x3x3().apply {
            setSpeed(1)  // Medium speed for step-by-step
            setRenderer(Renderer())
        }

        // GUI setup
        if (!VisUI.isLoaded()) {
            VisUI.load()
        }
        stage = Stage(ScreenViewport())

        val rootTable = VisTable()
        rootTable.setFillParent(true)
        stage.addActor(rootTable)

        // Solve button
        val solveButton = VisTextButton("Solve").apply {
            addListener(object : ClickListener() {
                override fun clicked(event: com.badlogic.gdx.scenes.scene2d.InputEvent?, x: Float, y: Float) {
                    cube.solve()
                }
            })
        }

        val scanButton = VisTextButton("Scan Cube").apply {
            addListener(object : ClickListener() {
                override fun clicked(event: com.badlogic.gdx.scenes.scene2d.InputEvent?, x: Float, y: Float) {
                    platformBridge?.startColorInputActivity()
                }
            })
        }

        // Undo/Redo
        val undoButton = VisTextButton("Undo").apply {
            addListener(object : ClickListener() {
                override fun clicked(event: com.badlogic.gdx.scenes.scene2d.InputEvent?, x: Float, y: Float) {
                    cube.undo()
                }
            })
        }
        val redoButton = VisTextButton("Redo").apply {
            addListener(object : ClickListener() {
                override fun clicked(event: com.badlogic.gdx.scenes.scene2d.InputEvent?, x: Float, y: Float) {
                    cube.redo()
                }
            })
        }

        // Add buttons to the UI
        rootTable.top().left().pad(20f)
        rootTable.add(scanButton).padRight(10f)
        rootTable.add(solveButton).padRight(10f)
        rootTable.add(undoButton).padRight(10f)
        rootTable.add(redoButton).padRight(10f)

        val multiplexer = com.badlogic.gdx.InputMultiplexer(stage, InputHandler(cube, camera), cameraController)
        Gdx.input.inputProcessor = multiplexer
    }

    override fun render() {
        Gdx.gl.glViewport(0, 0, Gdx.graphics.width, Gdx.graphics.height)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        cameraController.update()
        batch.begin(camera)
        cube.draw()  // Draw the cube (either static or partial-rotation)
        batch.end()

        cube.onNextFrame()  // Let the animation proceed

        // Draw the stage UI
        stage.act()
        stage.draw()
    }

    override fun dispose() {
        batch.dispose()
        stage.dispose()
        if (VisUI.isLoaded()) VisUI.dispose()
        super.dispose()
    }

    /**
     * Called from Android once the cube string is received.
     *
     * @param scannedCubeString The 54-character cube string.
     */
    fun onCubeScanned(scannedCubeString: String) {
        if (!::cube.isInitialized) {
            Gdx.app.error(TAG, "Cube is not initialized yet, skipping cube scan.")
            return
        }

        Log.d(TAG, "onCubeScanned called with string: $scannedCubeString")
        cube.cancelSolving()
        setCubeColorsFromScan(scannedCubeString)

        Gdx.app.postRunnable {
            cube.draw() // Ensure rendering is updated
        }
    }



    /**
     * Converts the scanned string to color assignments and applies them to the cube's squares.
     *
     * @param scannedColors The 54-character cube string.
     */
    private fun setCubeColorsFromScan(scannedColors: String) {
        if (scannedColors.length != 54) {
            Gdx.app.error(TAG, "Scanned color string not 54 chars? => $scannedColors")
            return
        }
        // min2phase standard order: U, R, F, D, L, B
        val charToColor = mapOf(
            'U' to Cube.COLOR_TOP,
            'R' to Cube.COLOR_RIGHT,
            'F' to Cube.COLOR_FRONT,
            'D' to Cube.COLOR_BOTTOM,
            'L' to Cube.COLOR_LEFT,
            'B' to Cube.COLOR_BACK
        )

        for (i in 0 until 9) {
            val c = scannedColors[i]
            cube.mTopSquares[i].color = charToColor[c] ?: Cube.Color_GRAY
            Log.d(TAG, "Top square $i set to ${cube.mTopSquares[i].color}")
        }
        for (i in 0 until 9) {
            val c = scannedColors[9 + i]
            cube.mRightSquares[i].color = charToColor[c] ?: Cube.Color_GRAY
            Log.d(TAG, "Right square $i set to ${cube.mRightSquares[i].color}")
        }
        for (i in 0 until 9) {
            val c = scannedColors[18 + i]
            cube.mFrontSquares[i].color = charToColor[c] ?: Cube.Color_GRAY
            Log.d(TAG, "Front square $i set to ${cube.mFrontSquares[i].color}")
        }
        for (i in 0 until 9) {
            val c = scannedColors[27 + i]
            cube.mBottomSquares[i].color = charToColor[c] ?: Cube.Color_GRAY
            Log.d(TAG, "Bottom square $i set to ${cube.mBottomSquares[i].color}")
        }
        for (i in 0 until 9) {
            val c = scannedColors[36 + i]
            cube.mLeftSquares[i].color = charToColor[c] ?: Cube.Color_GRAY
            Log.d(TAG, "Left square $i set to ${cube.mLeftSquares[i].color}")
        }
        for (i in 0 until 9) {
            val c = scannedColors[45 + i]
            cube.mBackSquares[i].color = charToColor[c] ?: Cube.Color_GRAY
            Log.d(TAG, "Back square $i set to ${cube.mBackSquares[i].color}")
        }
    }


    /**
     * Renderer that applies rotation transformations to the cube's squares.
     */
    inner class Renderer : CubeRenderer {
        override fun drawSquare(square: Square) {
            drawSquare(square, 0f, 0f, 0f, 0f)
        }
        override fun drawSquare(square: Square, angle: Float, x: Float, y: Float, z: Float) {
            square.modelInstance.transform.setToRotation(x, y, z, angle)
            batch.render(square.modelInstance, env)
        }
    }
}
