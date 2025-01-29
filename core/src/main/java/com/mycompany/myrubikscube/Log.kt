package com.mycompany.myrubikscube

import com.badlogic.gdx.Gdx

object Log {
    fun w(tag: String, msg: String) {
        Gdx.app.log(tag, msg)
    }
    fun d(tag: String, msg: String) {
        Gdx.app.log(tag, msg)
    }
    fun e(tag: String, msg: String) {
        Gdx.app.log(tag, msg)
    }
}
