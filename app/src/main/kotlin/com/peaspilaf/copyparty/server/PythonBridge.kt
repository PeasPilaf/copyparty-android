package com.peaspilaf.copyparty.server

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.io.File

/**
 * Kotlin side of the embedded CPython interpreter (Chaquopy). All calls block on Python work and
 * must happen off the main thread, except [ensureStarted], which is cheap after the first call.
 */
object PythonBridge {
    @Synchronized
    fun ensureStarted(context: Context) {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context.applicationContext))
        }
    }

    /** Returns null on success, or a human readable error message. */
    fun start(context: Context, config: File, log: File): String? {
        ensureStarted(context)
        val result = Python.getInstance()
            .getModule("cppandroid.boot")
            .callAttr("start", config.absolutePath, log.absolutePath)
            .toString()
        return result.ifBlank { null }
    }

    fun stop() {
        runCatching {
            Python.getInstance().getModule("cppandroid.boot").callAttr("stop")
        }
    }

    fun isAlive(): Boolean = runCatching {
        Python.getInstance().getModule("cppandroid.boot").callAttr("is_alive").toBoolean()
    }.getOrDefault(false)

    fun lastError(): String? = runCatching {
        Python.getInstance().getModule("cppandroid.boot").callAttr("last_error").toString()
    }.getOrNull()?.ifBlank { null }
}
