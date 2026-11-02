package com.peaspilaf.copyparty.server

import java.io.File

/**
 * The tail of the log copyparty writes for this app (`<filesDir>/copyparty/copyparty.log`, the path
 * the rendered config points `lo:` at).
 *
 * The log screen is only useful if it can show the end of that file - the last lines are where a
 * failed start explains itself - so this function never throws: a missing, unreadable or empty log
 * is `""`, and the screen has no error path of its own.
 */
fun serverLogTail(filesDir: File, maxLines: Int = 200): String {
    if (maxLines <= 0) return ""
    val log = File(File(filesDir, "copyparty"), "copyparty.log")
    return runCatching {
        if (!log.isFile) return@runCatching ""
        log.readLines().takeLast(maxLines).joinToString("\n")
    }.getOrDefault("")
}
