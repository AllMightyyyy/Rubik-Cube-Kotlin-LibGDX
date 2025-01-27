package com.mycompany.myrubikscube

import com.badlogic.gdx.Gdx

/**
 * Simplified logger that delegates to LibGDX's logging.
 */
object Log {

    @JvmStatic
    fun w(tag: String, msg: String) {
        Gdx.app.log(tag, msg)
    }

    @JvmStatic
    fun d(tag: String, msg: String) {
        Gdx.app.log(tag, msg)
    }

    @JvmStatic
    fun e(tag: String, msg: String) {
        Gdx.app.log(tag, msg)
    }
}
