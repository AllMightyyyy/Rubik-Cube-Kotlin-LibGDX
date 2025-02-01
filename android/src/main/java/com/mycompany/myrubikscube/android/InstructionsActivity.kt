package com.mycompany.myrubikscube.android

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.mycompany.myrubikscube.R

class InstructionsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_instructions)

        val backButton: Button = findViewById(R.id.buttonBack)
        backButton.setOnClickListener {
            finish()
        }
    }
}
