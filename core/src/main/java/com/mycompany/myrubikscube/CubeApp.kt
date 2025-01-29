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
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.mycompany.myrubikscube.cube.RubiksCube3x3x3
import com.mycompany.myrubikscube.cube.Square
import com.mycompany.myrubikscube.graphics.CubeRenderer
import com.kotcrab.vis.ui.VisUI
import com.kotcrab.vis.ui.widget.VisTable
import com.kotcrab.vis.ui.widget.VisTextButton
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener

import com.mycompany.myrubikscube.cs.min2phase.Search

class CubeApp : ApplicationAdapter() {

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

        // (1) Initialize min2phase once
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

        // Use the 3x3x3 specialized cube
        cube = RubiksCube3x3x3()
        cube.setSpeed(1)  // Medium speed
        cube.setRenderer(Renderer())

        // Prepare VisUI
        if (!VisUI.isLoaded()) {
            VisUI.load()
        }
        stage = Stage(ScreenViewport())

        // Build a simple UI with Solve, Undo, Redo
        val rootTable = VisTable()
        rootTable.setFillParent(true)
        stage.addActor(rootTable)

        // Solve button
        val solveButton = VisTextButton("Solve")
        solveButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                // Now calls the new override in RubiksCube3x3x3
                cube.solve()
            }
        })

        // Undo button
        val undoButton = VisTextButton("Undo")
        undoButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                cube.undo()
            }
        })

        // Redo button
        val redoButton = VisTextButton("Redo")
        redoButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                cube.redo()
            }
        })

        // Layout the buttons horizontally
        rootTable.top().left().pad(10f)
        rootTable.add(solveButton).padRight(10f)
        rootTable.add(undoButton).padRight(10f)
        rootTable.add(redoButton)

        // Combine our Stage with the existing camera input
        val inputMultiplexer = InputMultiplexer(stage, InputHandler(cube, camera), cameraController)
        Gdx.input.inputProcessor = inputMultiplexer
    }

    override fun render() {
        Gdx.gl.glViewport(0, 0, Gdx.graphics.width, Gdx.graphics.height)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        cameraController.update()

        batch.begin(camera)
        cube.draw()
        batch.end()

        // Let the cube's animation progress
        cube.onNextFrame()

        // Draw UI
        stage.act()
        stage.draw()
    }

    override fun dispose() {
        batch.dispose()
        stage.dispose()
        if (VisUI.isLoaded()) {
            VisUI.dispose()
        }
        super.dispose()
    }

    // An internal renderer that uses the environment and the batch
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
