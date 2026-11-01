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
import com.peaspilaf.copyparty.core.User
import com.peaspilaf.copyparty.server.PythonBridge
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The end-to-end promise of the whole app, on a real device: the embedded CPython interpreter boots
 * the real copyparty from the config the app renders, a shared folder can be listed and downloaded
 * byte for byte, a folder granted only to an account stays closed to anonymous clients but opens
 * with its password and accepts an upload through it, and WebDAV answers on that same volume.
 */
@RunWith(AndroidJUnit4::class)
class EmbeddedCopypartyTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private var port = 0
    private lateinit var workDir: File

    @Before
    fun setUp() {
        port = 39000 + (System.currentTimeMillis() % 500).toInt()
        workDir = File(context.filesDir, "smoke-${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        runCatching { PythonBridge.stop() }
        workDir.deleteRecursively()
    }

    @Test
    fun servesPublicFoldersAndProtectsPrivateOnes() {
        val publicDir = File(workDir, "public").apply { mkdirs() }
        val privateDir = File(workDir, "private").apply { mkdirs() }
        val unsharedDir = File(workDir, "unshared").apply { mkdirs() }
        val publicBody = "hello from copyparty\n"
        val privateBody = "top secret\n"
        File(publicDir, "hello.txt").writeText(publicBody)
        File(privateDir, "secret.txt").writeText(privateBody)
        File(unsharedDir, "secret.txt").writeText(privateBody)

        val state = AppState(
            users = listOf(User("alice", "hunter2")),
            shares = listOf(
                Share(
                    id = "public",
                    name = "Public",
                    urlPath = "public",
                    localPath = publicDir.absolutePath,
                    grants = listOf(Grant(Grant.EVERYONE, setOf(Permission.VIEW))),
                ),
                Share(
                    id = "private",
                    name = "Private",
                    urlPath = "private",
                    localPath = privateDir.absolutePath,
                    grants = listOf(Grant("alice", setOf(Permission.VIEW, Permission.UPLOAD))),
                ),
                // A folder nobody has a grant for must not become readable by accident.
                Share(
                    id = "unshared",
                    name = "Unshared",
                    urlPath = "unshared",
                    localPath = unsharedDir.absolutePath,
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

        val base = "http://127.0.0.1:$port"

        // The first request doubles as the "is the server listening yet" probe.
        val listing = String(awaitPublicListing(base), Charsets.UTF_8)
        assertTrue("the public listing did not mention hello.txt:\n$listing", listing.contains("hello.txt"))

        assertArrayEquals(
            "the public download must return the file unchanged",
            publicBody.toByteArray(Charsets.UTF_8),
            getBytes("$base/public/hello.txt"),
        )

        assertTrue(
            "a folder with no grants must be closed to anonymous clients",
            status("$base/unshared/") in setOf(401, 403),
        )
        assertTrue(
            "a folder granted only to an account must be closed to anonymous clients",
            status("$base/private/") in setOf(401, 403),
        )

        val unlocked = String(getBytes("$base/private/?pw=hunter2"), Charsets.UTF_8)
        assertTrue("the unlocked private listing did not mention secret.txt:\n$unlocked", unlocked.contains("secret.txt"))

        val uploadedBody = "uploaded by the android test"
        val uploadStatus = put("$base/private/put-by-alice.txt", uploadedBody.toByteArray(Charsets.UTF_8), "hunter2")
        assertTrue("an upload with the account password was rejected with $uploadStatus", uploadStatus in setOf(200, 201, 204))

        assertArrayEquals(
            "the uploaded file must read back unchanged",
            uploadedBody.toByteArray(Charsets.UTF_8),
            getBytes("$base/private/put-by-alice.txt?pw=hunter2"),
        )

        assertEquals("WebDAV PROPFIND must answer 207", 207, propfindStatus("/private/?pw=hunter2"))
    }

    /** Polls until copyparty answers, so a slow emulator cannot turn a late boot into a failure. */
    private fun awaitPublicListing(base: String, timeoutMs: Long = 90_000): ByteArray {
        val deadline = System.currentTimeMillis() + timeoutMs
        var last: Throwable? = null
        while (System.currentTimeMillis() < deadline) {
            try {
                return getBytes("$base/public/")
            } catch (t: Throwable) {
                last = t
                Thread.sleep(250)
            }
        }
        val detail = PythonBridge.lastError()
        throw AssertionError("copyparty did not answer on $base/public/: $last ${detail.orEmpty()}", last)
    }

    private fun getBytes(url: String): ByteArray {
        val connection = open(url)
        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw AssertionError("GET $url returned $code")
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private fun status(url: String): Int {
        val connection = open(url)
        try {
            return connection.responseCode
        } finally {
            connection.disconnect()
        }
    }

    private fun put(url: String, body: ByteArray, password: String): Int {
        val connection = open(url, "PUT")
        connection.doOutput = true
        connection.setRequestProperty("PW", password)
        connection.setFixedLengthStreamingMode(body.size)
        try {
            connection.outputStream.use { it.write(body) }
            return connection.responseCode
        } finally {
            connection.disconnect()
        }
    }

    /** [HttpURLConnection] refuses non-standard methods such as PROPFIND, so speak plain HTTP/1.1. */
    private fun propfindStatus(path: String): Int {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", port), 10_000)
            socket.soTimeout = 10_000
            val request = "PROPFIND $path HTTP/1.1\r\n" +
                "Host: 127.0.0.1:$port\r\n" +
                "Depth: 0\r\n" +
                "Connection: close\r\n" +
                "\r\n"
            socket.getOutputStream().apply {
                write(request.toByteArray(Charsets.US_ASCII))
                flush()
            }
            val statusLine = socket.getInputStream().bufferedReader(Charsets.US_ASCII).readLine().orEmpty()
            val code = statusLine.split(' ').getOrNull(1)?.toIntOrNull()
            if (code == null) throw AssertionError("PROPFIND $path answered with an unreadable status line: '$statusLine'")
            return code
        }
    }

    private fun open(url: String, method: String = "GET"): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 10_000
            instanceFollowRedirects = false
        }
}
