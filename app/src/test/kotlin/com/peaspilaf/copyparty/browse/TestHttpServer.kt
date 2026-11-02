package com.peaspilaf.copyparty.browse

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

/** One HTTP request as [TestHttpServer] received it. */
internal class TestRequest(
    val method: String,
    val path: String,
    val query: String,
    private val headers: Map<String, String>,
    val body: ByteArray,
) {
    fun header(name: String): String? = headers[name.lowercase()]
}

/** One HTTP response [TestHttpServer] sends back. */
internal class TestResponse(
    val status: Int,
    val body: ByteArray,
    val contentType: String = "application/json",
)

/**
 * A tiny HTTP/1.1 server on a loopback port for JVM tests.
 *
 * Android unit tests compile against android.jar, where the JDK's com.sun.net.httpserver is not
 * available (CI proved that the hard way), so the browse client is exercised against this
 * hand-written socket server instead. It reads one request per connection, hands it to [handler],
 * writes the status line, Content-Length and body, then closes - exactly what the client does.
 */
internal class TestHttpServer(private val handler: (TestRequest) -> TestResponse) : AutoCloseable {
    private val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))

    @Volatile
    private var running = true

    val port: Int get() = socket.localPort
    val baseUrl: String get() = "http://127.0.0.1:$port"

    private val worker = thread(isDaemon = true, name = "test-http-server") {
        while (running) {
            val client = try {
                socket.accept()
            } catch (_: Exception) {
                break
            }
            try {
                serve(client)
            } catch (_: Exception) {
                // A single malformed test request must not stop the server.
            } finally {
                runCatching { client.close() }
            }
        }
    }

    private fun serve(client: Socket) {
        val input = BufferedInputStream(client.getInputStream())
        val request = readRequest(input) ?: return
        val response = handler(request)
        val head = buildString {
            append("HTTP/1.1 ").append(response.status).append(' ').append(reason(response.status)).append("\r\n")
            append("Content-Type: ").append(response.contentType).append("\r\n")
            append("Content-Length: ").append(response.body.size).append("\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }.toByteArray(StandardCharsets.US_ASCII)
        client.getOutputStream().apply {
            write(head)
            write(response.body)
            flush()
        }
    }

    private fun readRequest(input: BufferedInputStream): TestRequest? {
        val requestLine = readLine(input) ?: return null
        val parts = requestLine.split(' ')
        if (parts.size < 2) return null
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon > 0) {
                headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
            }
        }
        val length = headers["content-length"]?.toIntOrNull() ?: 0
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            val count = input.read(body, read, length - read)
            if (count < 0) break
            read += count
        }
        val target = parts[1]
        val question = target.indexOf('?')
        val path = if (question >= 0) target.substring(0, question) else target
        val query = if (question >= 0) target.substring(question + 1) else ""
        return TestRequest(parts[0], path, query, headers, body.copyOf(read))
    }

    private fun readLine(input: BufferedInputStream): String? {
        val out = ByteArrayOutputStream()
        var previous = -1
        while (true) {
            val byte = input.read()
            if (byte < 0) {
                return if (out.size() == 0) null else out.toString(StandardCharsets.US_ASCII.name())
            }
            if (previous == '\r'.code && byte == '\n'.code) {
                return out.toString(StandardCharsets.US_ASCII.name()).dropLast(1)
            }
            out.write(byte)
            previous = byte
        }
    }

    private fun reason(status: Int): String = when (status) {
        200 -> "OK"
        201 -> "Created"
        401 -> "Unauthorized"
        403 -> "Forbidden"
        404 -> "Not Found"
        else -> "Response"
    }

    override fun close() {
        running = false
        runCatching { socket.close() }
        worker.join(1_000)
    }
}
