package com.mycompany.myrubikscube.android

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.util.AttributeSet
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.AppCompatButton
import com.mycompany.myrubikscube.R

class CubieButton : AppCompatButton {
    var face: Face? = null
    var position: Int = 0
    var colorOption: ColorOption? = null

    constructor(context: Context, face: Face, position: Int) : super(context) {
        this.face = face
        this.position = position
        init()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init()
    }

    private fun init() {
        // Set default appearance
        setBackgroundColor(context.getColor(R.color.gray))
        text = ""
        // Accessibility
        contentDescription = "Face: ${face?.name}, Position: $position"
    }
}
