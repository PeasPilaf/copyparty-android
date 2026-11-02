package com.peaspilaf.copyparty.server

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Files
import org.junit.Test

/**
 * J25 — "why did the server not start?" has an answer on the phone.
 *
 * copyparty writes its own log to `<filesDir>/copyparty/copyparty.log` (the config the app renders
 * points `lo:` there). The log screen is only useful if it can show the end of that file: the last
 * lines are where a failed start explains itself.
 *
 * `serverLogTail(filesDir, maxLines)` is the whole server-side contract: it reads that one file, never
 * throws, and returns "" when there is nothing to show - so the screen has no error path of its own.
 */
class ServerLogTailTest {
    private val filesDir: File = Files.createTempDirectory("copyparty-log").toFile()
    private val logFile: File get() = File(filesDir, "copyparty/copyparty.log")

    private fun writeLog(lines: List<String>) {
        logFile.parentFile?.mkdirs()
        logFile.writeText(lines.joinToString("\n") + "\n")
    }

    @Test
    fun `a fresh install has no log and shows nothing instead of failing`() {
        assertThat(serverLogTail(filesDir)).isEmpty()
    }

    @Test
    fun `the end of the log is what the screen shows`() {
        writeLog((1..5).map { "line $it" })

        assertThat(serverLogTail(filesDir, maxLines = 3)).isEqualTo("line 3\nline 4\nline 5")
    }

    @Test
    fun `a short log is shown whole`() {
        writeLog(listOf("starting", "listening on 3923"))

        assertThat(serverLogTail(filesDir, maxLines = 200)).isEqualTo("starting\nlistening on 3923")
    }

    @Test
    fun `the default tail is the last two hundred lines of a long log`() {
        writeLog((1..500).map { "line $it" })

        val tail = serverLogTail(filesDir)

        assertThat(tail.lines()).hasSize(200)
        assertThat(tail.lines().first()).isEqualTo("line 301")
        assertThat(tail.lines().last()).isEqualTo("line 500")
    }

    @Test
    fun `an empty log is empty text`() {
        logFile.parentFile?.mkdirs()
        logFile.writeText("")

        assertThat(serverLogTail(filesDir)).isEmpty()
    }

    @Test
    fun `a log that cannot be read is reported as empty instead of crashing the screen`() {
        logFile.mkdirs()

        assertThat(serverLogTail(filesDir)).isEmpty()
    }

    @Test
    fun `a nonsense line count still returns the log instead of throwing`() {
        writeLog(listOf("only line"))

        assertThat(serverLogTail(filesDir, maxLines = 0)).isEmpty()
    }
}
