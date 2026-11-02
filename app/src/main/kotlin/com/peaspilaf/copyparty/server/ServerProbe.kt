package com.peaspilaf.copyparty.server

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * "Is the port ours?" - the question the app used to answer with "something accepted a TCP
 * connection", which made a foreign process on 3923 look like a running server.
 *
 * One HTTP/1.1 ``GET /`` is sent to 127.0.0.1 and the answer is classified:
 *
 *  * [PortOwner.COPYPARTY] only when the response carries copyparty's own fingerprint. copyparty
 *    1.20.26 sends no `Server:` header, so the body is the reliable mark: the app name is always in
 *    the page (the `<title>`, the `/.cpr/` assets) even when the user renamed the server, as long as
 *    the rename only affects the displayed name and not the app identity.
 *  * [PortOwner.SOMETHING_ELSE] when bytes came back but they are not copyparty's - the port belongs
 *    to another app.
 *  * [PortOwner.NOTHING] when no bytes came back at all. A listener that accepts but has not
 *    answered yet is a server that is still booting, not a foreign one, so waiting is correct.
 */
object ServerProbe {
    enum class PortOwner { COPYPARTY, SOMETHING_ELSE, NOTHING }

    /** How many bytes of the response are worth keeping: the fingerprint is at the top of the page. */
    private const val MAX_RESPONSE_BYTES = 64 * 1024

    /**
     * Classifies whatever answers on [port]. Never throws: a refused connection, a timeout and a
     * reset connection are all "nothing answered".
     */
    fun probe(port: Int, timeoutMs: Int = 1_500): PortOwner {
        val response = runCatching { fetch(port, timeoutMs) }.getOrNull()
            ?: return PortOwner.NOTHING
        if (response.isEmpty()) return PortOwner.NOTHING
        return if (isCopyparty(response)) PortOwner.COPYPARTY else PortOwner.SOMETHING_ELSE
    }

    /** The one sentence the user sees when somebody else owns the port. */
    fun portInUseMessage(port: Int): String =
        "Port $port is used by another app. Choose another port in Settings."

    private fun fetch(port: Int, timeoutMs: Int): ByteArray {
        return Socket().use { socket ->
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress("127.0.0.1", port), timeoutMs)
            socket.soTimeout = timeoutMs
            val request = "GET / HTTP/1.1\r\n" +
                "Host: 127.0.0.1:$port\r\n" +
                "Connection: close\r\n" +
                "User-Agent: copyparty-android\r\n" +
                "\r\n"
            // A server that answers and hangs up before the whole request arrives is still worth
            // reading: the classification below only needs its bytes, not a completed request.
            runCatching {
                socket.getOutputStream().apply {
                    write(request.toByteArray(Charsets.US_ASCII))
                    flush()
                }
            }
            readAll(socket)
        }
    }

    /**
     * Reads until the server closes the connection (we asked it to), the timeout expires, or the
     * response gets implausibly large. Partially read bytes are kept: a slow server that has already
     * sent its headers is still worth classifying.
     */
    private fun readAll(socket: Socket): ByteArray {
        val response = ByteArrayOutputStream()
        val buffer = ByteArray(4_096)
        val input = socket.getInputStream()
        while (response.size() < MAX_RESPONSE_BYTES) {
            val read = try {
                input.read(buffer)
            } catch (_: SocketTimeoutException) {
                break
            } catch (_: IOException) {
                break
            }
            if (read <= 0) break
            response.write(buffer, 0, read)
        }
        return response.toByteArray()
    }

    /** copyparty's page always names itself; the `/.cpr/` asset paths corroborate it. */
    private fun isCopyparty(response: ByteArray): Boolean {
        val text = String(response, Charsets.UTF_8).lowercase()
        return text.contains("copyparty") || text.contains("/.cpr/")
    }
}
