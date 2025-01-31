package com.mycompany.myrubikscube.android

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.mycompany.myrubikscube.CubeApp
import com.mycompany.myrubikscube.PlatformBridge

class AndroidLauncher : AndroidApplication(), PlatformBridge {

    companion object {
        const val EXTRA_CUBE_STRING = "CUBE_STRING"
        const val REQUEST_CODE_SCAN = 1
    }

    private val TAG = "AndroidLauncher"
    private lateinit var cubeApp: CubeApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = AndroidApplicationConfiguration()

        val cubeString = intent.getStringExtra(EXTRA_CUBE_STRING)

        cubeApp = CubeApp(this, cubeString)
        initialize(cubeApp, config)
    }

    override fun startColorInputActivity() {
        val intent = Intent(this, ColorInputActivity::class.java)
        startActivityForResult(intent, REQUEST_CODE_SCAN)
    }

    /**
     * Receives the cube string from ColorInputActivity.
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_SCAN && resultCode == RESULT_OK && data != null) {
            if (data.hasExtra(EXTRA_CUBE_STRING)) {
                val cubeString = data.getStringExtra(EXTRA_CUBE_STRING)
                Log.d(TAG, "Received Cube String via onActivityResult: $cubeString")
                cubeApp.gameScreen.onCubeScanned(cubeString ?: "")
            }
        }
    }

    override fun sendCubeString(cubeString: String?) {
        cubeApp.gameScreen.onCubeScanned(cubeString ?: "")
    }

    override fun showMessage(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCubeSolved() {
        runOnUiThread {
            Toast.makeText(this, "Cube Solved!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun handleCubeMessage(msg: String) {
        showMessage(msg)
    }

    override fun handleCubeSolved() {
        onCubeSolved()
    }

    override fun onAlgorithmCompleted() {
        cubeApp.gameScreen.onAlgorithmCompleted()
    }

    override fun handleMainMenu() {
        finish()
    }
}
