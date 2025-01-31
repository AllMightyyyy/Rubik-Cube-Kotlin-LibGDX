package com.mycompany.myrubikscube

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.video.VideoPlayer
import com.badlogic.gdx.video.VideoPlayerCreator
import com.kotcrab.vis.ui.VisUI
import com.kotcrab.vis.ui.widget.VisTextButton
import com.mycompany.myrubikscube.CubeApp

class PreGameScreen(private val game: CubeApp) : Screen {

    private val stage: Stage = Stage(ScreenViewport())
    private var videoPlayer: VideoPlayer? = null
    private var videoTexture: Texture? = null
    private var videoLoaded = false

    init {
        if (!VisUI.isLoaded()) {
            VisUI.load()
        }

        Gdx.input.inputProcessor = stage

        try {
            // ✅ Initialize the Video Player Correctly
            videoPlayer = VideoPlayerCreator.createVideoPlayer()
            videoPlayer?.setLooping(true) // Loop the video

            // Load and play the video
            val videoFile = Gdx.files.internal("video/background.mp4") // Change to .mp4 if .webm fails
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

        val rootTable = Table()
        rootTable.setFillParent(true)
        stage.addActor(rootTable)

        // Transparent background for buttons
        rootTable.defaults().pad(15f).center()

        // Create custom button style using VisUI skin
        val buttonStyle = createCustomButtonStyle()

        // Play Game Button
        val playButton = VisTextButton("▶ Play Game", buttonStyle).apply {
            pad(10f)
            addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    game.setScreen(game.gameScreen)
                }
            })
        }

        // Scan Cube Button
        val scanButton = VisTextButton("📸 Scan Cube", buttonStyle).apply {
            pad(10f)
            addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    game.platformBridge?.startColorInputActivity()
                }
            })
        }

        // Add Buttons to Table (Center Align)
        rootTable.add(playButton).width(280f).height(80f).row()
        rootTable.add(scanButton).width(280f).height(80f).padTop(20f)
    }

    override fun show() {
        Gdx.app.log("PreGameScreen", "Showing PreGameScreen, resetting input processor.")
        Gdx.input.inputProcessor = stage
    }

    override fun render(delta: Float) {
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        // ✅ Update Video Player Before Rendering
        videoPlayer?.update()

        // ✅ Render the Video Background
        val batch = stage.batch
        batch.begin()
        videoTexture?.let {
            batch.draw(it, 0f, 0f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        }
        batch.end()

        // Draw UI
        stage.act(delta)
        stage.draw()
    }

    override fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
    }

    override fun pause() {}

    override fun resume() {}

    override fun hide() {
        videoPlayer?.stop() // Stop the video when leaving this screen
    }

    override fun dispose() {
        stage.dispose()
        videoPlayer?.dispose()
        if (VisUI.isLoaded()) VisUI.dispose()
    }

    // Function to create a custom VisUI button style
    private fun createCustomButtonStyle(): VisTextButton.VisTextButtonStyle {
        val skin = VisUI.getSkin()
        return VisTextButton.VisTextButtonStyle().apply {
            up = skin.getDrawable("button") // Default button
            down = skin.getDrawable("button-down") // Pressed effect
            over = skin.getDrawable("button-over") // Hover effect
            font = skin.getFont("default-font") // ✅ Use VisUI default font
            fontColor = Color.WHITE // Set text color
        }
    }
}
