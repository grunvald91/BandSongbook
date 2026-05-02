package com.fithealthzone.bandsongbook.data.sync

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class SyncApiClient {
    private val client = HttpClient(OkHttp) {
        expectSuccess = true

        engine {
            config {
                connectTimeout(20_000, java.util.concurrent.TimeUnit.MILLISECONDS)
                readTimeout(90_000, java.util.concurrent.TimeUnit.MILLISECONDS)
                writeTimeout(90_000, java.util.concurrent.TimeUnit.MILLISECONDS)
                callTimeout(120_000, java.util.concurrent.TimeUnit.MILLISECONDS)
                retryOnConnectionFailure(true)
            }
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 120_000
            connectTimeoutMillis = 20_000
            socketTimeoutMillis = 90_000
        }

        install(HttpRequestRetry) {
            retryOnExceptionOrServerErrors(maxRetries = 2)
            exponentialDelay(base = 300.0, maxDelayMs = 2_500)
            retryIf { _, response ->
                response.status.value == 408 || response.status.value == 429
            }
        }

        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun pull(baseUrl: String, groupCode: String, authToken: String): SyncSnapshotDto {
        val url = baseUrl.trimEnd('/') + "/sync/pull"
        return client.post(url) {
            contentType(ContentType.Application.Json)
            applyAuth(authToken)
            setBody(SyncPullRequest(groupCode))
        }.body()
    }

    suspend fun meta(baseUrl: String, groupCode: String, authToken: String): SyncMetaDto {
        val url = baseUrl.trimEnd('/') + "/sync/meta"
        return client.post(url) {
            contentType(ContentType.Application.Json)
            applyAuth(authToken)
            setBody(SyncPullRequest(groupCode))
        }.body()
    }

    suspend fun push(baseUrl: String, groupCode: String, authToken: String, snapshot: SyncSnapshotDto) {
        val url = baseUrl.trimEnd('/') + "/sync/push"
        val response = client.post(url) {
            contentType(ContentType.Application.Json)
            applyAuth(authToken)
            setBody(SyncPushRequest(groupCode = groupCode, snapshot = snapshot))
        }
        if (!response.status.isSuccess()) {
            val body = runCatching { response.bodyAsText() }.getOrElse { "" }
            error("Push failed with status ${response.status}: $body")
        }
    }

    suspend fun audioExists(
        baseUrl: String,
        groupCode: String,
        authToken: String,
        contentHash: String
    ): AudioExistsResponse {
        val url = baseUrl.trimEnd('/') + "/audio/exists"
        return client.post(url) {
            contentType(ContentType.Application.Json)
            applyAuth(authToken)
            setBody(AudioExistsRequest(groupCode = groupCode, contentHash = contentHash))
        }.body()
    }

    suspend fun audioUploadUrl(
        baseUrl: String,
        groupCode: String,
        authToken: String,
        contentHash: String,
        mimeType: String?,
        sizeBytes: Long?,
        fileName: String?
    ): AudioUploadUrlResponse {
        val url = baseUrl.trimEnd('/') + "/audio/upload-url"
        return client.post(url) {
            contentType(ContentType.Application.Json)
            applyAuth(authToken)
            setBody(
                AudioUploadUrlRequest(
                    groupCode = groupCode,
                    contentHash = contentHash,
                    mimeType = mimeType,
                    sizeBytes = sizeBytes,
                    fileName = fileName
                )
            )
        }.body()
    }

    suspend fun audioConfirm(
        baseUrl: String,
        authToken: String,
        request: AudioConfirmRequest
    ): AudioConfirmResponse {
        val url = baseUrl.trimEnd('/') + "/audio/confirm"
        return client.post(url) {
            contentType(ContentType.Application.Json)
            applyAuth(authToken)
            setBody(request)
        }.body()
    }

    suspend fun audioDownloadUrl(
        baseUrl: String,
        groupCode: String,
        authToken: String,
        objectKey: String
    ): AudioDownloadUrlResponse {
        val url = baseUrl.trimEnd('/') + "/audio/download-url"
        return client.post(url) {
            contentType(ContentType.Application.Json)
            applyAuth(authToken)
            setBody(AudioDownloadUrlRequest(groupCode = groupCode, objectKey = objectKey))
        }.body()
    }

    suspend fun uploadBinary(
        uploadUrl: String,
        bytes: ByteArray,
        mimeType: String?,
        headers: Map<String, String>
    ) {
        val response = client.put(uploadUrl) {
            if (!mimeType.isNullOrBlank()) {
                contentType(ContentType.parse(mimeType))
            }
            headers.forEach { (k, v) ->
                if (k.isNotBlank() && v.isNotBlank()) {
                    header(k, v)
                }
            }
            setBody(bytes)
        }

        if (!response.status.isSuccess()) {
            error("Upload failed with status ${response.status}")
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.applyAuth(authToken: String) {
        val token = authToken.trim().replace(Regex("(?i)^bearer\\s+"), "")
        if (token.isNotEmpty()) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
    }
}
