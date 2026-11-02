package com.peaspilaf.copyparty.browse

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.net.URLDecoder
import java.nio.file.Files
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The network half of the native file screen, exercised against a real HTTP server (the JDK's, not a
 * mock): the JSON listing copyparty answers with becomes files and folders, a download writes the
 * exact bytes, an upload carries the `PW` header copyparty expects, and a refusal becomes the typed
 * "missing permission" the screen can act on instead of a silent no-op.
 *
 * The JSON shape here is the one real copyparty 1.20.26 returns for `?ls=json`: `dirs` entries have a
 * trailing slash in `href`, sizes are `sz` and timestamps are `ts`. File names arrive URL-encoded.
 */
class CopypartyClientTest {

    private lateinit var server: TestHttpServer
    private lateinit var client: CopypartyClient
    private lateinit var work: File

    /** Bytes the fake server received through PUT, keyed by request path. */
    private val uploaded = linkedMapOf<String, ByteArray>()

    /** Every `PW` header the fake server saw, so a test can prove the password was actually sent. */
    private val passwordHeaders = mutableListOf<String?>()

    private var baseUrl = ""

    private val listingJson = """
        {"dirs": [{"href": "sub/", "sz": 96, "ts": 1700000000}],
         "files": [{"href": "a.txt", "sz": 6, "ts": 1700000001},
                   {"href": "my%20file.txt", "sz": 7, "ts": 1700000002}]}
    """.trimIndent()

    private val subListingJson = """{"dirs": [], "files": [{"href": "b.txt", "sz": 3, "ts": 1700000003}]}"""

    private val secureListingJson = """{"dirs": [], "files": [{"href": "secret.txt", "sz": 11, "ts": 1700000004}]}"""

    @Before
    fun setUp() {
        work = Files.createTempDirectory("copyparty-client").toFile()
        server = TestHttpServer { request -> handle(request) }
        baseUrl = server.baseUrl
        client = CopypartyClient()
    }

    @After
    fun tearDown() {
        server.close()
        work.deleteRecursively()
    }

    @Test
    fun `a folder listing returns the files and folders it contains`() {
        val entries = client.list("$baseUrl/public", "")

        assertThat(entries).containsExactly(
            BrowseEntry("sub", isFolder = true, size = 96, modified = 1700000000L),
            BrowseEntry("a.txt", isFolder = false, size = 6, modified = 1700000001L),
            BrowseEntry("my file.txt", isFolder = false, size = 7, modified = 1700000002L),
        )
    }

    @Test
    fun `a listing follows the folder it was asked for`() {
        assertThat(client.list("$baseUrl/public", "sub")).containsExactly(
            BrowseEntry("b.txt", isFolder = false, size = 3, modified = 1700000003L),
        )
    }

    @Test
    fun `a download writes the exact bytes to the file it was given`() {
        val sink = File(work, "a.txt")

        val saved = client.download("$baseUrl/public", "a.txt", null, sink)

        assertThat(saved).isEqualTo(sink)
        assertThat(sink.readText()).isEqualTo("hello\n")
    }

    @Test
    fun `a file name with a space is requested from its real path`() {
        // The listing decodes "my%20file.txt"; the download has to encode it again instead of sending a
        // raw space into the request line.
        val sink = File(work, "my file.txt")

        client.download("$baseUrl/public", "my file.txt", null, sink)

        assertThat(sink.readText()).isEqualTo("spaced\n")
    }

    @Test
    fun `a protected listing without the password is reported as missing permission`() {
        val error = runCatching { client.list("$baseUrl/secure", "") }.exceptionOrNull()

        assertThat(error).isInstanceOf(CopypartyException.Unauthorized::class.java)
        assertThat(client.list("$baseUrl/secure", "", "hunter2")).containsExactly(
            BrowseEntry("secret.txt", isFolder = false, size = 11, modified = 1700000004L),
        )
    }

    @Test
    fun `a protected download without the password is reported as missing permission`() {
        val sink = File(work, "secret.txt")

        val error = runCatching { client.download("$baseUrl/secure", "secret.txt", null, sink) }.exceptionOrNull()

        assertThat(error).isInstanceOf(CopypartyException.Unauthorized::class.java)
        assertThat(sink.exists()).isFalse()
    }

    @Test
    fun `an upload sends the file with the password header`() {
        val file = File(work, "notes.txt").apply { writeText("uploaded bytes") }
        var sent = -1L
        var total = -1L

        client.upload("$baseUrl/upload", file, "notes.txt", "hunter2") { done, size ->
            sent = done
            total = size
        }

        assertThat(passwordHeaders).contains("hunter2")
        assertThat(uploaded["/upload/notes.txt"]).isEqualTo("uploaded bytes".toByteArray())
        assertThat(total).isEqualTo(file.length())
        assertThat(sent).isEqualTo(file.length())
    }

    @Test
    fun `a refused upload is reported as missing permission`() {
        val file = File(work, "notes.txt").apply { writeText("nope") }

        for (password in listOf(null, "wrong")) {
            val error = runCatching {
                client.upload("$baseUrl/upload", file, "notes.txt", password)
            }.exceptionOrNull()
            assertThat(error).isInstanceOf(CopypartyException.Unauthorized::class.java)
        }

        // copyparty answers 403 when the password is valid but the account may not upload.
        val forbidden = runCatching {
            client.upload("$baseUrl/forbidden", file, "notes.txt", "hunter2")
        }.exceptionOrNull()
        assertThat(forbidden).isInstanceOf(CopypartyException.Unauthorized::class.java)
    }

    @Test
    fun `an uploaded file reads back byte for byte`() {
        val file = File(work, "notes.txt").apply { writeBytes(ByteArray(64) { index -> (index * 7).toByte() }) }

        client.upload("$baseUrl/upload", file, "notes.txt", "hunter2")

        val sink = File(work, "read-back.txt")
        client.download("$baseUrl/upload", "notes.txt", "hunter2", sink)
        assertThat(sink.readBytes()).isEqualTo(file.readBytes())
    }

    @Test
    fun `malformed json is reported as an error instead of crashing`() {
        val error = runCatching { client.list("$baseUrl/broken", "") }.exceptionOrNull()

        assertThat(error).isInstanceOf(CopypartyException.BadResponse::class.java)
    }

    @Test
    fun `an html page instead of a listing is reported as an error`() {
        // copyparty can answer with its HTML page (a proxy, a wrong volume, a login redirect). That is
        // not an empty folder, and the screen must be able to say so.
        val error = runCatching { client.list("$baseUrl/html", "") }.exceptionOrNull()

        assertThat(error).isInstanceOf(CopypartyException.BadResponse::class.java)
    }

    // -- the fake copyparty ----------------------------------------------------------------

    private fun handle(request: TestRequest): TestResponse {
        val path = URLDecoder.decode(request.path, "UTF-8")
        val password = request.header("PW")
        passwordHeaders += password

        if (request.method == "PUT") return handlePut(request, path, password)

        return when {
            path.trimEnd('/') == "/secure" ->
                if (password != "hunter2") reply(401, "password required") else reply(200, secureListingJson)
            path == "/secure/secret.txt" ->
                if (password != "hunter2") reply(401, "password required") else reply(200, "top secret\n", "text/plain")
            path.trimEnd('/') == "/broken" -> reply(200, """{"dirs": [""")
            path.trimEnd('/') == "/html" -> reply(200, "<!doctype html><html></html>", "text/html")
            path.trimEnd('/') == "/public" -> reply(200, listingJson)
            path.trimEnd('/') == "/public/sub" -> reply(200, subListingJson)
            path == "/public/my file.txt" -> reply(200, "spaced\n", "text/plain")
            path == "/public/a.txt" -> reply(200, "hello\n", "text/plain")
            uploaded.containsKey(path) -> replyBytes(200, uploaded.getValue(path))
            else -> reply(404, "not found")
        }
    }

    private fun handlePut(request: TestRequest, path: String, password: String?): TestResponse {
        if (path.startsWith("/forbidden/")) return reply(403, "forbidden")
        if (password != "hunter2") return reply(401, "password required")
        uploaded[path] = request.body
        return reply(201, "")
    }

    private fun reply(status: Int, body: String, contentType: String = "application/json"): TestResponse =
        replyBytes(status, body.toByteArray(Charsets.UTF_8), contentType)

    private fun replyBytes(
        status: Int,
        bytes: ByteArray,
        contentType: String = "application/octet-stream",
    ): TestResponse = TestResponse(status, bytes, contentType)
}
