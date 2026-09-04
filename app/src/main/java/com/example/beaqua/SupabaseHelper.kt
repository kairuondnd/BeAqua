package com.example.beaqua

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink
import okio.source
import java.io.IOException
import java.net.URLConnection
import java.util.UUID

data class SupabaseAuthResult(
    val userId: String,
    val hasSession: Boolean,
    val isNewSignup: Boolean = true
)

object SupabaseHelper {
    private const val KYC_BUCKET = "kyc-documents"
    private const val SESSION_PREFERENCES = "supabase_session"
    private const val ACCESS_TOKEN = "access_token"
    private const val USER_ID = "user_id"
    private const val MAX_KYC_FILE_BYTES = 15L * 1024L * 1024L
    private val client = OkHttpClient()

    private fun authRequest(
        context: Context,
        relativeEndpoint: String,
        jsonBody: JsonObject
    ): Task<SupabaseAuthResult> {
        val source = TaskCompletionSource<SupabaseAuthResult>()
        val baseUrl = configuredBaseUrl(source) ?: return source.task
        val url = baseUrl.newBuilder()
            .addPathSegment("auth")
            .addPathSegment("v1")
            .apply {
                if (relativeEndpoint.startsWith("token")) {
                    addPathSegment("token")
                    addQueryParameter("grant_type", "password")
                } else {
                    addPathSegment(relativeEndpoint)
                }
            }
            .build()
        val request = authenticatedRequestBuilder(url, source)
            ?: return source.task
        val jsonMediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        client.newCall(
            request.post(jsonBody.toString().toRequestBody(jsonMediaType)).build()
        ).enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                source.trySetException(IOException("Could not connect to Supabase", error))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val responseBody = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        source.trySetException(IllegalStateException(errorMessage(responseBody)))
                        return
                    }
                    try {
                        val json = JsonParser.parseString(responseBody).asJsonObject
                        val userJson = json.getAsJsonObject("user")
                        val userId = userJson
                            ?.get("id")
                            ?.asString
                            .orEmpty()
                        if (userId.isBlank()) {
                            throw IllegalStateException("Supabase did not return a user account")
                        }
                        val accessToken = json.get("access_token")?.asString.orEmpty()
                        if (accessToken.isNotBlank()) {
                            saveSession(context, userId, accessToken)
                        }
                        source.trySetResult(
                            SupabaseAuthResult(
                                userId = userId,
                                hasSession = accessToken.isNotBlank(),
                                // With email enumeration protection, signing up an address
                                // that already exists can return an obscured user whose
                                // identities array is empty instead of returning an error.
                                isNewSignup = relativeEndpoint != "signup" ||
                                    userJson?.getAsJsonArray("identities")?.size() != 0
                            )
                        )
                    } catch (error: Exception) {
                        source.trySetException(
                            IllegalStateException("Supabase returned an invalid response", error)
                        )
                    }
                }
            }
        })
        return source.task
    }

    fun currentUserId(context: Context): String {
        return context.getSharedPreferences(SESSION_PREFERENCES, Context.MODE_PRIVATE)
            .getString(USER_ID, "")
            .orEmpty()
    }

    fun uploadKycDocument(
        context: Context,
        userId: String,
        documentType: String,
        documentUri: Uri
    ): Task<String> {
        val source = TaskCompletionSource<String>()
        if (userId.isBlank()) {
            source.setException(IllegalStateException("Please log in again before uploading"))
            return source.task
        }
        val accessToken = accessToken(context)
        if (accessToken.isBlank()) {
            source.setException(IllegalStateException("Your secure session expired. Please log in again"))
            return source.task
        }

        val details = documentDetails(context, documentUri)
        if (details.size > MAX_KYC_FILE_BYTES) {
            source.setException(IllegalArgumentException("Each document must be 15 MB or smaller"))
            return source.task
        }
        val extension = fileExtension(details.displayName, details.mimeType)
        val path = "$userId/$documentType/${UUID.randomUUID()}.$extension"
        val url = storageObjectUrl(source, KYC_BUCKET, path) ?: return source.task
        val requestBody = contentUriRequestBody(
            context,
            documentUri,
            details.mimeType,
            details.size
        )
        val request = authenticatedRequestBuilder(url, source, accessToken)
            ?.addHeader("x-upsert", "false")
            ?.post(requestBody)
            ?.build()
            ?: return source.task

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                source.trySetException(IOException("Document upload could not connect", error))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val responseBody = it.body?.string().orEmpty()
                    if (it.isSuccessful) {
                        source.trySetResult(path)
                    } else {
                        source.trySetException(IllegalStateException(errorMessage(responseBody)))
                    }
                }
            }
        })
        return source.task
    }

    fun createKycSignedUrl(context: Context, path: String): Task<String> {
        val source = TaskCompletionSource<String>()
        if (path.startsWith("https://") || path.startsWith("http://")) {
            source.setResult(path)
            return source.task
        }
        val accessToken = accessToken(context)
        if (accessToken.isBlank()) {
            source.setException(IllegalStateException("Admin session expired. Please log in again"))
            return source.task
        }
        val baseUrl = configuredBaseUrl(source) ?: return source.task
        val url = baseUrl.newBuilder()
            .addPathSegment("storage")
            .addPathSegment("v1")
            .addPathSegment("object")
            .addPathSegment("sign")
            .addPathSegment(KYC_BUCKET)
            .apply { path.split('/').forEach(::addPathSegment) }
            .build()
        val jsonBody = JsonObject().apply { addProperty("expiresIn", 600) }
        val request = authenticatedRequestBuilder(url, source, accessToken)
            ?.post(
                jsonBody.toString().toRequestBody(
                    "application/json; charset=utf-8".toMediaTypeOrNull()
                )
            )
            ?.build()
            ?: return source.task

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                source.trySetException(IOException("Could not open the secure document", error))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val responseBody = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        source.trySetException(IllegalStateException(errorMessage(responseBody)))
                        return
                    }
                    try {
                        val json = JsonParser.parseString(responseBody).asJsonObject
                        val signedPath = json.get("signedURL")?.asString
                            ?: json.get("signedUrl")?.asString
                            ?: throw IllegalStateException("Signed document link was unavailable")
                        val signedUrl = when {
                            signedPath.startsWith("http://") || signedPath.startsWith("https://") ->
                                signedPath
                            signedPath.startsWith("/storage/v1/") ->
                                baseUrl.toString().trimEnd('/') + signedPath
                            signedPath.startsWith("/object/") ->
                                baseUrl.toString().trimEnd('/') + "/storage/v1" + signedPath
                            else -> baseUrl.toString().trimEnd('/') +
                                "/storage/v1/" + signedPath.trimStart('/')
                        }
                        source.trySetResult(signedUrl)
                    } catch (error: Exception) {
                        source.trySetException(error)
                    }
                }
            }
        })
        return source.task
    }

    fun signOut(context: Context) {
        context.getSharedPreferences(SESSION_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    private fun saveSession(context: Context, userId: String, accessToken: String) {
        context.getSharedPreferences(SESSION_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(USER_ID, userId)
            .putString(ACCESS_TOKEN, accessToken)
            .apply()
    }

    private fun accessToken(context: Context): String {
        return context.getSharedPreferences(SESSION_PREFERENCES, Context.MODE_PRIVATE)
            .getString(ACCESS_TOKEN, "")
            .orEmpty()
    }

    private fun <T> configuredBaseUrl(source: TaskCompletionSource<T>): HttpUrl? {
        val url = BuildConfig.SUPABASE_URL.trim().trimEnd('/').toHttpUrlOrNull()
        if (url == null || BuildConfig.SUPABASE_PUBLISHABLE_KEY.isBlank()) {
            source.setException(
                IllegalStateException("Supabase is not configured in local.properties")
            )
            return null
        }
        return url
    }

    private fun <T> authenticatedRequestBuilder(
        url: HttpUrl,
        source: TaskCompletionSource<T>,
        bearerToken: String = BuildConfig.SUPABASE_PUBLISHABLE_KEY
    ): Request.Builder? {
        if (BuildConfig.SUPABASE_PUBLISHABLE_KEY.isBlank()) {
            source.setException(
                IllegalStateException("Supabase publishable key is missing")
            )
            return null
        }
        return Request.Builder()
            .url(url)
            .addHeader("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            .addHeader("Authorization", "Bearer $bearerToken")
    }

    private fun <T> storageObjectUrl(
        source: TaskCompletionSource<T>,
        bucket: String,
        path: String
    ): HttpUrl? {
        val baseUrl = configuredBaseUrl(source) ?: return null
        return baseUrl.newBuilder()
            .addPathSegment("storage")
            .addPathSegment("v1")
            .addPathSegment("object")
            .addPathSegment(bucket)
            .apply { path.split('/').forEach(::addPathSegment) }
            .build()
    }

    private data class DocumentDetails(
        val displayName: String,
        val mimeType: String,
        val size: Long
    )

    private fun documentDetails(context: Context, uri: Uri): DocumentDetails {
        var displayName = "document"
        var size = -1L
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) displayName = cursor.getString(nameIndex) ?: displayName
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
        val mimeType = context.contentResolver.getType(uri)
            ?: URLConnection.guessContentTypeFromName(displayName)
            ?: "application/octet-stream"
        return DocumentDetails(displayName, mimeType, size)
    }

    private fun fileExtension(displayName: String, mimeType: String): String {
        val supplied = displayName.substringAfterLast('.', "")
            .lowercase()
            .filter(Char::isLetterOrDigit)
            .take(8)
        if (supplied.isNotBlank()) return supplied
        return when (mimeType.lowercase()) {
            "application/pdf" -> "pdf"
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/heic" -> "heic"
            "image/heif" -> "heif"
            else -> "file"
        }
    }

    private fun contentUriRequestBody(
        context: Context,
        uri: Uri,
        mimeType: String,
        knownSize: Long
    ): RequestBody {
        return object : RequestBody() {
            override fun contentType() = mimeType.toMediaTypeOrNull()

            override fun contentLength(): Long = knownSize

            override fun writeTo(sink: BufferedSink) {
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IOException("The selected document could not be read")
                input.use { sink.writeAll(it.source()) }
            }
        }
    }

    private fun errorMessage(responseBody: String): String {
        return try {
            val json = JsonParser.parseString(responseBody).asJsonObject
            json.get("msg")?.asString
                ?: json.get("message")?.asString
                ?: json.get("error_description")?.asString
                ?: json.get("error")?.asString
                ?: "Supabase request failed"
        } catch (_: Exception) {
            "Supabase request failed"
        }
    }

}
