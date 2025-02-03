package com.mycompany.myrubikscube.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ui.PlayerView
import com.mycompany.myrubikscube.R

class MainActivity : AppCompatActivity() {

    private lateinit var exoPlayer: ExoPlayer
    private lateinit var backgroundVideoView: PlayerView
    private lateinit var playGameButton: ImageButton
    private lateinit var scanCubeButton: ImageButton
    private lateinit var infoButton: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_menu)

        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )

        exoPlayer = ExoPlayer.Builder(this).build()
        backgroundVideoView = findViewById(R.id.backgroundVideoView)
        backgroundVideoView.player = exoPlayer

        val videoUri = Uri.parse("android.resource://${packageName}/${R.raw.bg_video}")
        val mediaItem = MediaItem.fromUri(videoUri)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.repeatMode = ExoPlayer.REPEAT_MODE_ALL
        exoPlayer.prepare()
        exoPlayer.play()

        playGameButton = findViewById(R.id.playGameButton)
        scanCubeButton = findViewById(R.id.scanCubeButton)
        infoButton = findViewById(R.id.infoButton)

        playGameButton.setOnClickListener {
            val intent = Intent(this, AndroidLauncher::class.java)
            startActivity(intent)
        }

        scanCubeButton.setOnClickListener {
            val intent = Intent(this, ColorInputActivity::class.java)
            startActivityForResult(intent, AndroidLauncher.REQUEST_CODE_SCAN)
        }

        infoButton.setOnClickListener {
            val intent = Intent(this, InstructionsActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == AndroidLauncher.REQUEST_CODE_SCAN && resultCode == RESULT_OK && data != null) {
            val cubeString = data.getStringExtra(AndroidLauncher.EXTRA_CUBE_STRING)
            val intent = Intent(this, AndroidLauncher::class.java).apply {
                putExtra(AndroidLauncher.EXTRA_CUBE_STRING, cubeString)
            }
            startActivity(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer.release()
    }
}
