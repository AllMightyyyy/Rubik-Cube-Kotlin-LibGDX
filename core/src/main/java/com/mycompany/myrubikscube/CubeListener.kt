package com.mycompany.myrubikscube

interface CubeListener {
    /**
     * Called when a single rotation has completed.
     */
    fun handleRotationCompleted()

    /**
     * Called when the cube wants to send a message (e.g., logging or status).
     */
    fun handleCubeMessage(msg: String)

    /**
     * Called when the cube is solved.
     */
    fun handleCubeSolved()
}
