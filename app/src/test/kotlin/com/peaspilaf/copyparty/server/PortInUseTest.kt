package com.peaspilaf.copyparty.server

import com.google.common.truth.Truth.assertThat
import com.peaspilaf.copyparty.server.ServerProbe.PortOwner
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread
import org.junit.After
import org.junit.Test

/**
 * J22 — the port belongs to us, or the app says so.
 *
 * What this pins, and why it exists: "the server is running" used to mean "something accepts TCP on
 * the port". A foreign process holding 3923 (another server app, a stray `python -m http.server`) was
 * therefore reported as Running with URLs, and every link the app offered was wrong.
 *
 * The contract these tests hold the implementation to:
 *
 *  * `ServerProbe.probe(port)` opens one HTTP/1.1 ``GET /`` against 127.0.0.1 and classifies the
 *    answer: [PortOwner.COPYPARTY] only when the response carries copyparty's own fingerprint,
 *    [PortOwner.SOMETHING_ELSE] when a listener answered with anything else, and
 *    [PortOwner.NOTHING] when no bytes came back (so a server that is still booting is never
 *    mistaken for a foreign one).
 *  * `ServerReadiness(port, lanAccess, ...).await(timeoutMs)` polls until it has the answer, keeps
 *    the 120 s readiness budget, requires `PythonBridge.isAlive()`, and reports
 *    [ServerStatus.Failed] with [ServerProbe.portInUseMessage] - never [ServerStatus.Running] with
 *    URLs - when the port is somebody else's.
 *
 * The copyparty sample below is a real response: copyparty 1.20.26 with the app's own global flags
 * (`http-only`, `no-crt`, `no-robots`, `q`, `no-voldump`) and a renamed server, captured from a live
 * server. The fingerprint has to survive that rename, because a user may call their phone anything.
 */
class PortInUseTest {
    private var foreign: FakeHttpServer? = null

    @After
    fun tearDown() {
        foreign?.close()
        foreign = null
    }

    // -- What answers on the port -------------------------------------------------------------

    @Test
    fun `an ordinary web server on the port is not copyparty`() {
        foreign = FakeHttpServer(rawResponse("HTTP/1.1 200 OK", listOf("Server: nginx/1.25.3", "Content-Length: 5"), "hello"))

        assertThat(ServerProbe.probe(foreign!!.port)).isEqualTo(PortOwner.SOMETHING_ELSE)
    }

    @Test
    fun `the page real copyparty serves is recognised as copyparty`() {
        foreign = FakeHttpServer(REAL_COPYPARTY_RESPONSE)

        assertThat(ServerProbe.probe(foreign!!.port)).isEqualTo(PortOwner.COPYPARTY)
    }

    @Test
    fun `nothing listening on the port is not a server at all`() {
        val port = freePort()

        assertThat(ServerProbe.probe(port, timeoutMs = 1_000)).isEqualTo(PortOwner.NOTHING)
    }

    @Test
    fun `a silent listener is not yet a foreign server`() {
        // A port that accepts but says nothing can be a server that is still booting. Treating it as
        // "used by another app" would fail a start that is merely slow.
        foreign = FakeHttpServer(ByteArray(0))

        assertThat(ServerProbe.probe(foreign!!.port, timeoutMs = 1_000)).isEqualTo(PortOwner.NOTHING)
    }

    @Test
    fun `the port in use message names the port and tells the user what to do`() {
        assertThat(ServerProbe.portInUseMessage(3923))
            .isEqualTo("Port 3923 is used by another app. Choose another port in Settings.")
    }

    // -- What the app reports -----------------------------------------------------------------

    @Test
    fun `a foreign listener on the port is reported as a failure, not as running`() {
        foreign = FakeHttpServer(
            rawResponse("HTTP/1.1 200 OK", listOf("Server: nginx/1.25.3", "Content-Length: 9"), "not us :("),
        )

        val status = ServerReadiness(foreign!!.port, lanAccess = false, isAlive = { true })
            .await(timeoutMs = 10_000)

        assertThat(status).isInstanceOf(ServerStatus.Failed::class.java)
        assertThat((status as ServerStatus.Failed).message)
            .isEqualTo("Port ${foreign!!.port} is used by another app. Choose another port in Settings.")
        assertThat(status).isNotInstanceOf(ServerStatus.Running::class.java)
    }

    @Test
    fun `running is only reported once copyparty itself answers`() {
        foreign = FakeHttpServer(REAL_COPYPARTY_RESPONSE)

        val status = ServerReadiness(foreign!!.port, lanAccess = false, isAlive = { true })
            .await(timeoutMs = 10_000)

        assertThat(status).isInstanceOf(ServerStatus.Running::class.java)
        assertThat((status as ServerStatus.Running).port).isEqualTo(foreign!!.port)
        assertThat(status.urls).contains("http://127.0.0.1:${foreign!!.port}")
    }

    @Test
    fun `a server that has not answered yet is not reported as running`() {
        var now = 0L
        val answers = ArrayDeque(listOf(PortOwner.NOTHING, PortOwner.NOTHING, PortOwner.NOTHING))

        val status = ServerReadiness(
            port = 3923,
            lanAccess = false,
            isAlive = { true },
            probe = { answers.removeFirstOrNull() ?: PortOwner.NOTHING },
            clock = { now },
            sleep = { now += 200 },
        ).await(timeoutMs = 1_000)

        assertThat(status).isInstanceOf(ServerStatus.Failed::class.java)
        assertThat((status as ServerStatus.Failed).message).contains("3923")
    }

    @Test
    fun `a copyparty that answers late is still reported as running`() {
        var now = 0L
        val answers = ArrayDeque(
            listOf(PortOwner.NOTHING, PortOwner.NOTHING, PortOwner.COPYPARTY),
        )

        val status = ServerReadiness(
            port = 3923,
            lanAccess = true,
            isAlive = { true },
            probe = { answers.removeFirstOrNull() ?: PortOwner.COPYPARTY },
            clock = { now },
            sleep = { now += 150 },
        ).await(timeoutMs = 120_000)

        assertThat(status).isInstanceOf(ServerStatus.Running::class.java)
        assertThat((status as ServerStatus.Running).port).isEqualTo(3923)
        assertThat(status.urls).contains("http://127.0.0.1:3923")
    }

    @Test
    fun `a crashed copyparty is not worth waiting two minutes for`() {
        val status = ServerReadiness(
            port = 3923,
            lanAccess = false,
            isAlive = { false },
            probe = { PortOwner.NOTHING },
            clock = { 0L },
            sleep = { throw AssertionError("a dead interpreter must not be polled for 120 seconds") },
        ).await(timeoutMs = 120_000)

        assertThat(status).isInstanceOf(ServerStatus.Failed::class.java)
    }

    private fun freePort(): Int {
        val socket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        return try {
            socket.localPort
        } finally {
            socket.close()
        }
    }
}

/** A tiny HTTP server that answers every request with the same bytes, so a port can be "taken". */
private class FakeHttpServer(private val response: ByteArray) {
    private val serverSocket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))

    val port: Int get() = serverSocket.localPort

    private val thread = thread(isDaemon = true, name = "fake-http-$port") {
        while (!serverSocket.isClosed) {
            // Only a closed listening socket ends the loop; a client that hangs up must not stop the
            // fake server, because the readiness check may probe more than once.
            val socket = try {
                serverSocket.accept()
            } catch (_: Exception) {
                return@thread
            }
            runCatching {
                socket.use {
                    it.soTimeout = 2_000
                    runCatching { it.getInputStream().read(ByteArray(4_096)) }
                    if (response.isNotEmpty()) {
                        it.getOutputStream().apply {
                            write(response)
                            flush()
                        }
                    }
                }
            }
        }
    }

    fun close() {
        runCatching { serverSocket.close() }
        thread.join(2_000)
    }
}

private val REAL_COPYPARTY_RESPONSE: ByteArray = rawResponse(
    statusLine = "HTTP/1.1 200 OK",
    headers = listOf(
        "Content-Length: 2814",
        "Connection: Close",
        "Cache-Control: no-store, max-age=0",
        "Content-Type: text/html; charset=utf-8",
        "X-Content-Type-Options: nosniff",
        "X-Robots-Tag: noindex, nofollow",
        "Vary: Origin, PW, Cookie",
        "Content-Security-Policy: script-src 'unsafe-eval' 'nonce-MTo+okOYeEK/5bXM8tNgBw=='; worker-src 'self'",
    ),
    body = """
        <!DOCTYPE html>
        <html lang="en" id="ht_spl">

        <head>
        	<meta charset="utf-8">
        	<title>copyparty @ my phone</title>
        	<meta name="theme-color" content="#333333">
        	<link rel="stylesheet" media="screen" href="/.cpr/w/splash.css?_=50Si">
        	<link rel="stylesheet" media="screen" href="/.cpr/w/ui.css?_=50Si">
        <meta name="robots" content="noindex, nofollow">

        </head>
        <body>
        	<div id="wrap">
        		<a id="a" href="/?h&re=1" class="af">refresh</a>
        	</div>
        	<script src="/.cpr/w/util.js?_=50Si"></script>
        </body>
        </html>
    """.trimIndent(),
)

private fun rawResponse(statusLine: String, headers: List<String>, body: String): ByteArray =
    (listOf(statusLine) + headers + listOf("", body)).joinToString("\r\n").toByteArray(Charsets.UTF_8)
