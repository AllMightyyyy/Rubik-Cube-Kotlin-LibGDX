package com.mycompany.myrubikscube

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.video.VideoPlayer
import com.badlogic.gdx.video.VideoPlayerCreator

class PreGameScreen(private val game: CubeApp) : Screen {

    private val stage: Stage = Stage(ScreenViewport())
    private var videoPlayer: VideoPlayer? = null
    private var videoTexture: Texture? = null
    private var videoLoaded = false

    // ← Load your Neon UI skin from assets (adjust the path as needed)
    private val neonSkin: Skin = Skin(
        Gdx.files.internal("neon-ui/neon-ui.json"),
        TextureAtlas(Gdx.files.internal("neon-ui/neon-ui.atlas"))
    )

    init {
        // Set the input processor to the stage
        Gdx.input.inputProcessor = stage

        // Initialize and load the video player
        try {
            videoPlayer = VideoPlayerCreator.createVideoPlayer()
            videoPlayer?.setLooping(true)
            val videoFile = Gdx.files.internal("video/background.mp4") // adjust extension if needed
            if (videoPlayer?.load(videoFile) == true) {
                videoTexture = videoPlayer?.texture
                videoLoaded = true
                videoPlayer?.play()
                Gdx.app.log("PreGameScreen", "Video loaded successfully!")
            } else {
                Gdx.app.error("PreGameScreen", "Failed to load video!")
            }
        } catch (e: Exception) {
            Gdx.app.error("PreGameScreen", "Error initializing video player: ${e.message}")
        }

        // Create a root table to layout your UI components
        val rootTable = Table()
        rootTable.setFillParent(true)
        stage.addActor(rootTable)

        // Default padding for components (customize as needed)
        rootTable.defaults().pad(15f).center()

        // Create a custom button style if you want to tweak things,
        // or simply use the style defined in your neon skin.
        val buttonStyle = createCustomButtonStyle()

        // Create Play Game and Scan Cube buttons
        val playButton = TextButton("▶ Play Game", buttonStyle).apply {
            pad(10f)
            addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    game.setScreen(game.gameScreen)
                }
            })
        }

        val scanButton = TextButton("📸 Scan Cube", buttonStyle).apply {
            pad(10f)
            addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    game.platformBridge?.startColorInputActivity()
                }
            })
        }

        // Add buttons to the table
        rootTable.add(playButton).width(280f).height(80f).row()
        rootTable.add(scanButton).width(280f).height(80f).padTop(20f)
    }

    override fun show() {
        Gdx.app.log("PreGameScreen", "Showing PreGameScreen")
        Gdx.input.inputProcessor = stage
    }

    override fun render(delta: Float) {
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        // Update and render the video background
        videoPlayer?.update()
        val batch = stage.batch
        batch.begin()
        videoTexture?.let {
            batch.draw(it, 0f, 0f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        }
        batch.end()

        // Render UI stage
        stage.act(delta)
        stage.draw()
    }

    override fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
    }

    override fun pause() {}
    override fun resume() {}
    override fun hide() {
        videoPlayer?.stop() // Stop video when leaving the screen
    }

    override fun dispose() {
        stage.dispose()
        videoPlayer?.dispose()
        // No need to dispose neonSkin here if you plan to reuse it across screens
    }

    // Example: Create a custom button style that uses drawables from your neon skin.
    // You can also simply use neonSkin.get("default", TextButton.TextButtonStyle::class.java)
    private fun createCustomButtonStyle(): TextButton.TextButtonStyle {
        return TextButton.TextButtonStyle().apply {
            // Assuming your neon skin defines drawables with these names:
            up = neonSkin.getDrawable("button")
            down = neonSkin.getDrawable("button-down")
            over = neonSkin.getDrawable("button-over")
            font = neonSkin.getFont("default-font")
            fontColor = Color.WHITE
        }
    }
}
