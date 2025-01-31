package com.mycompany.myrubikscube.android

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.GridLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.mycompany.myrubikscube.R

class ColorInputActivity : AppCompatActivity() {

    private lateinit var faceLabelTextView: TextView
    private lateinit var prevButton: Button
    private lateinit var nextButton: Button
    private lateinit var currentFaceGrid: GridLayout
    private lateinit var generateSolveButton: Button
    private lateinit var debugCubeStringTextView: TextView

    private var currentFaceIndex: Int = 0
    private val faceOrder = listOf(Face.UP, Face.RIGHT, Face.FRONT, Face.DOWN, Face.LEFT, Face.BACK)
    private val cubieMap = HashMap<Face, Array<CubieButton>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_color_input)

        // Initialize UI Elements
        faceLabelTextView = findViewById(R.id.faceLabelTextView)
        prevButton = findViewById(R.id.prevButton)
        nextButton = findViewById(R.id.nextButton)
        currentFaceGrid = findViewById(R.id.currentFaceGrid)
        generateSolveButton = findViewById(R.id.generateSolveButton)
        debugCubeStringTextView = findViewById(R.id.debugCubeStringTextView)

        // Initialize Navigation Buttons
        prevButton.setOnClickListener {
            if (currentFaceIndex > 0) {
                currentFaceIndex--
                updateUIForCurrentFace()
            }
        }

        nextButton.setOnClickListener {
            if (currentFaceIndex < faceOrder.size - 1) {
                currentFaceIndex++
                updateUIForCurrentFace()
            }
        }

        // Initialize the first face
        updateUIForCurrentFace()

        // Set Generate and Solve button listener
        generateSolveButton.setOnClickListener {
            val cubeString = generateCubeString()
            if (validateCubeString(cubeString)) {
                sendCubeStringToCubeApp(cubeString)
            } else {
                Toast.makeText(this, "Invalid Cube Configuration. Please ensure all faces are properly colored.", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Updates the UI based on the current face index.
     */
    private fun updateUIForCurrentFace() {
        val face = faceOrder[currentFaceIndex]
        faceLabelTextView.text = "${face.name.capitalize()} Face"

        // Enable or disable navigation buttons
        prevButton.isEnabled = currentFaceIndex > 0
        nextButton.isEnabled = currentFaceIndex < faceOrder.size - 1

        // Populate the grid with cubies for the current face
        initializeFace(face, currentFaceGrid)
    }

    /**
     * Initializes the GridLayout for a specific face by adding 9 CubieButtons.
     *
     * @param face The face of the cube.
     * @param gridLayout The GridLayout corresponding to the face.
     */
    private fun initializeFace(face: Face, gridLayout: GridLayout) {
        // Remove any existing views to prevent duplication
        gridLayout.removeAllViews()

        // Retrieve existing cubies if any
        val existingCubies = cubieMap[face]
        val cubies = existingCubies ?: Array(9) { index ->
            CubieButton(this, face, index).apply {
                if (index == 4) {
                    // Center position
                    val centerColor = when (face) {
                        Face.UP -> ColorOption.WHITE
                        Face.RIGHT -> ColorOption.RED
                        Face.FRONT -> ColorOption.GREEN
                        Face.DOWN -> ColorOption.YELLOW
                        Face.LEFT -> ColorOption.ORANGE
                        Face.BACK -> ColorOption.BLUE
                    }
                    setBackgroundColor(getColor(centerColor.colorResId))
                    this.colorOption = centerColor
                    isEnabled = false
                    isClickable = false
                } else {
                    // Only set gray for non-center cubies
                    setBackgroundColor(getColor(R.color.gray))
                }
                text = ""
                // Set layout parameters
                val sizeInDp = 80
                val scale = resources.displayMetrics.density
                val sizeInPx = (sizeInDp * scale + 0.5f).toInt()

                // Ensure square shape by setting fixed size
                layoutParams = GridLayout.LayoutParams().apply {
                    width = sizeInPx
                    height = sizeInPx
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(8, 8, 8, 8)
                }
                setOnClickListener {
                    showColorPicker(this)
                }
                contentDescription = "Face: ${face.name}, Position: $index"
                Log.d("ColorInputActivity", "Created CubieButton for face: ${face.name}, position: $index")
            }
        }

        // Add cubies to the grid and log
        cubies.forEach { cubieButton ->
            gridLayout.addView(cubieButton)
            Log.d("ColorInputActivity", "Added CubieButton to GridLayout: ${cubieButton.contentDescription}")
        }
        cubieMap[face] = cubies

        // Log existing cubie states
        cubies.forEach { cubie ->
            Log.d("ColorInputActivity", "Cubie at face ${face.name}, position ${cubie.position} has colorOption: ${cubie.colorOption}")
        }

        // Check if all cubies are colored
        checkGenerateButtonState()
    }

    /**
     * Displays the ColorPickerDialog for a specific cubie.
     *
     * @param cubieButton The CubieButton that was clicked.
     */
    private fun showColorPicker(cubieButton: CubieButton) {
        val colorPicker = ColorPickerDialog(this, object : ColorPickerDialog.OnColorSelectedListener {
            override fun onColorSelected(colorOption: ColorOption) {
                cubieButton.setBackgroundColor(getColor(colorOption.colorResId))
                cubieButton.colorOption = colorOption
                Log.d("ColorInputActivity", "CubieButton at face ${faceOrder[currentFaceIndex].name}, position ${cubieButton.position} set to $colorOption")
                checkGenerateButtonState()
            }
        })
        colorPicker.show()
    }

    /**
     * Generates the 54-character cube string.
     *
     * @return The cube string.
     */
    private fun generateCubeString(): String {
        val sb = StringBuilder()

        // Define the order: U, R, F, D, L, B
        for (face in faceOrder) {
            val cubies = cubieMap[face]
            if (cubies != null) {
                for (cubie in cubies) {
                    val colorOption = cubie.colorOption
                    if (colorOption != null) {
                        sb.append(colorOption.getChar())
                    } else {
                        sb.append('X')
                    }
                }
            } else {
                sb.append("XXXXXXXXX")
            }
        }

        Log.d("ColorInputActivity", "Generated cube string: $sb")
        debugCubeStringTextView.text = "Cube String: $sb"

        return sb.toString()
    }

    /**
     * Validates the generated cube string.
     *
     * @param cubeString The cube string to validate.
     * @return True if valid, False otherwise.
     */
    private fun validateCubeString(cubeString: String): Boolean {
        if (cubeString.length != 54) {
            Log.e("ColorInputActivity", "Cube string length is not 54: $cubeString")
            return false
        }

        // Count colors
        val colorCount = HashMap<Char, Int>()
        for (c in cubeString.toCharArray()) {
            if (c == 'X') {
                Log.e("ColorInputActivity", "Cube string contains undefined color 'X'")
                return false
            }
            colorCount[c] = colorCount.getOrDefault(c, 0) + 1
        }

        // Valid Rubik's Cube has exactly 9 of each color
        for (colorOption in ColorOption.values()) {
            val expectedChar = colorOption.getChar()
            if (colorCount[expectedChar] != 9) {
                Log.e("ColorInputActivity", "Color $expectedChar has count ${colorCount[expectedChar]}, expected 9")
                return false
            }
        }

        Log.d("ColorInputActivity", "Cube string is valid.")
        return true
    }

    /**
     * Sends the cube string back to AndroidLauncher via setResult.
     */
    private fun sendCubeStringToCubeApp(cubeString: String) {
        val resultIntent = Intent().apply {
            putExtra("CUBE_STRING", cubeString)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    /**
     * Checks if all faces are fully colored to enable the generate button.
     */
    private fun checkGenerateButtonState() {
        for (face in faceOrder) {
            val cubies = cubieMap[face]
            if (cubies == null || cubies.any { it.colorOption == null }) {
                generateSolveButton.isEnabled = false
                return
            }
            val centerColor = when (face) {
                Face.UP -> ColorOption.WHITE
                Face.RIGHT -> ColorOption.RED
                Face.FRONT -> ColorOption.GREEN
                Face.DOWN -> ColorOption.YELLOW
                Face.LEFT -> ColorOption.ORANGE
                Face.BACK -> ColorOption.BLUE
            }
            if (cubies[4].colorOption != centerColor) {
                generateSolveButton.isEnabled = false
                return
            }
        }
        generateSolveButton.isEnabled = true
    }
}
