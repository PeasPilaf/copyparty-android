package com.peaspilaf.copyparty.browse

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * The real copyparty, spoken to over plain `HttpURLConnection` (no new dependency, and the same
 * client the phone's own server answers).
 *
 * Three things are pinned by the coroutines-as-tests in `CopypartyClientTest`:
 *  * a listing is `GET <volume>/<path>?ls=json` and the answer is the JSON shape copyparty 1.20.26
 *    returns (`dirs`/`files`, `href`, `sz`, `ts`); anything else - malformed JSON, an HTML page, a
 *    login redirect - is a [CopypartyException.BadResponse], never an empty folder;
 *  * a password is the `PW` header on the request, for listing, download and upload alike;
 *  * 401 and 403 are the one [CopypartyException.Unauthorized] the screen can offer to fix.
 */
class CopypartyClient(
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 60_000,
) : CopypartyApi {

    override fun list(baseUrl: String, path: String, password: String?): List<BrowseEntry> {
        val url = endpoint(baseUrl, path, LISTING_QUERY)
        val body = requestBytes(url, "GET", password)
        return parseListing(String(body, Charsets.UTF_8), url)
    }

    override fun download(baseUrl: String, path: String, password: String?, sink: File): File {
        val url = endpoint(baseUrl, path, query = null)
        val connection = open(url, "GET", password)
        try {
            checkStatus(connection.responseCode, url)
            // Only touch the file system once the server has agreed to send the bytes, so a refusal
            // cannot leave an empty "downloaded" file behind.
            sink.parentFile?.mkdirs()
            connection.inputStream.use { input ->
                sink.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (error: CopypartyException) {
            throw error
        } catch (error: IOException) {
            throw CopypartyException.Unreachable("Could not download ${sink.name}: ${reason(error)}", error)
        } finally {
            connection.disconnect()
        }
        return sink
    }

    override fun upload(
        baseUrl: String,
        file: File,
        path: String,
        password: String?,
        onProgress: (Long, Long) -> Unit,
    ) {
        val url = endpoint(baseUrl, path, query = null)
        val connection = open(url, "PUT", password)
        try {
            val total = file.length()
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/octet-stream")
            connection.setFixedLengthStreamingMode(total)
            connection.outputStream.use { output ->
                file.inputStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var sent = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        sent += read
                        onProgress(sent, total)
                    }
                }
            }
            checkStatus(connection.responseCode, url)
            onProgress(total, total)
        } catch (error: CopypartyException) {
            throw error
        } catch (error: IOException) {
            throw CopypartyException.Unreachable("Could not upload ${file.name}: ${reason(error)}", error)
        } finally {
            connection.disconnect()
        }
    }

    // -- HTTP ---------------------------------------------------------------------------------

    private fun requestBytes(url: URL, method: String, password: String?): ByteArray {
        val connection = open(url, method, password)
        try {
            checkStatus(connection.responseCode, url)
            return connection.inputStream.use { it.readBytes() }
        } catch (error: CopypartyException) {
            throw error
        } catch (error: IOException) {
            throw CopypartyException.Unreachable("Could not reach the server at ${origin(url)}: ${reason(error)}", error)
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: URL, method: String, password: String?): HttpURLConnection =
        (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            // copyparty answers a folder without a trailing slash with a redirect to the folder;
            // following it keeps a listing working on both spellings.
            instanceFollowRedirects = true
            if (!password.isNullOrEmpty()) setRequestProperty(HEADER_PASSWORD, password)
        }

    private fun checkStatus(status: Int, url: URL) {
        if (status in 200..299) return
        when (status) {
            HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN ->
                throw CopypartyException.Unauthorized()
            else -> throw CopypartyException.BadResponse(
                "The server answered HTTP $status for ${url.path.ifEmpty { "/" }}.",
            )
        }
    }

    // -- URLs ---------------------------------------------------------------------------------

    /**
     * `<baseUrl>/<path>?<query>`. Each path segment is encoded on its own, so a file name with a
     * space is requested by its real path (`my%20file.txt`) instead of putting a raw space in the
     * request line.
     */
    private fun endpoint(baseUrl: String, path: String, query: String?): URL {
        val base = URL(baseUrl)
        val port = if (base.port >= 0) ":${base.port}" else ""
        val segments = listOf(base.path.trim('/'), path.trim('/'))
            .filter { it.isNotEmpty() }
            .map { encodePath(it) }
        val pathPart = if (segments.isEmpty()) "/" else "/" + segments.joinToString("/")
        val queryPart = query?.let { "?$it" }.orEmpty()
        return URL("${base.protocol}://${base.host}$port$pathPart$queryPart")
    }

    private fun encodePath(value: String): String =
        value.split('/').joinToString("/") { segment ->
            URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
        }

    private fun origin(url: URL): String = "${url.protocol}://${url.host}${if (url.port >= 0) ":${url.port}" else ""}"

    // -- the JSON listing ---------------------------------------------------------------------

    private fun parseListing(body: String, url: URL): List<BrowseEntry> {
        val root = try {
            Json.parseToJsonElement(body)
        } catch (error: Exception) {
            throw CopypartyException.BadResponse("The folder listing from ${origin(url)} is not JSON.")
        }
        val obj = root as? JsonObject
            ?: throw CopypartyException.BadResponse("The folder listing from ${origin(url)} is not a JSON object.")
        if ("dirs" !in obj && "files" !in obj) {
            throw CopypartyException.BadResponse(
                "The server at ${origin(url)} answered with a page, not a folder listing.",
            )
        }
        return obj["dirs"].entries(isFolder = true) + obj["files"].entries(isFolder = false)
    }

    private fun JsonElement?.entries(isFolder: Boolean): List<BrowseEntry> {
        val array = this as? JsonArray ?: return emptyList()
        return array.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val href = (item["href"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            BrowseEntry(
                name = displayName(href),
                isFolder = isFolder,
                size = (item["sz"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 0L,
                modified = (item["ts"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 0L,
            )
        }
    }

    /** `sub/` -> `sub`, `my%20file.txt` -> `my file.txt`, `a/b.txt` -> `b.txt`. */
    private fun displayName(href: String): String {
        val trimmed = href.trimEnd('/')
        val last = trimmed.substringAfterLast('/')
        return decodePercent(last)
    }

    /**
     * Decodes `%XX` escapes as UTF-8. [URLDecoder] is not used because it also turns `+` into a
     * space, and copyparty leaves a literal `+` in a file name as it is.
     */
    private fun decodePercent(value: String): String {
        val out = StringBuilder(value.length)
        val bytes = ByteArrayOutputStream()
        fun flush() {
            if (bytes.size() > 0) {
                out.append(String(bytes.toByteArray(), Charsets.UTF_8))
                bytes.reset()
            }
        }
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char == '%' && index + 2 < value.length) {
                val byte = value.substring(index + 1, index + 3).toIntOrNull(16)
                if (byte != null) {
                    bytes.write(byte)
                    index += 3
                    continue
                }
            }
            flush()
            out.append(char)
            index += 1
        }
        flush()
        return out.toString()
    }

    private fun reason(error: Throwable): String = error.message ?: error.javaClass.simpleName

    private companion object {
        const val LISTING_QUERY = "ls=json"
        const val HEADER_PASSWORD = "PW"
    }
}
