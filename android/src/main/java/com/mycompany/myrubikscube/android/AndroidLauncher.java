package com.mycompany.myrubikscube.android;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.mycompany.myrubikscube.CubeApp;
import com.mycompany.myrubikscube.PlatformBridge;

public class AndroidLauncher extends AndroidApplication implements PlatformBridge {

    private static final String TAG = "AndroidLauncher";
    private static final String EXTRA_CUBE_STRING = "CUBE_STRING";
    private CubeApp cubeApp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();

        // Initialize CubeApp
        cubeApp = new CubeApp(this);
        initialize(cubeApp, config);

        // Delay processing the cube string until CubeApp is initialized
        new Handler().postDelayed(() -> {
            Intent intent = getIntent();
            if (intent != null && intent.hasExtra(EXTRA_CUBE_STRING)) {
                String cubeString = intent.getStringExtra(EXTRA_CUBE_STRING);
                Log.d(TAG, "Received Cube String: " + cubeString);
                cubeApp.onCubeScanned(cubeString); // This ensures cube is initialized before calling
            }
        }, 500); // Give time for CubeApp to fully initialize
    }


    @Override
    public void startColorInputActivity() {
        Intent intent = new Intent(this, ColorInputActivity.class);
        startActivity(intent);
    }


    /**
     * Receives the cube string from ColorInputActivity.
     */
    @Override
    public void sendCubeString(String cubeString) {
        cubeApp.onCubeScanned(cubeString);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.hasExtra(EXTRA_CUBE_STRING)) {
            String cubeString = intent.getStringExtra(EXTRA_CUBE_STRING);
            Log.d(TAG, "Received Cube String via onNewIntent: " + cubeString);
            cubeApp.onCubeScanned(cubeString);
        }
    }
}
