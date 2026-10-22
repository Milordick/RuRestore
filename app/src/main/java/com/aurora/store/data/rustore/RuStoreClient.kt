/*
 * SPDX-FileCopyrightText: 2026 RuOpenStore
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.data.rustore

import android.content.Context
import android.os.Build
import android.util.Log
import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.data.models.Artwork
import com.aurora.gplayapi.data.models.Category
import com.aurora.gplayapi.data.models.PlayFile
import com.aurora.gplayapi.data.models.Rating
import com.aurora.gplayapi.data.models.Review
import com.aurora.gplayapi.data.models.StreamBundle
import com.aurora.gplayapi.data.models.StreamCluster
import com.aurora.store.data.model.Report
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-level client for RuStore services.
 * Integrates directly with RuStore public REST APIs and maps them to Aurora Store domain models.
 */
@Singleton
class RuStoreClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json
) {
    companion object {
        private const val TAG = "RuStoreClient"
        const val BASE_BACKAPI = "https://backapi-m.rustore.ru"
        const val BASE_API = "https://api-m.rustore.ru"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }

    private val appIdCache = ConcurrentHashMap<String, Long>()
    private val companyNameToId = ConcurrentHashMap<String, String>()
    private val companyIdToName = ConcurrentHashMap<String, String>()
    private val sectionsCache = ConcurrentHashMap<String, Pair<Long, StreamBundle>>()
    private val appDetailsCache = ConcurrentHashMap<String, Pair<Long, App>>()
    private val searchCache = ConcurrentHashMap<String, Pair<Long, Pair<List<App>, Int>>>()

    data class StarCounts(
        val oneStar: Long,
        val twoStar: Long,
        val threeStar: Long,
        val fourStar: Long,
        val fiveStar: Long
    )

    fun formatRatingVotes(votes: Long): String {
        if (votes <= 0L) return "Нет оценок"
        if (votes >= 1_000_000L) {
            val millions = votes / 1_000_000.0
            val pattern = if (votes % 1_000_000L < 100_000L) "%.0f млн оценок" else "%.1f млн оценок"
            return String.format(Locale.getDefault(), pattern, millions)
        }
        if (votes >= 10_000L) {
            return String.format(Locale.getDefault(), "%d тыс. оценок", votes / 1000L)
        }
        if (votes >= 1_000L) {
            val thousands = votes / 1000.0
            val pattern = if (votes % 1000L < 100L) "%.0f тыс. оценок" else "%.1f тыс. оценок"
            return String.format(Locale.getDefault(), pattern, thousands)
        }
        val remainder10 = votes % 10
        val remainder100 = votes % 100
        val word = when {
            remainder100 in 11..19 -> "оценок"
            remainder10 == 1L -> "оценка"
            remainder10 in 2..4 -> "оценки"
            else -> "оценок"
        }
        return "$votes $word"
    }

    fun calculateStarDistribution(average: Float, totalVotes: Long): StarCounts {
        if (totalVotes <= 0L || average <= 0f) {
            return StarCounts(0L, 0L, 0L, 0L, 0L)
        }
        val target = average.toDouble().coerceIn(1.0, 5.0)
        val rawWeights = DoubleArray(5)
        for (i in 0 until 5) {
            val star = (i + 1).toDouble()
            val diff = star - target
            var w = Math.exp(-0.5 * (diff * diff) / 0.75)
            if (star == 1.0 && target > 3.0) {
                w = maxOf(w, 0.03 * (5.2 - target))
            }
            rawWeights[i] = maxOf(0.001, w)
        }
        val sumW = rawWeights.sum()
        val currentMean = rawWeights.indices.sumOf { (it + 1) * rawWeights[it] } / sumW
        val meanDiff = target - currentMean
        if (meanDiff > 0) {
            rawWeights[4] += meanDiff * sumW * 1.5
            rawWeights[3] += meanDiff * sumW * 0.5
        } else if (meanDiff < 0) {
            rawWeights[0] += (-meanDiff) * sumW * 1.5
            rawWeights[1] += (-meanDiff) * sumW * 0.5
        }
        val finalSum = rawWeights.sum()
        val c1 = (rawWeights[0] / finalSum * totalVotes).toLong()
        val c2 = (rawWeights[1] / finalSum * totalVotes).toLong()
        val c3 = (rawWeights[2] / finalSum * totalVotes).toLong()
        val c4 = (rawWeights[3] / finalSum * totalVotes).toLong()
        val c5 = (totalVotes - c1 - c2 - c3 - c4).coerceAtLeast(0L)
        return StarCounts(c1, c2, c3, c4, c5)
    }

    fun buildRating(average: Float, votes: Long): Rating {
        val stars = calculateStarDistribution(average, votes)
        val lbl = formatRatingVotes(votes)
        return Rating(
            average = average,
            oneStar = stars.oneStar,
            twoStar = stars.twoStar,
            threeStar = stars.threeStar,
            fourStar = stars.fourStar,
            fiveStar = stars.fiveStar,
            label = lbl,
            abbreviatedLabel = lbl
        )
    }

    fun rememberAppId(packageName: String, appId: Long) {
        if (appId > 0 && packageName.isNotBlank()) {
            appIdCache[packageName] = appId
        }
    }

    private val httpClient: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)

        // Trust Russian Root CAs (Ministry of Digital Development)
        RuStoreCertificates.configureSsl(builder, context)
        builder.build()
    }

    private val defaultJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * Executes an authenticated or device-identified GET request to RuStore.
     */
    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val reqBuilder = Request.Builder().url(url)
        RuStoreDevice.getHeaders(context).forEach { (k, v) ->
            reqBuilder.header(k, v)
        }
        val response = httpClient.newCall(reqBuilder.build()).execute()
        if (!response.isSuccessful) {
            val errBody = response.body?.string().orEmpty()
            Log.e(TAG, "GET $url failed: ${response.code} $errBody")
            throw RuntimeException("HTTP ${response.code}: $errBody")
        }
        response.body?.string().orEmpty()
    }

    /**
     * Executes a POST request with JSON payload to RuStore.
     */
    private suspend fun post(url: String, jsonPayload: String): String = withContext(Dispatchers.IO) {
        val body = jsonPayload.toRequestBody(JSON_MEDIA)
        val reqBuilder = Request.Builder().url(url).post(body)
        RuStoreDevice.getHeaders(context).forEach { (k, v) ->
            reqBuilder.header(k, v)
        }
        val response = httpClient.newCall(reqBuilder.build()).execute()
        if (!response.isSuccessful) {
            val errBody = response.body?.string().orEmpty()
            Log.e(TAG, "POST $url failed: ${response.code} $errBody")
            throw RuntimeException("HTTP ${response.code}: $errBody")
        }
        response.body?.string().orEmpty()
    }

    /**
     * Search apps by keyword query.
     */
    suspend fun search(query: String, pageNumber: Int = 0, pageSize: Int = 20): Pair<List<App>, Int> = withContext(Dispatchers.IO) {
        if (query.startsWith("pub:", ignoreCase = true)) {
            val devApps = getDeveloperApps(query.substring(4), pageSize)
            return@withContext devApps to 1
        }
        val cacheKey = "$query|$pageNumber|$pageSize"
        val now = System.currentTimeMillis()
        searchCache[cacheKey]?.let { (ts, cached) ->
            if (now - ts < 180_000L) return@withContext cached
        }
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$BASE_BACKAPI/applicationData/apps?query=$encodedQuery&pageNumber=$pageNumber&pageSize=$pageSize"
            val raw = get(url)
            val envelope = defaultJson.decodeFromString<RuStoreEnvelope<RuStoreSearchBody>>(raw)
            val body = envelope.body ?: return@withContext emptyList<App>() to 0
            val apps = body.content.map { item ->
                rememberAppId(item.packageName, item.appId)
                App(
                    id = (item.appId and 0x7FFFFFFFL).toInt(),
                    packageName = item.packageName,
                    displayName = item.appName,
                    developerName = "",
                    versionCode = item.versionCode,
                    versionName = "",
                    shortDescription = item.shortDescription,
                    description = item.shortDescription,
                    iconArtwork = Artwork(url = item.iconUrl),
                    isFree = item.price == 0,
                    price = if (item.price == 0) "Free" else "${item.price} ₽",
                    size = 0L,
                    updatedOn = "",
                    labeledRating = if (item.averageUserRating > 0f) String.format(Locale.US, "%.1f", item.averageUserRating) else "",
                    rating = buildRating(item.averageUserRating, item.totalRatings),
                    installs = parseDownloads(item.roundedDownloadsText)
                )
            }
            val result = apps to body.totalPages
            searchCache[cacheKey] = now to result
            result
        } catch (e: Exception) {
            Log.e(TAG, "search failed for '$query'", e)
            emptyList<App>() to 0
        }
    }

    /**
     * Fetches full application details by package name.
     */
    suspend fun getAppDetails(packageName: String): App? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cached = appDetailsCache[packageName]
        if (cached != null && now - cached.first < 600_000L) {
            return@withContext cached.second
        }
        try {
            val url = "$BASE_BACKAPI/applicationData/overallInfo/$packageName"
            val raw = get(url)
            val envelope = defaultJson.decodeFromString<RuStoreEnvelope<RuStoreAppDetails>>(raw)
            val details = envelope.body ?: return@withContext null
            rememberAppId(details.packageName, details.appId)

            if (!details.publicCompanyId.isNullOrBlank()) {
                companyNameToId[details.companyName] = details.publicCompanyId
                companyIdToName[details.publicCompanyId] = details.companyName
            }

            val app = App(
                id = (details.appId and 0x7FFFFFFFL).toInt(),
                packageName = details.packageName,
                displayName = details.appName,
                developerName = details.companyName,
                versionCode = details.versionCode,
                versionName = details.versionName,
                shortDescription = details.shortDescription,
                description = details.fullDescription,
                changes = details.whatsNew,
                iconArtwork = Artwork(url = details.iconUrl),
                screenshots = details.fileUrls
                    .sortedBy { it.ordinal }
                    .map { Artwork(url = it.effectiveUrl) }
                    .filter { it.url.isNotBlank() }
                    .toMutableList(),
                isFree = details.price == 0,
                price = if (details.price == 0) "Free" else "${details.price} ₽",
                size = details.fileSize,
                updatedOn = "",
                labeledRating = if ((details.rating?.average ?: 0f) > 0f) {
                    String.format(Locale.US, "%.1f", details.rating!!.average)
                } else "",
                rating = buildRating(details.rating?.average ?: 0f, details.rating?.votes ?: 0L),
                categoryName = details.categories.firstOrNull().orEmpty(),
                developerEmail = details.developerContacts?.email.orEmpty(),
                developerWebsite = details.developerContacts?.website.orEmpty(),
                targetSdk = details.targetSdkVersion ?: details.maxSdkVersion ?: 34,
                installs = parseDownloads(details.roundedDownloadsText)
            )
            appDetailsCache[packageName] = now to app
            app
        } catch (e: Exception) {
            Log.e(TAG, "getAppDetails failed for $packageName", e)
            null
        }
    }

    /**
     * Resolves apps published by a developer or company.
     * Uses RuStore's native /applicationData/devs/{devId}/apps endpoint.
     */
    suspend fun getDeveloperApps(devIdOrName: String, limit: Int = 50): List<App> = withContext(Dispatchers.IO) {
        val clean = devIdOrName.removePrefix("pub:").trim('"', ' ', '«', '»')
        val companyId = when {
            clean.matches(Regex("^[0-9a-f]{8}$")) -> clean
            companyNameToId.containsKey(clean) -> companyNameToId[clean]
            companyNameToId.containsKey(devIdOrName) -> companyNameToId[devIdOrName]
            else -> {
                val (apps, _) = search(clean, pageNumber = 0, pageSize = 3)
                val firstPkg = apps.firstOrNull()?.packageName
                if (firstPkg != null) {
                    val d = getAppDetails(firstPkg)
                    d?.let { companyNameToId[clean] }
                } else null
            }
        }

        if (companyId != null) {
            try {
                val url = "$BASE_BACKAPI/applicationData/devs/$companyId/apps?limit=$limit"
                val raw = get(url)
                val envelope = defaultJson.decodeFromString<RuStoreEnvelope<RuStoreDevAppsBody>>(raw)
                val elements = envelope.body?.elements.orEmpty()
                if (elements.isNotEmpty()) {
                    val devName = companyIdToName[companyId] ?: clean
                    return@withContext elements.map { item ->
                        rememberAppId(item.packageName, item.appId)
                        App(
                            id = (item.appId and 0x7FFFFFFFL).toInt(),
                            packageName = item.packageName,
                            displayName = item.appName,
                            developerName = devName,
                            versionCode = item.versionCode,
                            shortDescription = item.shortDescription,
                            iconArtwork = Artwork(url = item.iconUrl),
                            isFree = item.price == 0,
                            size = 0L,
                            labeledRating = if (item.averageUserRating > 0f) String.format(Locale.US, "%.1f", item.averageUserRating) else "",
                            rating = buildRating(item.averageUserRating, item.totalRatings)
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "getDeveloperApps failed for companyId $companyId", e)
            }
        }

        search(clean, pageNumber = 0, pageSize = limit).first
    }

    /**
     * Batch loads app information for a list of package names (e.g. for updates and showcases).
     */
    suspend fun getBatchApps(packageNames: List<String>): List<App> = withContext(Dispatchers.IO) {
        if (packageNames.isEmpty()) return@withContext emptyList()
        try {
            val url = "$BASE_BACKAPI/v2/showcase/store-app"
            val payload = json.encodeToString(RuStoreBatchRequest.serializer(), RuStoreBatchRequest(packageNames))
            val raw = post(url, payload)
            val items = defaultJson.decodeFromString<List<RuStoreBatchAppItem>>(raw)
            items.map { item ->
                rememberAppId(item.packageName, item.id)
                App(
                    id = (item.id and 0x7FFFFFFFL).toInt(),
                    packageName = item.packageName,
                    displayName = item.name,
                    developerName = "",
                    versionCode = item.versionCode,
                    versionName = "",
                    shortDescription = item.description,
                    description = item.description,
                    iconArtwork = Artwork(url = item.iconUrl),
                    screenshots = item.screenshots
                        .sortedBy { it.ordinal }
                        .map { Artwork(url = it.effectiveUrl) }
                        .filter { it.url.isNotBlank() }
                        .toMutableList(),
                    isFree = true,
                    size = item.fileSize,
                    labeledRating = if ((item.rating?.average ?: 0f) > 0f) {
                        String.format(Locale.US, "%.1f", item.rating!!.average)
                    } else "",
                    rating = buildRating(item.rating?.average ?: 0f, item.rating?.votes ?: 0L)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "getBatchApps failed", e)
            emptyList()
        }
    }

    /**
     * Resolves direct APK download URL for an application from RuStore CDN using packageName.
     */
    suspend fun getDownloadUrls(
        packageName: String,
        supportedAbis: List<String> = getDeviceAbis(),
        sdkVersion: Int = Build.VERSION.SDK_INT
    ): List<PlayFile> = withContext(Dispatchers.IO) {
        var appId = appIdCache[packageName]
        if (appId == null) {
            val details = getAppDetails(packageName)
            if (details != null) {
                appId = appIdCache[packageName]
            }
        }
        if (appId == null) {
            Log.e(TAG, "Cannot resolve appId for package $packageName")
            return@withContext emptyList()
        }
        val files = getDownloadUrls(appId, supportedAbis, sdkVersion)
        // Ensure standard filename matching packageName
        files.map { it.copy(name = "$packageName.apk") }
    }

    /**
     * Resolves direct APK download URL for an application from RuStore CDN using appId.
     * Uses a multi-tier fallback (v3 -> applicationData/v2) to ensure rock-solid downloads.
     */
    suspend fun getDownloadUrls(
        appId: Long,
        supportedAbis: List<String> = getDeviceAbis(),
        sdkVersion: Int = Build.VERSION.SDK_INT
    ): List<PlayFile> = withContext(Dispatchers.IO) {
        val rawUrls = fetchDownloadUrlsWithFallback(appId, supportedAbis, sdkVersion)
        rawUrls.mapIndexed { index, item ->
            val directApkUrl = item.url.replace(Regex("\\.zip$"), ".apk")
            val fileName = if (rawUrls.size == 1) "$appId.apk" else "${appId}_$index.apk"
            val (finalUrl, finalSize) = probeApkUrl(directApkUrl, item.url, item.size)
            PlayFile(
                name = fileName,
                url = finalUrl,
                size = finalSize,
                sha256 = item.hash.orEmpty()
            )
        }
    }

    private fun probeApkUrl(apkUrl: String, fallbackUrl: String, defaultSize: Long): Pair<String, Long> {
        return try {
            val req = Request.Builder().url(apkUrl).head().build()
            httpClient.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    val len = response.header("Content-Length")?.toLongOrNull() ?: defaultSize
                    apkUrl to len
                } else {
                    fallbackUrl to defaultSize
                }
            }
        } catch (_: Exception) {
            apkUrl to defaultSize
        }
    }

    private fun extractDownloadItems(raw: String): List<RuStoreDownloadUrlItem> {
        try {
            val direct = defaultJson.decodeFromString<RuStoreDownloadLinkResponse>(raw)
            if (direct.downloadUrls.isNotEmpty()) return direct.downloadUrls
        } catch (_: Exception) {}
        try {
            val env = defaultJson.decodeFromString<RuStoreEnvelope<RuStoreDownloadLinkResponse>>(raw)
            if (env.body?.downloadUrls?.isNotEmpty() == true) return env.body.downloadUrls
        } catch (_: Exception) {}
        return emptyList()
    }

    private suspend fun fetchDownloadUrlsWithFallback(
        appId: Long,
        supportedAbis: List<String>,
        sdkVersion: Int
    ): List<RuStoreDownloadUrlItem> {
        // Tier 1: v3 showcase download-link (lightweight appId, provides universal standalone APKs directly)
        try {
            val url = "$BASE_BACKAPI/v3/showcase/apps/download-link"
            val payload = "{\"appId\":$appId}"
            val items = extractDownloadItems(post(url, payload))
            if (items.isNotEmpty()) {
                Log.i(TAG, "Tier 1 download-link (v3) resolved ${items.size} file(s) for appId $appId")
                return items
            }
        } catch (e: Exception) {
            Log.w(TAG, "Tier 1 download-link (v3) failed for appId $appId: ${e.message}")
        }

        // Tier 2: applicationData/v2/download-link (fallback with device ABI specs)
        try {
            val url = "$BASE_BACKAPI/applicationData/v2/download-link"
            val reqDto = RuStoreDownloadLinkRequest(
                appId = appId,
                firstInstall = true,
                supportedAbis = supportedAbis,
                screenDensity = 480,
                supportedLocales = listOf("ru"),
                sdkVersion = sdkVersion,
                withoutSplits = false
            )
            val payload = json.encodeToString(RuStoreDownloadLinkRequest.serializer(), reqDto)
            val items = extractDownloadItems(post(url, payload))
            if (items.isNotEmpty()) {
                Log.i(TAG, "Tier 2 download-link (v2) resolved ${items.size} file(s) for appId $appId")
                return items
            }
        } catch (e: Exception) {
            Log.e(TAG, "Tier 2 download-link (v2) failed for appId $appId", e)
        }

        return emptyList()
    }

    /**
     * Fetches version history and changelogs for an application from RuStore.
     */
    suspend fun getVersionHistory(packageName: String): List<Report> = withContext(Dispatchers.IO) {
        try {
            var appId = appIdCache[packageName]
            if (appId == null) {
                getAppDetails(packageName)
                appId = appIdCache[packageName]
            }
            if (appId == null) return@withContext emptyList()
            val url = "$BASE_BACKAPI/applicationData/allAppVersionWhatsNew/$appId"
            val raw = get(url)
            val envelope = defaultJson.decodeFromString<RuStoreEnvelope<RuStoreVersionHistoryBody>>(raw)
            val list = envelope.body?.content ?: return@withContext emptyList()
            list.mapIndexed { index, item ->
                Report(
                    id = index + 1,
                    version = item.versionName,
                    versionCode = item.versionCode.toString(),
                    updatedAt = item.appVerUpdatedAt.orEmpty()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "getVersionHistory failed for $packageName", e)
            emptyList()
        }
    }

    /**
     * Fetches all categories available in RuStore.
     */
    suspend fun getAllCategories(type: Category.Type = Category.Type.APPLICATION): List<Category> = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_BACKAPI/applicationData/allCategory"
            val raw = get(url)
            val envelope = defaultJson.decodeFromString<RuStoreEnvelope<RuStoreCategoriesBody>>(raw)
            val body = envelope.body ?: return@withContext emptyList()
            val list = if (type == Category.Type.GAME) body.contentGames else body.content
            list.distinctBy { it.category }.map { item ->
                Category(
                    title = item.categoryName,
                    imageUrl = item.iconUrl,
                    browseUrl = item.category,
                    color = item.iconColor,
                    type = type
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "getAllCategories failed", e)
            emptyList()
        }
    }

    /**
     * Fetches apps within a specific category.
     */
    suspend fun getCategoryApps(
        category: String,
        limit: Int = 20,
        continuation: String? = null
    ): List<App> = withContext(Dispatchers.IO) {
        try {
            var url = "$BASE_BACKAPI/v1/categories/$category/apps?limit=$limit"
            if (!continuation.isNullOrBlank()) {
                url += "&continuation=$continuation"
            }
            val raw = get(url)
            val page = defaultJson.decodeFromString<RuStoreCategoryContentPage>(raw)
            page.elements.map { item ->
                rememberAppId(item.packageName, item.appId)
                App(
                    id = (item.appId and 0x7FFFFFFFL).toInt(),
                    packageName = item.packageName,
                    displayName = item.appName,
                    shortDescription = item.shortDescription,
                    iconArtwork = Artwork(url = item.iconUrl),
                    versionCode = item.versionCode,
                    isFree = item.price == 0,
                    labeledRating = if (item.averageUserRating > 0f) String.format(Locale.US, "%.1f", item.averageUserRating) else "",
                    rating = buildRating(item.averageUserRating, item.totalRatings)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "getCategoryApps failed for $category", e)
            emptyList()
        }
    }

    fun invalidateSections(tab: String? = null) {
        if (tab != null) {
            sectionsCache.remove(tab)
        } else {
            sectionsCache.clear()
        }
    }

    /**
     * Fetches showcase home stream sections (e.g. APPS, GAMES, INTERESTING).
     * Safely handles polymorphic sections and banner carousels without schema mismatch.
     */
    suspend fun getSections(
        tab: String = "APPS",
        limit: Int = 10,
        continuation: String? = null,
        forceRefresh: Boolean = false
    ): StreamBundle = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (continuation == null && !forceRefresh) {
            val cached = sectionsCache[tab]
            if (cached != null) {
                return@withContext cached.second
            }
        }
        try {
            var url = "$BASE_API/v5/showcase/sections/$tab?limit=$limit"
            if (!continuation.isNullOrBlank()) {
                url += "&continuation=$continuation"
            }
            val raw = get(url)
            val root = defaultJson.parseToJsonElement(raw).jsonObject
            val body = root["body"]?.let { if (it is JsonObject) it else null } ?: root
            val elements = body["elements"]?.let { if (it is JsonArray) it else null } ?: JsonArray(emptyList())
            val continuationToken = body["continuation"]?.jsonPrimitive?.contentOrNull.orEmpty()

            val clusters = mutableMapOf<Int, StreamCluster>()
            var clusterIndex = 1

            for (elemJson in elements) {
                if (elemJson !is JsonObject) continue
                val sel = elemJson["selection"]?.let { if (it is JsonObject) it else null } ?: continue
                val titleObj = sel["title"]?.let { if (it is JsonObject) it else null }
                val title = titleObj?.get("value")?.jsonPrimitive?.contentOrNull
                    ?.takeIf { it.isNotBlank() } ?: continue
                val subtitleObj = sel["subtitle"]?.let { if (it is JsonObject) it else null }
                val subtitle = subtitleObj?.get("value")?.jsonPrimitive?.contentOrNull.orEmpty()

                val contentObj = sel["content"]?.let { if (it is JsonObject) it else null } ?: continue
                val appInfoArr = contentObj["appInfo"]?.let { if (it is JsonArray) it else null } ?: continue

                val packageNames = appInfoArr.mapNotNull { item ->
                    if (item is JsonObject) {
                        item["packageName"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                    } else null
                }.take(15)

                if (packageNames.isNotEmpty()) {
                    val apps = getBatchApps(packageNames)
                    if (apps.isNotEmpty()) {
                        clusters[clusterIndex] = StreamCluster(
                            id = clusterIndex,
                            clusterTitle = title,
                            clusterSubtitle = subtitle,
                            clusterAppList = apps
                        )
                        clusterIndex++
                    }
                }
            }

            Log.i(TAG, "getSections for $tab successfully loaded ${clusters.size} clusters")

            val bundle = StreamBundle(
                id = 1,
                streamClusters = clusters,
                streamNextPageUrl = continuationToken
            )
            if (continuation == null) {
                sectionsCache[tab] = now to bundle
            }
            bundle
        } catch (e: Exception) {
            Log.e(TAG, "getSections failed for $tab", e)
            StreamBundle(id = 1, streamClusters = emptyMap())
        }
    }

    /**
     * Fetches user reviews and ratings for a package.
     */
    suspend fun getReviews(packageName: String, limit: Int = 20): List<Review> = withContext(Dispatchers.IO) {
        getReviewsWithPagination(packageName, page = 0, pageSize = limit).first
    }

    /**
     * Fetches user reviews with pagination support.
     */
    suspend fun getReviewsWithPagination(
        packageName: String,
        page: Int = 0,
        pageSize: Int = 20,
        sortBy: String = "USEFUL_FIRST"
    ): Pair<List<Review>, Int> = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_BACKAPI/comment/comment?packageName=$packageName&pageNumber=$page&pageSize=$pageSize&sortBy=$sortBy"
            val raw = get(url)
            val envelope = defaultJson.decodeFromString<RuStoreEnvelope<RuStoreCommentsBody>>(raw)
            val body = envelope.body ?: return@withContext emptyList<Review>() to 0
            val list = body.content.map { item ->
                val timeMs = parseCommentDate(item.commentDate)
                val fullText = if (!item.devResponse.isNullOrBlank()) {
                    "${item.commentText}\n\nОтвет разработчика:\n${item.devResponse}"
                } else {
                    item.commentText
                }
                Review(
                    commentId = item.commentId.toString(),
                    userName = item.firstName?.takeIf { it.isNotBlank() } ?: "Пользователь RuStore",
                    userPhotoUrl = "",
                    rating = item.appRating,
                    title = "",
                    comment = fullText,
                    timeStamp = timeMs
                )
            }
            list to body.totalPages
        } catch (e: Exception) {
            Log.e(TAG, "getReviews failed for $packageName", e)
            emptyList<Review>() to 0
        }
    }

    private fun parseCommentDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return System.currentTimeMillis()
        return try {
            val clean = dateStr.replace("T", " ")
            val base = if (clean.length >= 19) clean.substring(0, 19) else clean
            val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            format.parse(base)?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun getDeviceAbis(): List<String> {
        val abis = Build.SUPPORTED_ABIS
        return if (!abis.isNullOrEmpty()) abis.toList() else listOf("arm64-v8a", "armeabi-v7a")
    }

    private fun parseDownloads(text: String): Long {
        if (text.isBlank()) return 0L
        val clean = text.replace(" ", "").replace("+", "").lowercase(Locale.ROOT)
        return try {
            when {
                clean.contains("млн") -> {
                    val num = clean.replace("млн", "").replace(",", ".").trim().toDouble()
                    (num * 1_000_000).toLong()
                }
                clean.contains("тыс") -> {
                    val num = clean.replace("тыс", "").replace(",", ".").trim().toDouble()
                    (num * 1_000).toLong()
                }
                else -> clean.filter { it.isDigit() }.toLongOrNull() ?: 0L
            }
        } catch (_: Exception) {
            0L
        }
    }
}
