package com.peaspilaf.copyparty

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.peaspilaf.copyparty.core.AppState
import com.peaspilaf.copyparty.core.ConfigRenderer
import com.peaspilaf.copyparty.core.Grant
import com.peaspilaf.copyparty.core.Permission
import com.peaspilaf.copyparty.core.ServerSettings
import com.peaspilaf.copyparty.core.Share
import com.peaspilaf.copyparty.server.PythonBridge
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The one test that cannot run on the JVM: it boots the real embedded CPython + copyparty inside the
 * Android app process, then talks HTTP to it. Everything above (config rendering, ACL mapping) is
 * covered by fast unit tests; this is the proof that the packaging works on a device.
 */
@RunWith(AndroidJUnit4::class)
class EmbeddedCopypartyTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private var port = 0
    private lateinit var workDir: File

    @Before
    fun setUp() {
        port = 39000 + (System.nanoTime() % 500).toInt()
        workDir = File(context.filesDir, "smoke-${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        runCatching { PythonBridge.stop() }
        workDir.deleteRecursively()
    }

    @Test
    fun servesAConfiguredFolderOverHttp() {
        val shareDir = File(workDir, "share").apply { mkdirs() }
        File(shareDir, "hello.txt").writeText("hello from copyparty\n")

        val state = AppState(
            shares = listOf(
                Share(
                    id = "smoke",
                    name = "Smoke",
                    urlPath = "smoke",
                    localPath = shareDir.absolutePath,
                    grants = listOf(Grant(Grant.EVERYONE, setOf(Permission.VIEW))),
                ),
            ),
            settings = ServerSettings(port = port, lanAccess = false),
        )
        val configFile = File(workDir, "copyparty.conf")
        configFile.writeText(ConfigRenderer.render(state, File(workDir, "copyparty.log").absolutePath))

        val error = PythonBridge.start(
            context,
            configFile,
            File(workDir, "copyparty.log"),
            File(workDir, "runtime"),
        )
        assertNull("copyparty failed to start: $error", error)

        val listing = getWithRetry("http://127.0.0.1:$port/smoke/")
        assertTrue("listing did not mention hello.txt:\n$listing", listing.contains("hello.txt"))

        assertEquals("hello from copyparty\n", getWithRetry("http://127.0.0.1:$port/smoke/hello.txt"))
    }

    private fun getWithRetry(url: String, timeoutMs: Long = 120_000): String {
        val deadline = System.currentTimeMillis() + timeoutMs
        var last: Throwable? = null
        while (System.currentTimeMillis() < deadline) {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 5_000
                connection.readTimeout = 5_000
                return connection.inputStream.bufferedReader().use { it.readText() }
            } catch (t: Throwable) {
                last = t
                Thread.sleep(500)
            }
        }
        throw AssertionError("could not GET $url: ${last}", last)
    }
}
