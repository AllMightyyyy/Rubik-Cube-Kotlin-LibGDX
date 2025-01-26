package com.mycompany.myrubikscube

import com.badlogic.gdx.*
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.*
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.math.collision.Ray
import com.badlogic.gdx.scenes.scene2d.*
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.badlogic.gdx.utils.Array as GdxArray
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.kotcrab.vis.ui.VisUI
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

// Explicitly import Color
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder

/**
 * A 3D Rubik's Cube (3x3), fully colorized.
 * - Click a cubie to see 4 arrow buttons (Left/Right/Up/Down).
 * - Pressing an arrow rotates the slice containing that cubie.
 * - Includes "Manual Scramble" and "Auto Scramble" and "Solve".
 *
 * "Solving" here is simply reversing the scramble moves, step by step.
 * This is *not* an official solver. It's a demonstration of layered rotation logic.
 */
class Main : ApplicationAdapter() {

    // 3D rendering
    private lateinit var modelBatch: ModelBatch
    private lateinit var environment: Environment
    private lateinit var camera: PerspectiveCamera
    private lateinit var cameraController: CameraInputController

    // Holds all 27 "cubies"
    private val cubeInstances = mutableListOf<Cubie>()

    // Stage/UI
    private lateinit var stage: Stage

    // Table that shows arrow buttons
    private lateinit var arrowTable: Table
    private var selectedCubie: Cubie? = null

    // For picking
    private val pickRay = Ray()
    private val tmpVector = Vector3()

    // Cubie arrangement: 3x3x3, each slightly less than 1.0 so there's a small gap
    private val cubieSize = 0.98f

    // For building the colored cubies
    private lateinit var modelBuilder: ModelBuilder

    // UI buttons for scramble/solve
    private lateinit var btnManualScramble: TextButton
    private lateinit var btnAutoScramble: TextButton
    private lateinit var btnSaveScramble: TextButton
    private lateinit var btnSolve: TextButton

    // Tracking the "scramble moves" and "solution moves"
    private val scrambleMoves = mutableListOf<Move>()  // what user or auto-scramble does
    private val solveMoves = mutableListOf<Move>()     // reversed from scramble, for "solving"

    // We allow an animation approach for rotating slices
    private var rotatingSlice: SliceRotation? = null
    private var accumulatedRotation = 0f

    // Game states
    private enum class GameState { IDLE, MANUAL_SCRAMBLE, SOLVING }
    private var gameState = GameState.IDLE

    // Keep track of all Models for proper disposal
    private val createdModels = mutableListOf<Model>()

    // Coordinate axes instance
    private lateinit var axesInstance: ModelInstance

    override fun create() {
        // Load default VisUI skin
        if (!VisUI.isLoaded()) {
            VisUI.load()
        }

        modelBatch = ModelBatch()
        environment = Environment().apply {
            set(ColorAttribute.createAmbientLight(0.8f, 0.8f, 0.8f, 1f))
            add(DirectionalLight().apply {
                set(Color(0.9f, 0.9f, 0.9f, 1f), -1f, -1f, -1f)
            })
        }

        // Camera setup
        camera = PerspectiveCamera(67f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat()).apply {
            position.set(3f, 3f, 3f)
            lookAt(0f, 0f, 0f)
            near = 0.1f
            far = 100f
            update()
        }
        cameraController = CameraInputController(camera)

        // Build coordinate axes
        modelBuilder = ModelBuilder()
        val axesModel = createCoordinateAxes(5f)
        createdModels.add(axesModel)
        axesInstance = ModelInstance(axesModel)

        // Build the Rubik's cube cubies
        createRubikCube()

        // Enable depth test
        Gdx.gl.glEnable(GL20.GL_CULL_FACE)
        Gdx.gl.glCullFace(GL20.GL_BACK)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)

        // UI stage
        stage = Stage(ExtendViewport(800f, 480f))
        val inputMux = InputMultiplexer()
        inputMux.addProcessor(stage)
        inputMux.addProcessor(cameraController)
        Gdx.input.inputProcessor = inputMux

        // Arrow Table (hidden by default)
        arrowTable = Table(VisUI.getSkin()).apply {
            setBackground("window-bg") // Make sure your skin has this
            isVisible = false
        }
        val btnLeft = TextButton("Left", VisUI.getSkin())
        val btnRight = TextButton("Right", VisUI.getSkin())
        val btnUp = TextButton("Up", VisUI.getSkin())
        val btnDown = TextButton("Down", VisUI.getSkin())

        arrowTable.add(btnUp).colspan(2).center()
        arrowTable.row()
        arrowTable.add(btnLeft).left()
        arrowTable.add(btnRight).right()
        arrowTable.row()
        arrowTable.add(btnDown).colspan(2).center()
        arrowTable.pack()

        // Center arrowTable on any touch
        stage.addListener(object : InputListener() {
            override fun touchDown(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int): Boolean {
                arrowTable.setPosition(
                    (stage.viewport.worldWidth - arrowTable.width) / 2f,
                    (stage.viewport.worldHeight - arrowTable.height) / 2f
                )
                return super.touchDown(event, x, y, pointer, button)
            }
        })
        stage.addActor(arrowTable)

        // Arrow button listeners
        btnLeft.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                rotateSelectedLayer(Direction.LEFT)
            }
        })
        btnRight.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                rotateSelectedLayer(Direction.RIGHT)
            }
        })
        btnUp.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                rotateSelectedLayer(Direction.UP)
            }
        })
        btnDown.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                rotateSelectedLayer(Direction.DOWN)
            }
        })

        // Top bar
        val topTable = Table(VisUI.getSkin())
        topTable.setFillParent(true)
        topTable.top().left()

        btnManualScramble = TextButton("Manual Scramble", VisUI.getSkin()).apply { pad(20f) }
        btnAutoScramble = TextButton("Auto Scramble", VisUI.getSkin()).apply { pad(20f) }
        btnSaveScramble = TextButton("Save", VisUI.getSkin()).apply { pad(20f) }
        btnSolve = TextButton("Solve", VisUI.getSkin()).apply { pad(20f) }

        topTable.add(btnManualScramble).pad(10f).minWidth(200f)
        topTable.add(btnAutoScramble).pad(10f).minWidth(200f)
        topTable.add(btnSaveScramble).pad(10f).minWidth(200f)
        topTable.add(btnSolve).pad(10f).minWidth(200f)
        stage.addActor(topTable)

        // Button actions
        btnManualScramble.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                scrambleMoves.clear()
                gameState = GameState.MANUAL_SCRAMBLE
                Gdx.app.log("Scramble", "Manual Scramble Mode Activated")
            }
        })
        btnAutoScramble.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                autoScramble()
            }
        })
        btnSaveScramble.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                if (gameState == GameState.MANUAL_SCRAMBLE) {
                    gameState = GameState.IDLE
                    Gdx.app.log("Scramble", "Manual Scramble Saved")
                }
            }
        })
        btnSolve.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                startSolving()
            }
        })
    }

    /**
     * Data class for each sub-cubie.
     */
    data class Cubie(
        val modelInstance: ModelInstance,
        var xIndex: Int,
        var yIndex: Int,
        var zIndex: Int,
        val modelSpaceBounds: BoundingBox
    )

    /**
     * Represents a single 90-degree rotation of a slice.
     * axisChar in {x,y,z}, layerIndex in {0,1,2}, angle in +90 or -90.
     */
    data class Move(val axisChar: Char, val layerIndex: Int, val angle: Float)

    /**
     * Struct for an in-progress rotation animation.
     */
    data class SliceRotation(val move: Move, val cubies: List<Cubie>)

    /**
     * Directions for the arrowTable buttons.
     */
    enum class Direction { LEFT, RIGHT, UP, DOWN }

    /**
     * Build the 3x3x3 Rubik's cube: each sub-cube is colorized with up to 3 faces.
     */
    private fun createRubikCube() {
        cubeInstances.clear()
        val halfSize = cubieSize / 2f

        for (x in 0..2) {
            for (y in 0..2) {
                for (z in 0..2) {
                    val model = buildColoredCubie(x, y, z)
                    createdModels.add(model)

                    val instance = ModelInstance(model)
                    val modelSpaceBounds = BoundingBox().apply {
                        set(Vector3(-halfSize, -halfSize, -halfSize), Vector3(halfSize, halfSize, halfSize))
                    }

                    // Position each cubie
                    val offsetX = (x - 1) * cubieSize
                    val offsetY = (y - 1) * cubieSize
                    val offsetZ = (z - 1) * cubieSize
                    instance.transform.setToTranslation(offsetX, offsetY, offsetZ)

                    cubeInstances.add(Cubie(instance, x, y, z, modelSpaceBounds))
                }
            }
        }
    }

    /**
     * Build a single sub-cube model. Outer faces get standard Rubik's colors; interior sides black.
     */
    private fun buildColoredCubie(x: Int, y: Int, z: Int): Model {
        modelBuilder.begin()

        // Rubik’s standard colors
        val upColor = Color.WHITE
        val downColor = Color.YELLOW
        val leftColor = Color.ORANGE
        val rightColor = Color.RED
        val frontColor = Color.GREEN
        val backColor = Color.BLUE
        val blackColor = Color(0f, 0f, 0f, 1f)

        val half = cubieSize / 2f
        var partIndex = 0

        // Helper to add one face
        fun addFace(
            matColor: Color,
            centerX: Float,
            centerY: Float,
            centerZ: Float,
            normalX: Float,
            normalY: Float,
            normalZ: Float
        ) {
            val uniquePartName = "face_${x}_${y}_${z}_$partIndex"
            partIndex++

            val material = Material(ColorAttribute.createDiffuse(matColor))
            val partBuilder = modelBuilder.part(
                uniquePartName,
                GL20.GL_TRIANGLES,
                (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong(),
                material
            )

            // Normal pointing outward
            val normal = Vector3(normalX, normalY, normalZ).nor()

            // Make two perpendicular axes orthogonal to 'normal'
            val axisA = Vector3()
            val axisB = Vector3()
            getPerpendiculars(normal, axisA, axisB)

            axisA.scl(half) // half the face in one direction
            axisB.scl(half) // half the face in the perpendicular direction

            // Define corners in a consistent CCW order
            val c1 = Vector3(centerX, centerY, centerZ).sub(axisA).add(axisB) // top-left
            val c2 = Vector3(centerX, centerY, centerZ).add(axisA).add(axisB) // top-right
            val c3 = Vector3(centerX, centerY, centerZ).add(axisA).sub(axisB) // bottom-right
            val c4 = Vector3(centerX, centerY, centerZ).sub(axisA).sub(axisB) // bottom-left

            // Correct vertex order for CCW winding
            partBuilder.rect(
                c1.x, c1.y, c1.z,
                c2.x, c2.y, c2.z,
                c3.x, c3.y, c3.z,
                c4.x, c4.y, c4.z,
                normal.x, normal.y, normal.z
            )
        }

        // Outer or black faces (remaining code remains the same)
        // Up (y=2)
        if (y == 2) addFace(upColor, 0f, +half, 0f, 0f, 1f, 0f)
        else        addFace(blackColor, 0f, +half, 0f, 0f, 1f, 0f)

        // Down (y=0)
        if (y == 0) addFace(downColor, 0f, -half, 0f, 0f, -1f, 0f)
        else        addFace(blackColor, 0f, -half, 0f, 0f, -1f, 0f)

        // Left (x=0)
        if (x == 0) addFace(leftColor, -half, 0f, 0f, -1f, 0f, 0f)
        else        addFace(blackColor, -half, 0f, 0f, -1f, 0f, 0f)

        // Right (x=2)
        if (x == 2) addFace(rightColor, +half, 0f, 0f, 1f, 0f, 0f)
        else        addFace(blackColor, +half, 0f, 0f, 1f, 0f, 0f)

        // Front (z=2)
        if (z == 2) addFace(frontColor, 0f, 0f, +half, 0f, 0f, 1f)
        else        addFace(blackColor, 0f, 0f, +half, 0f, 0f, 1f)

        // Back (z=0)
        if (z == 0) addFace(backColor, 0f, 0f, -half, 0f, 0f, -1f)
        else        addFace(blackColor, 0f, 0f, -half, 0f, 0f, -1f)

        return modelBuilder.end()
    }

    /**
     * Helper for perpendicular vectors.
     */
    private fun getPerpendiculars(normal: Vector3, outA: Vector3, outB: Vector3) {
        val worldUp = Vector3(0f, 1f, 0f)
        outA.set(worldUp).crs(normal)
        if (outA.len() < 0.0001f) {
            outA.set(1f, 0f, 0f).crs(normal)
        }
        outB.set(outA).crs(normal).nor()
        outA.nor()
    }

    /**
     * Create coordinate axes as lines: X=Red, Y=Green, Z=Blue.
     * Avoids "line(...)" convenience method in case older LibGDX doesn't have it.
     */
    private fun createCoordinateAxes(length: Float): Model {
        modelBuilder.begin()

        // One part for all axes, using GL_LINES
        val builder = modelBuilder.part(
            "axes",
            GL20.GL_LINES,
            (VertexAttributes.Usage.Position or VertexAttributes.Usage.ColorUnpacked).toLong(),
            Material()
        )

        // We'll create short line segments. Each line has 2 vertices + indices.

        // X axis in red
        val red = Color(1f, 0f, 0f, 1f)
        val vX1 = MeshPartBuilder.VertexInfo().setPos(0f, 0f, 0f).setCol(red)
        val vX2 = MeshPartBuilder.VertexInfo().setPos(length, 0f, 0f).setCol(red)
        val iX1 = builder.vertex(vX1)
        val iX2 = builder.vertex(vX2)
        builder.index(iX1, iX2)

        // Y axis in green
        val green = Color(0f, 1f, 0f, 1f)
        val vY1 = MeshPartBuilder.VertexInfo().setPos(0f, 0f, 0f).setCol(green)
        val vY2 = MeshPartBuilder.VertexInfo().setPos(0f, length, 0f).setCol(green)
        val iY1 = builder.vertex(vY1)
        val iY2 = builder.vertex(vY2)
        builder.index(iY1, iY2)

        // Z axis in blue
        val blue = Color(0f, 0f, 1f, 1f)
        val vZ1 = MeshPartBuilder.VertexInfo().setPos(0f, 0f, 0f).setCol(blue)
        val vZ2 = MeshPartBuilder.VertexInfo().setPos(0f, 0f, length).setCol(blue)
        val iZ1 = builder.vertex(vZ1)
        val iZ2 = builder.vertex(vZ2)
        builder.index(iZ1, iZ2)

        return modelBuilder.end()
    }

    override fun render() {
        // Clear screen
        Gdx.gl.glViewport(0, 0, Gdx.graphics.width, Gdx.graphics.height)
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        // Update camera
        cameraController.update()

        // Update any ongoing slice rotation
        updateSliceRotation(Gdx.graphics.deltaTime)

        // Draw 3D
        modelBatch.begin(camera)
        modelBatch.render(axesInstance, environment)
        for (cubie in cubeInstances) {
            modelBatch.render(cubie.modelInstance, environment)
        }
        modelBatch.end()

        // Check for picking
        if (Gdx.input.justTouched()) {
            handlePicking(Gdx.input.x, Gdx.input.y)
        }

        // If solving and not rotating, do next move
        if (gameState == GameState.SOLVING && rotatingSlice == null) {
            if (solveMoves.isNotEmpty()) {
                val next = solveMoves.removeAt(0)
                startSliceRotation(next)
            } else {
                gameState = GameState.IDLE
                Gdx.app.log("Solver", "Solving Completed")
            }
        }

        // Draw UI
        stage.act()
        stage.draw()
    }

    /**
     * Check if user clicked a cubie. If so, show arrow UI.
     */
    private fun handlePicking(screenX: Int, screenY: Int) {
        val ray = camera.getPickRay(screenX.toFloat(), screenY.toFloat())
        var closestDist = Float.MAX_VALUE
        var hitCubie: Cubie? = null

        for (c in cubeInstances) {
            // Copy model-space bounds to world-space
            val worldBounds = BoundingBox(c.modelSpaceBounds)
            worldBounds.mul(c.modelInstance.transform)

            if (Intersector.intersectRayBoundsFast(ray, worldBounds)) {
                c.modelInstance.transform.getTranslation(tmpVector)
                val dist = tmpVector.dst(camera.position)
                if (dist < closestDist) {
                    closestDist = dist
                    hitCubie = c
                }
            }
        }

        selectedCubie = hitCubie
        arrowTable.isVisible = (hitCubie != null)

        if (hitCubie != null) {
            Gdx.app.log("Pick", "Cubie selected at (${hitCubie.xIndex}, ${hitCubie.yIndex}, ${hitCubie.zIndex})")
        }
    }

    /**
     * Rotate the slice containing selectedCubie in the given direction.
     */
    private fun rotateSelectedLayer(direction: Direction) {
        if (selectedCubie == null || rotatingSlice != null) return
        val c = selectedCubie!!

        // Decide which axisChar & layerIndex (x/y/z, 0/1/2)
        val possibleLayers = mutableListOf<Char>()
        if (c.xIndex == 0 || c.xIndex == 2) possibleLayers.add('x')
        if (c.yIndex == 0 || c.yIndex == 2) possibleLayers.add('y')
        if (c.zIndex == 0 || c.zIndex == 2) possibleLayers.add('z')

        // If user clicked the center piece (index=1), pick the axis with largest offset from 1
        if (possibleLayers.isEmpty()) {
            val dx = abs(c.xIndex - 1)
            val dy = abs(c.yIndex - 1)
            val dz = abs(c.zIndex - 1)
            val best = listOf(dx to 'x', dy to 'y', dz to 'z').maxByOrNull { it.first }!!
            possibleLayers.add(best.second)
        }

        val axisChar = possibleLayers.first()
        val layerIndex = when (axisChar) {
            'x' -> c.xIndex
            'y' -> c.yIndex
            'z' -> c.zIndex
            else -> 1
        }

        val angle = when (direction) {
            Direction.LEFT  ->  90f
            Direction.RIGHT -> -90f
            Direction.UP    ->  90f
            Direction.DOWN  -> -90f
        }

        val move = Move(axisChar, layerIndex, angle)
        startSliceRotation(move)

        // If in manual scramble mode, record move
        if (gameState == GameState.MANUAL_SCRAMBLE) {
            scrambleMoves.add(move)
            Gdx.app.log("Scramble", "Move recorded: $move")
        }
    }

    /**
     * Begin the 90-degree slice rotation animation.
     */
    private fun startSliceRotation(move: Move) {
        val sliceCubies = cubeInstances.filter { c ->
            when (move.axisChar) {
                'x' -> c.xIndex == move.layerIndex
                'y' -> c.yIndex == move.layerIndex
                'z' -> c.zIndex == move.layerIndex
                else -> false
            }
        }
        rotatingSlice = SliceRotation(move, sliceCubies)
        accumulatedRotation = 0f
        Gdx.app.log("Rotation", "Starting rotation: $move")
    }

    /**
     * Animate until we reach ±90°, then finalize.
     */
    private fun updateSliceRotation(delta: Float) {
        val slice = rotatingSlice ?: return
        val speed = 180f // deg/sec
        val step = speed * delta

        val sign = if (slice.move.angle > 0f) 1 else -1
        val remain = abs(slice.move.angle) - abs(accumulatedRotation)
        val rotateThisFrame = min(step, remain)

        rotateSlice(slice.cubies, slice.move.axisChar, sign * rotateThisFrame)
        accumulatedRotation += rotateThisFrame

        if (abs(accumulatedRotation) >= 90f) {
            // Snap if there's a remainder
            val leftover = sign * (90f - (abs(accumulatedRotation) - rotateThisFrame))
            if (abs(leftover) > 0.0001f) {
                rotateSlice(slice.cubies, slice.move.axisChar, leftover)
            }
            finalizeSliceRotation(slice)
            rotatingSlice = null
            Gdx.app.log("Rotation", "Completed rotation: ${slice.move}")
        }
    }

    /**
     * Rotate the given cubies around axisChar by deg degrees about (0,0,0).
     */
    private fun rotateSlice(cubies: List<Cubie>, axisChar: Char, deg: Float) {
        val axis = when (axisChar) {
            'x' -> Vector3.X
            'y' -> Vector3.Y
            'z' -> Vector3.Z
            else -> Vector3.Y
        }
        for (c in cubies) {
            c.modelInstance.transform.rotate(axis, deg)
        }
    }

    /**
     * After a full 90° turn, update each cubie's xIndex/yIndex/zIndex.
     */
    private fun finalizeSliceRotation(slice: SliceRotation) {
        val (axisChar, layerIndex, angle) = slice.move

        for (c in slice.cubies) {
            val oldX = c.xIndex
            val oldY = c.yIndex
            val oldZ = c.zIndex
            when (axisChar) {
                'x' -> {
                    if (angle > 0) {
                        c.yIndex = oldZ
                        c.zIndex = 2 - oldY
                    } else {
                        c.yIndex = 2 - oldZ
                        c.zIndex = oldY
                    }
                }
                'y' -> {
                    if (angle > 0) {
                        c.xIndex = oldZ
                        c.zIndex = 2 - oldX
                    } else {
                        c.xIndex = 2 - oldZ
                        c.zIndex = oldX
                    }
                }
                'z' -> {
                    if (angle > 0) {
                        c.xIndex = oldY
                        c.yIndex = 2 - oldX
                    } else {
                        c.xIndex = 2 - oldY
                        c.yIndex = oldX
                    }
                }
            }
        }

        // Snap transforms to new position but keep the same orientation
        for (c in slice.cubies) {
            val offsetX = (c.xIndex - 1) * cubieSize
            val offsetY = (c.yIndex - 1) * cubieSize
            val offsetZ = (c.zIndex - 1) * cubieSize

            val rotation = Quaternion()
            c.modelInstance.transform.getRotation(rotation)
            c.modelInstance.transform.idt()
            c.modelInstance.transform.rotate(rotation)
            c.modelInstance.transform.setTranslation(offsetX, offsetY, offsetZ)
        }
    }

    /**
     * Auto-scramble with 20 random moves.
     */
    private fun autoScramble() {
        scrambleMoves.clear()
        val possibleAxes = listOf('x', 'y', 'z')
        repeat(20) {
            val axis = possibleAxes.random()
            val layerIndex = Random.nextInt(0, 3)
            val angle = if (Random.nextBoolean()) 90f else -90f
            scrambleMoves.add(Move(axis, layerIndex, angle))
        }
        solveMoves.clear()
        gameState = GameState.IDLE
        solveMoves.addAll(scrambleMoves)
        gameState = GameState.SOLVING
        Gdx.app.log("Scramble", "Auto Scramble Initiated with 20 Moves")
    }

    /**
     * Reverse scramble moves and animate them step by step.
     */
    private fun startSolving() {
        if (scrambleMoves.isEmpty() || rotatingSlice != null) return
        solveMoves.clear()
        for (i in scrambleMoves.indices.reversed()) {
            val m = scrambleMoves[i]
            solveMoves.add(Move(m.axisChar, m.layerIndex, -m.angle))
        }
        gameState = GameState.SOLVING
        Gdx.app.log("Solver", "Solving Initiated")
    }

    override fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
        camera.viewportWidth = width.toFloat()
        camera.viewportHeight = height.toFloat()
        camera.update()

        // Re-center arrow table
        arrowTable.setPosition(
            (width - arrowTable.width) / 2f,
            (height - arrowTable.height) / 2f
        )
    }

    override fun dispose() {
        modelBatch.dispose()
        stage.dispose()
        if (VisUI.isLoaded()) {
            VisUI.dispose()
        }
        // Dispose all created Models
        for (model in createdModels) {
            model.dispose()
        }
    }
}

/**
 * Data class for each sub-cubie.
 */
data class Cubie(
    val modelInstance: ModelInstance,
    var xIndex: Int,
    var yIndex: Int,
    var zIndex: Int,
    val modelSpaceBounds: BoundingBox
)

/**
 * Represents a single 90-degree rotation of a slice.
 */
data class Move(val axisChar: Char, val layerIndex: Int, val angle: Float)

/**
 * In-progress rotation animation data.
 */
data class SliceRotation(val move: Move, val cubies: List<Cubie>)

/**
 * Directions for arrow buttons.
 */
enum class Direction { LEFT, RIGHT, UP, DOWN }
