package com.daftar.app.data.remote

import com.google.gson.Gson
import com.google.gson.JsonParseException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class HttpMethod { GET, POST, PUT, PATCH, DELETE }

data class HttpRequest(
    val method: HttpMethod,
    val url: String,
    val headers: Map<String, String>,
    val body: String?,
)

data class HttpResponse(val statusCode: Int, val body: String)

/** Transport seam: production uses HttpURLConnection; unit tests use a recorder. */
fun interface HttpTransport {
    suspend fun execute(request: HttpRequest): HttpResponse
}

class UrlConnectionTransport(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : HttpTransport {
    override suspend fun execute(request: HttpRequest): HttpResponse = withContext(ioDispatcher) {
        val connection = (URL(request.url).openConnection() as HttpURLConnection).apply {
            requestMethod = request.method.name
            connectTimeout = 15_000
            readTimeout = 30_000
            useCaches = false
            request.headers.forEach { (name, value) -> setRequestProperty(name, value) }
            if (request.body != null) doOutput = true
        }
        try {
            request.body?.let { body ->
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            HttpResponse(status, stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty())
        } finally {
            connection.disconnect()
        }
    }
}

interface AuthTokenStore {
    var token: String?
    fun clear()
}

sealed class ApiException(message: String, cause: Throwable? = null) : IOException(message, cause) {
    data class ValidationDetail(val path: String, val messageText: String)

    class Unauthorized(message: String) : ApiException(message)
    class Server(
        val statusCode: Int,
        message: String,
        val details: List<ValidationDetail>,
    ) : ApiException(message)
    class Decoding(cause: Throwable) : ApiException("The server response could not be read", cause)
    class Transport(cause: Throwable) : ApiException(cause.message ?: "Network request failed", cause)
}

/**
 * Cross-cutting HTTP behavior shared by every endpoint: JSON, bearer sessions,
 * the backend's error envelope, and revoked-token cleanup.
 */
class ApiClient(
    baseUrl: String,
    private val tokenStore: AuthTokenStore,
    private val transport: HttpTransport = UrlConnectionTransport(),
    private val gson: Gson = Gson(),
) {
    private val root = baseUrl.trimEnd('/') + "/"

    suspend fun <Response : Any> send(
        method: HttpMethod,
        path: String,
        responseType: Class<Response>,
        query: Map<String, String> = emptyMap(),
        body: Any? = null,
        authenticated: Boolean = true,
    ): Response {
        val response = execute(method, path, query, body, authenticated)
        if (response.body.isBlank()) throw ApiException.Decoding(EOFException(path))
        return try {
            gson.fromJson(response.body, responseType)
        } catch (error: JsonParseException) {
            throw ApiException.Decoding(error)
        }
    }

    suspend fun sendWithoutResponse(
        method: HttpMethod,
        path: String,
        body: Any? = null,
        authenticated: Boolean = true,
    ) {
        execute(method, path, emptyMap(), body, authenticated)
    }

    private suspend fun execute(
        method: HttpMethod,
        path: String,
        query: Map<String, String>,
        body: Any?,
        authenticated: Boolean,
    ): HttpResponse {
        val normalizedPath = path.trimStart('/')
        val queryString = query.entries.joinToString("&") { (key, value) ->
            "${key.urlEncoded()}=${value.urlEncoded()}"
        }
        val url = root + normalizedPath + if (queryString.isEmpty()) "" else "?$queryString"
        val encodedBody = body?.let(gson::toJson)
        val headers = buildMap {
            put("Accept", "application/json")
            if (encodedBody != null) put("Content-Type", "application/json")
            if (authenticated) tokenStore.token?.let { put("Authorization", "Bearer $it") }
        }

        val response = try {
            transport.execute(HttpRequest(method, url, headers, encodedBody))
        } catch (error: ApiException) {
            throw error
        } catch (error: IOException) {
            throw ApiException.Transport(error)
        } catch (error: Exception) {
            throw ApiException.Transport(error)
        }
        if (response.statusCode !in 200..299) throw serverError(response)
        return response
    }

    private fun serverError(response: HttpResponse): ApiException {
        val envelope = runCatching { gson.fromJson(response.body, ErrorEnvelopeDto::class.java) }.getOrNull()
        val message = envelope?.error?.message ?: "Request failed (${response.statusCode})"
        if (response.statusCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
            // Sessions are server-revocable, so an unauthorized token must not be restored again.
            tokenStore.clear()
            return ApiException.Unauthorized(message)
        }
        return ApiException.Server(
            statusCode = response.statusCode,
            message = message,
            details = envelope?.error?.details.orEmpty().map {
                ApiException.ValidationDetail(it.path, it.message)
            },
        )
    }

    private fun String.urlEncoded(): String = java.net.URLEncoder.encode(this, Charsets.UTF_8.name())

    private class EOFException(path: String) : IOException("Empty response for $path")
}
