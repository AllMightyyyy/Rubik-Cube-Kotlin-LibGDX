package com.mycompany.myrubikscube

import com.badlogic.gdx.Application
import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.InputEvent
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
        private const val TAG = "rubik-app"
    }

    private lateinit var cube: RubiksCube3x3x3
    private lateinit var batch: ModelBatch
    private lateinit var camera: PerspectiveCamera
    private lateinit var env: Environment
    private lateinit var cameraController: CameraInputController

    private lateinit var stage: Stage

    override fun create() {
        Gdx.app.logLevel = Application.LOG_DEBUG

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
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    cube.solve()
                }
            })
        }

        // "Scan" button
        val scanButton = VisTextButton("Scan").apply {
            addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    platformBridge?.startCameraScan()
                }
            })
        }

        // Undo/Redo
        val undoButton = VisTextButton("Undo").apply {
            addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    cube.undo()
                }
            })
        }
        val redoButton = VisTextButton("Redo").apply {
            addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    cube.redo()
                }
            })
        }

        rootTable.top().left().pad(10f)
        rootTable.add(solveButton).padRight(10f)
        rootTable.add(scanButton).padRight(10f)
        rootTable.add(undoButton).padRight(10f)
        rootTable.add(redoButton).padRight(10f)

        val multiplexer = InputMultiplexer(stage, InputHandler(cube, camera), cameraController)
        Gdx.input.inputProcessor = multiplexer
    }

    override fun render() {
        Gdx.gl.glViewport(0, 0, Gdx.graphics.width, Gdx.graphics.height)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        cameraController.update()
        batch.begin(camera)
        cube.draw()  // 1) Draw the cube (either static or partial-rotation)
        batch.end()

        cube.onNextFrame()  // 2) Let the animation proceed

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
     * Called from Android once scanning is done (with a 54 char layout).
     */
    fun onCubeScanned(scannedCubeString: String) {
        // 1) Cancel any in-progress solver
        cube.cancelSolving()
        // If you had a randomizing animation, also do: cube.stopRandomize()

        // 2) Apply the scanned layout to the cube squares
        setCubeColorsFromScan(scannedCubeString)

        // 3) Automatically solve the newly-updated layout
        //    which will show each step in the on-screen 3D cube:
        if (cube.getState() == RubiksCube.CubeState.IDLE) {
            cube.solve()
        } else {
            // fallback if for some reason it wasn't idle:
            Gdx.app.postRunnable { cube.solve() }
        }
    }

    /**
     * Convert scanned string -> color assignments -> put on 3D cube squares
     */
    private fun setCubeColorsFromScan(scannedColors: String) {
        if (scannedColors.length != 54) {
            Log.e(TAG, "Scanned color string not 54 chars? => $scannedColors")
            return
        }
        // min2phase standard order: U(0..8), R(9..17), F(18..26), D(27..35), L(36..44), B(45..53)
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
        }
        for (i in 0 until 9) {
            val c = scannedColors[9 + i]
            cube.mRightSquares[i].color = charToColor[c] ?: Cube.Color_GRAY
        }
        for (i in 0 until 9) {
            val c = scannedColors[18 + i]
            cube.mFrontSquares[i].color = charToColor[c] ?: Cube.Color_GRAY
        }
        for (i in 0 until 9) {
            val c = scannedColors[27 + i]
            cube.mBottomSquares[i].color = charToColor[c] ?: Cube.Color_GRAY
        }
        for (i in 0 until 9) {
            val c = scannedColors[36 + i]
            cube.mLeftSquares[i].color = charToColor[c] ?: Cube.Color_GRAY
        }
        for (i in 0 until 9) {
            val c = scannedColors[45 + i]
            cube.mBackSquares[i].color = charToColor[c] ?: Cube.Color_GRAY
        }
    }

    /**
     * Simple renderer that sets rotation transform on squares
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
