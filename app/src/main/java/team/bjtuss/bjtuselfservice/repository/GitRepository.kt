package team.bjtuss.bjtuselfservice.repository

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import team.bjtuss.bjtuselfservice.constant.ApiConstant


data class GitHubRelease(
    val id: Long,
    @Json(name = "tag_name") val tagName: String,
    val name: String?,
    val body: String?,
    @Json(name = "html_url") val htmlUrl: String,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "published_at") val publishedAt: String,
    val assets: List<Asset>
)

data class Asset(
    val name: String,
    @Json(name = "browser_download_url") val downloadUrl: String,
    val size: Long
)

/**
 * The browser_download_url points directly at the APK asset. Prefer the signed
 * arm64 release APK, then any non-debug APK, then the release page fallback.
 */
fun GitHubRelease.preferredDownloadUrl(): String {
    val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
    val preferred = apks.firstOrNull {
        !it.name.contains("debug", ignoreCase = true) &&
            it.name.contains("arm64-v8a", ignoreCase = true)
    } ?: apks.firstOrNull {
        !it.name.contains("debug", ignoreCase = true)
    } ?: apks.firstOrNull()
    return preferred?.downloadUrl ?: htmlUrl
}

suspend fun fetchLatestRelease(
    channel: UpdateChannel = UpdateChannel.VANNESA,
): GitHubRelease? = withContext(Dispatchers.IO) {
    val request = Request.Builder()
        .url(channel.latestReleaseUrl)
        .build()
    val client = OkHttpClient()
    try {
        val response: Response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            println("Request failed: ${response.code}")
            return@withContext null
        }

        val responseBody = response.body?.string() ?: return@withContext null

        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        val jsonAdapter = moshi.adapter(GitHubRelease::class.java)
        return@withContext jsonAdapter.fromJson(responseBody)
    } catch (e: Exception) {
        // Update checks run during startup. A malformed GitHub response must not
        // be allowed to take down the main screen.
        println("Network error: ${e.message}")
        null
    }
}