package com.mycompany.myrubikscube.android

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import android.view.Window
import androidx.annotation.RequiresApi
import com.mycompany.myrubikscube.R

@RequiresApi(Build.VERSION_CODES.M)
class ColorPickerDialog(
    context: Context,
    private val listener: OnColorSelectedListener
) : Dialog(context) {

    interface OnColorSelectedListener {
        fun onColorSelected(colorOption: ColorOption)
    }

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_color_picker)

        val colorGrid = findViewById<GridLayout>(R.id.colorPickerGrid)

        ColorOption.values().forEach { colorOption ->
            val colorButton = Button(context).apply {
                setBackgroundColor(context.getColor(colorOption.colorResId))
                text = colorOption.displayName
                setTextColor(Color.WHITE)
                textSize = 16f
                // Set size
                val sizeInDp = 80
                val scale = context.resources.displayMetrics.density
                val sizeInPx = (sizeInDp * scale + 0.5f).toInt()
                layoutParams = GridLayout.LayoutParams().apply {
                    width = sizeInPx
                    height = sizeInPx
                    setMargins(8, 8, 8, 8)
                }
                setOnClickListener {
                    listener.onColorSelected(colorOption)
                    dismiss()
                }
                contentDescription = colorOption.displayName // For accessibility
            }

            colorGrid.addView(colorButton)
        }
    }
}
