package com.mycompany.myrubikscube

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.mycompany.myrubikscube.cube.CubeListener
import com.mycompany.myrubikscube.cube.RubiksCube3x3x3
import com.mycompany.myrubikscube.cs.min2phase.Search
import com.mycompany.myrubikscube.cube.Cube
import com.mycompany.myrubikscube.cube.RubiksCube

class GameScreen(
    private val game: CubeApp,
    private val initialCubeString: String? = null
) : Screen, CubeListener {

    private lateinit var cube: RubiksCube3x3x3
    private lateinit var batch: ModelBatch
    private lateinit var camera: PerspectiveCamera
    private lateinit var env: Environment
    private lateinit var cameraController: CameraInputController
    private lateinit var stage: Stage

    // UI buttons
    private lateinit var solveStepByStepButton: TextButton
    private lateinit var nextStepButton: TextButton
    private lateinit var prevStepButton: TextButton
    private lateinit var undoButton: TextButton
    private lateinit var redoButton: TextButton
    private lateinit var mainMenuButton: TextButton

    private var solutionSteps: List<com.mycompany.myrubikscube.cube.Rotation> = emptyList()
    private var currentStepIndex: Int = 0
    private var isStepByStepSolving: Boolean = false

    // Extra texture if needed (for example, for alternative button backgrounds)
    private lateinit var buttonTexture: Texture

    // Load your Neon UI skin from assets once.
    // (Make sure that neon-ui/neon-ui.json and neon-ui/neon-ui.atlas use high-res assets and fonts.)
    private val neonSkin: Skin = Skin(
        Gdx.files.internal("neon-ui/neon-ui.json"),
        TextureAtlas(Gdx.files.internal("neon-ui/neon-ui.atlas"))
    )

    // (Optional) Load a background image for the UI
    private val backgroundTexture: Texture = Texture(Gdx.files.internal("neon-ui/neon-bg.jpg"))

    // Create a SpriteBatch dedicated for drawing the background image.
    private val backgroundBatch = SpriteBatch()

    override fun show() {
        Gdx.app.logLevel = com.badlogic.gdx.Application.LOG_DEBUG

        Search.init()
        batch = ModelBatch()
        env = Environment().apply {
            set(ColorAttribute(ColorAttribute.AmbientLight, 0.6f, 0.6f, 0.6f, 1f))
            add(DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f))
        }

        // Set up the camera so that the cube and UI don’t compete for center stage.
        camera = PerspectiveCamera(67f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        camera.position.set(12f, 12f, 12f)  // adjust to your liking
        camera.lookAt(0f, 0f, 0f)
        camera.near = 1f
        camera.far = 300f
        camera.update()

        cameraController = CameraInputController(camera)

        cube = RubiksCube3x3x3().apply {
            setSpeed(1)
            setRenderer(Renderer())
            setListener(this@GameScreen)
        }

        initialCubeString?.let { scannedCubeString ->
            onCubeScanned(scannedCubeString)
        }

        // Create the UI stage with a ScreenViewport
        stage = Stage(ScreenViewport())

        // Do NOT add the background image as a stage actor,
        // since it will be drawn separately with backgroundBatch.
        // The stage will contain only the UI (buttons, tables, etc.)

        // Create a root table to layout the UI.
        val rootTable = Table()
        rootTable.setFillParent(true)
        rootTable.top().padTop(60f).padLeft(30f).padRight(30f)
        stage.addActor(rootTable)

        // Use a high-resolution font from your skin.
        // (If your skin’s default font is too small, consider generating a new BitmapFont via FreeType.)
        val buttonStyle: TextButton.TextButtonStyle =
            neonSkin.get("default", TextButton.TextButtonStyle::class.java).apply {
                fontColor = Color.WHITE
            }

        // Optionally, if you want to combine an external texture (e.g. button05.png)
        // with your skin’s style, create a drawable from it:
        buttonTexture = Texture(Gdx.files.internal("button05.png"))
        val buttonDrawable = TextureRegionDrawable(TextureRegion(buttonTexture))
        // You could assign this drawable to your style's "up" state if desired.
        // e.g.: buttonStyle.up = buttonDrawable

        // Create buttons with the neon skin style
        undoButton = TextButton("Undo", buttonStyle).apply {
            addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    cube.undo()
                }
            })
        }
        redoButton = TextButton("Redo", buttonStyle).apply {
            addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    cube.redo()
                }
            })
        }
        solveStepByStepButton = TextButton("Solve Step by Step", buttonStyle).apply {
            isDisabled = false
            addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    if (!isDisabled) {
                        startStepByStepSolving()
                    }
                }
            })
        }
        nextStepButton = TextButton("Next", buttonStyle).apply {
            isVisible = false
            addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    applyNextStep()
                }
            })
        }
        prevStepButton = TextButton("Previous", buttonStyle).apply {
            isVisible = false
            addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    applyPreviousStep()
                }
            })
        }
        mainMenuButton = TextButton("Main Menu", buttonStyle).apply {
            addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    game.platformBridge?.handleMainMenu()
                }
            })
        }

        // Layout the buttons in the table with extra padding and spacing.
        rootTable.add(undoButton).width(240f).height(100f).pad(10f)
        rootTable.add(redoButton).width(240f).height(100f).pad(10f)
        rootTable.row()
        rootTable.add(solveStepByStepButton).colspan(2).width(500f).height(100f).pad(10f)
        rootTable.row()
        rootTable.add(prevStepButton).width(240f).height(100f).pad(10f)
        rootTable.add(nextStepButton).width(240f).height(100f).pad(10f)
        rootTable.row()
        rootTable.add(mainMenuButton).colspan(2).width(500f).height(100f).pad(10f)

        // Combine input processors: UI stage, cube input, and camera controller.
        val multiplexer = InputMultiplexer(stage, InputHandler(cube, camera), cameraController)
        Gdx.input.inputProcessor = multiplexer
    }

    override fun render(delta: Float) {
        // Clear the screen with a clear color that complements your neon style.
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.15f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        // --- Draw the background image first using backgroundBatch ---
        backgroundBatch.begin()
        backgroundBatch.draw(
            backgroundTexture,
            0f,
            0f,
            Gdx.graphics.width.toFloat(),
            Gdx.graphics.height.toFloat()
        )
        backgroundBatch.end()

        // --- Draw the 3D cube ---
        cameraController.update()
        batch.begin(camera)
        cube.draw()
        batch.end()
        cube.onNextFrame()

        // --- Draw the UI stage on top ---
        stage.act(delta)
        stage.draw()
    }

    override fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
        camera.viewportWidth = width.toFloat()
        camera.viewportHeight = height.toFloat()
        camera.update()
    }

    override fun pause() {}
    override fun resume() {}
    override fun hide() {
        Gdx.app.log("GameScreen", "Hiding GameScreen")
        cube.cancelSolving()
        cube.setState(RubiksCube.CubeState.IDLE)
        Gdx.input.inputProcessor = null
    }

    override fun dispose() {
        Gdx.app.log("GameScreen", "Disposing GameScreen resources")
        batch.dispose()
        stage.dispose()
        backgroundBatch.dispose()
        buttonTexture.dispose()
        backgroundTexture.dispose()
        // Do not dispose neonSkin if it is shared between screens.
    }

    /**
     * Called when a scanned cube string is received.
     */
    fun onCubeScanned(scannedCubeString: String) {
        if (!::cube.isInitialized) {
            Gdx.app.error("GameScreen", "Cube is not initialized yet, skipping cube scan.")
            return
        }
        Gdx.app.log("GameScreen", "onCubeScanned called with string: $scannedCubeString")
        cube.cancelSolving()
        setCubeColorsFromScan(scannedCubeString)
        Gdx.app.postRunnable {
            solveStepByStepButton.isVisible = true
            solveStepByStepButton.isDisabled = false
        }
    }

    /**
     * Converts the scanned string into color assignments for the cube's squares.
     */
    private fun setCubeColorsFromScan(scannedColors: String) {
        if (scannedColors.length != 54) {
            Gdx.app.error("GameScreen", "Scanned color string not 54 chars? => $scannedColors")
            return
        }
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
            cube.mTopSquares[i].color = charToColor[c] ?: Cube.COLOR_GRAY
        }
        for (i in 0 until 9) {
            val c = scannedColors[9 + i]
            cube.mRightSquares[i].color = charToColor[c] ?: Cube.COLOR_GRAY
        }
        for (i in 0 until 9) {
            val c = scannedColors[18 + i]
            cube.mFrontSquares[i].color = charToColor[c] ?: Cube.COLOR_GRAY
        }
        for (i in 0 until 9) {
            val c = scannedColors[27 + i]
            cube.mBottomSquares[i].color = charToColor[c] ?: Cube.COLOR_GRAY
        }
        for (i in 0 until 9) {
            val c = scannedColors[36 + i]
            cube.mLeftSquares[i].color = charToColor[c] ?: Cube.COLOR_GRAY
        }
        for (i in 0 until 9) {
            val c = scannedColors[45 + i]
            cube.mBackSquares[i].color = charToColor[c] ?: Cube.COLOR_GRAY
        }
    }

    /**
     * Inner renderer class for drawing cube squares.
     */
    inner class Renderer : com.mycompany.myrubikscube.graphics.CubeRenderer {
        override fun drawSquare(square: com.mycompany.myrubikscube.cube.Square) {
            drawSquare(square, 0f, 0f, 0f, 0f)
        }
        override fun drawSquare(
            square: com.mycompany.myrubikscube.cube.Square,
            angle: Float,
            x: Float,
            y: Float,
            z: Float
        ) {
            square.modelInstance.transform.setToRotation(x, y, z, angle)
            batch.render(square.modelInstance, env)
        }
    }

    private fun startStepByStepSolving() {
        if (isStepByStepSolving) return

        sendMessage("Computing solution...")
        solveStepByStepButton.isDisabled = true

        Thread {
            val steps = cube.computeSolutionSteps()
            if (steps == null || steps.isEmpty()) {
                Gdx.app.postRunnable {
                    sendMessage("No solution steps available.")
                    solveStepByStepButton.isDisabled = false
                }
                return@Thread
            }
            solutionSteps = steps
            currentStepIndex = 0
            isStepByStepSolving = true

            cube.setState(RubiksCube.CubeState.SOLVING)
            cube.setStepByStepSolving(true)

            Gdx.app.postRunnable {
                nextStepButton.isVisible = true
                prevStepButton.isVisible = true
                solveStepByStepButton.isDisabled = false
            }
            Gdx.app.postRunnable { applyNextStep() }
        }.start()
    }

    private fun applyNextStep() {
        if (!isStepByStepSolving) return

        if (currentStepIndex >= solutionSteps.size) {
            sendMessage("Step-by-step solving completed!")
            isStepByStepSolving = false
            cube.setStepByStepSolving(false)
            nextStepButton.isVisible = false
            prevStepButton.isVisible = false
            cube.setState(RubiksCube.CubeState.IDLE)
            Gdx.app.postRunnable { solveStepByStepButton.isDisabled = false }
            return
        }

        val step = solutionSteps[currentStepIndex]
        val algo = com.mycompany.myrubikscube.cube.Algorithm().apply { addStep(step) }
        Gdx.app.postRunnable {
            try {
                cube.setAlgo(algo)
                currentStepIndex++
            } catch (e: IllegalStateException) {
                sendMessage("Error applying step: ${e.message}")
                isStepByStepSolving = false
                cube.setStepByStepSolving(false)
                nextStepButton.isVisible = false
                prevStepButton.isVisible = false
                Gdx.app.postRunnable { solveStepByStepButton.isDisabled = false }
            }
        }
    }

    private fun applyPreviousStep() {
        if (!isStepByStepSolving || currentStepIndex <= 0) {
            sendMessage("Already at the first step.")
            return
        }
        currentStepIndex--
        val step = solutionSteps[currentStepIndex]
        val inverseStep = com.mycompany.myrubikscube.cube.Rotation(
            step.axis,
            if (step.direction == com.mycompany.myrubikscube.graphics.Direction.CLOCKWISE)
                com.mycompany.myrubikscube.graphics.Direction.COUNTER_CLOCKWISE
            else
                com.mycompany.myrubikscube.graphics.Direction.CLOCKWISE,
            step.startFace,
            step.faceCount
        )
        val algo = com.mycompany.myrubikscube.cube.Algorithm().apply { addStep(inverseStep) }
        Gdx.app.postRunnable {
            try {
                cube.setAlgo(algo)
            } catch (e: IllegalStateException) {
                sendMessage("Error applying step: ${e.message}")
                isStepByStepSolving = false
                cube.setStepByStepSolving(false)
                nextStepButton.isVisible = false
                prevStepButton.isVisible = false
                Gdx.app.postRunnable { solveStepByStepButton.isDisabled = false }
            }
        }
    }

    /**
     * Utility method to show messages.
     */
    private fun sendMessage(message: String) {
        Gdx.app.log("GameScreen", message)
        game.platformBridge?.showMessage(message)
    }

    override fun handleRotationCompleted() { }
    override fun handleCubeMessage(msg: String) { sendMessage(msg) }
    override fun handleCubeSolved() {
        Gdx.app.postRunnable {
            sendMessage("Cube solved!")
            isStepByStepSolving = false
            cube.setStepByStepSolving(false)
            nextStepButton.isVisible = false
            prevStepButton.isVisible = false
            solveStepByStepButton.isVisible = true
            solveStepByStepButton.isDisabled = false
            cube.setState(RubiksCube.CubeState.IDLE)
        }
    }
    override fun onAlgorithmCompleted() { applyNextStep() }
}
