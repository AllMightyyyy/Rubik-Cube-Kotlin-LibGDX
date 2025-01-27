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
import com.mycompany.myrubikscube.Square
import com.mycompany.myrubikscube.CubeRenderer
import com.badlogic.gdx.graphics.g3d.ModelInstance

class CubeApp : ApplicationAdapter() {
    companion object {
        private const val tag = "rubik-app"
    }

    private lateinit var cube: RubiksCube
    private lateinit var batch: ModelBatch
    private lateinit var camera: PerspectiveCamera
    private lateinit var env: Environment
    private lateinit var cameraController: com.badlogic.gdx.graphics.g3d.utils.CameraInputController

    override fun create() {
        Gdx.app.logLevel = Application.LOG_DEBUG
        batch = ModelBatch()
        env = Environment().apply {
            set(ColorAttribute(ColorAttribute.AmbientLight, 0.4f, 0.4f, 0.4f, 1f))
            add(DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f))
        }

        camera = PerspectiveCamera(67f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat()).apply {
            position.set(10f, 10f, 10f)
            lookAt(0f, 0f, 0f)
            near = 1f
            far = 300f
            update()
        }

        cameraController = com.badlogic.gdx.graphics.g3d.utils.CameraInputController(camera)
        cube = RubiksCube(3)
        cube.setSpeed(1)
        cube.setRenderer(Renderer())

        Gdx.input.inputProcessor = InputMultiplexer(
            InputHandler(cube, camera),
            cameraController
        )
    }

    override fun render() {
        Gdx.gl.glViewport(0, 0, Gdx.graphics.width, Gdx.graphics.height)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        cameraController.update()
        batch.begin(camera)
        cube.draw()
        batch.end()

        cube.onNextFrame()
    }

    override fun dispose() {
        batch.dispose()
    }

    inner class Renderer : CubeRenderer {
        override fun drawSquare(square: Square) {
            drawSquare(square, 0f, 0f, 0f, 0f)
        }

        override fun drawSquare(square: Square, angleDegrees: Float, x: Float, y: Float, z: Float) {
            square.instance.transform.setToRotation(x, y, z, angleDegrees)
            batch.render(square.instance, env)
        }
    }
}
