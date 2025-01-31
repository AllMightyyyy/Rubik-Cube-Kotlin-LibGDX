package com.mycompany.myrubikscube.android

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.mycompany.myrubikscube.R

class MainActivity : AppCompatActivity() {

    private lateinit var scanButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Launch CubeApp (LibGDX game) first
        val intent = Intent(this, AndroidLauncher::class.java)
        startActivity(intent)
        finish() // Close MainActivity after launching CubeApp
    }
}
