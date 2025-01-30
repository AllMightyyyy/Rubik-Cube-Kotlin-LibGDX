package com.mycompany.myrubikscube.android

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.mycompany.myrubikscube.CubeApp
import com.mycompany.myrubikscube.PlatformBridge

class AndroidLauncher : AndroidApplication(), PlatformBridge {

    companion object {
        private const val REQUEST_CODE_CAMERA = 1234
        var instance: AndroidLauncher? = null
    }

    private lateinit var coreApp: CubeApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        val config = AndroidApplicationConfiguration()
        coreApp = CubeApp(this)
        initialize(coreApp, config)
    }

    override fun startCameraScan() {
        // Launch camera intent
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        startActivityForResult(cameraIntent, REQUEST_CODE_CAMERA)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_CAMERA && resultCode == Activity.RESULT_OK) {
            // In real code, you’d extract the actual camera Bitmap, etc.

            // 1) For now, produce a FAKE scrambled cube string
            val scannedCubeString = fakeCubeColors()

            // 2) Post to the LibGDX (GL) thread to avoid concurrency issues
            Gdx.app.postRunnable {
                // Pass this scrambled string to the cube
                coreApp.onCubeScanned(scannedCubeString)
            }
        }
    }

    /**
     * Return a clearly scrambled 3×3 layout (not solved).
     * This is just an example. Min2phase must see a valid l ayout or it might say "Error".
     */
    private fun fakeCubeColors(): String {
        // Supposed 54-char scramble (U,R,F,D,L,B order):
        return "RRRRRRUDLBFFBUUBDDRBUFBUFLUDURFLLFLLBBFDDLUULDDDRFFBBL"
    }




}
