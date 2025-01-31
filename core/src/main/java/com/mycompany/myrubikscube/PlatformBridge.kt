// core/src/main/java/com/mycompany/myrubikscube/PlatformBridge.kt
package com.mycompany.myrubikscube

interface PlatformBridge {
    fun sendCubeString(cubeString: String?)
    fun startColorInputActivity()
}
