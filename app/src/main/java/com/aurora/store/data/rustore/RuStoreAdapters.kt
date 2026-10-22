/*
 * SPDX-FileCopyrightText: 2026 RuOpenStore
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.data.rustore

import android.util.Log
import com.aurora.gplayapi.SearchSuggestEntry
import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.data.models.Category
import com.aurora.gplayapi.data.models.Review
import com.aurora.gplayapi.data.models.ReviewCluster
import com.aurora.gplayapi.data.models.StreamBundle
import com.aurora.gplayapi.data.models.StreamCluster
import com.aurora.gplayapi.data.models.details.DevStream
import com.aurora.gplayapi.data.models.details.TestingProgramStatus
import com.aurora.gplayapi.helpers.contracts.AppDetailsContract
import com.aurora.gplayapi.helpers.contracts.CategoryContract
import com.aurora.gplayapi.helpers.contracts.CategoryStreamContract
import com.aurora.gplayapi.helpers.contracts.SearchContract
import com.aurora.gplayapi.helpers.contracts.StreamContract
import com.aurora.gplayapi.helpers.contracts.TopChartsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adapter that connects Aurora Store's StreamContract to RuStore's showcase sections.
 */
@Singleton
class RuStoreStreamHelper @Inject constructor(
    private val ruStoreClient: RuStoreClient
) : StreamContract {

    override fun fetch(
        type: StreamContract.Type,
        category: StreamContract.Category
    ): StreamBundle = runBlocking(Dispatchers.IO) {
        val tab = when (category) {
            StreamContract.Category.GAME -> "GAMES"
            else -> "APPS"
        }
        ruStoreClient.getSections(tab)
    }

    override fun nextStreamCluster(id: Int, nextPageUrl: String): StreamCluster {
        return StreamCluster.EMPTY
    }

    override fun nextStreamBundle(
        id: Int,
        category: StreamContract.Category,
        nextPageUrl: String
    ): StreamBundle = runBlocking(Dispatchers.IO) {
        if (nextPageUrl.isBlank()) return@runBlocking StreamBundle.EMPTY
        val tab = when (category) {
            StreamContract.Category.GAME -> "GAMES"
            else -> "APPS"
        }
        ruStoreClient.getSections(tab, continuation = nextPageUrl)
    }
}

/**
 * Adapter that connects Aurora Store's SearchContract to RuStore's search API.
 */
@Singleton
class RuStoreSearchHelper @Inject constructor(
    private val ruStoreClient: RuStoreClient
) : SearchContract {

    override fun searchSuggestions(query: String): List<SearchSuggestEntry> {
        if (query.isBlank()) return emptyList()
        return try {
            val (apps, _) = runBlocking(Dispatchers.IO) {
                ruStoreClient.search(query, pageNumber = 0, pageSize = 6)
            }
            apps.map { app ->
                SearchSuggestEntry.newBuilder()
                    .setType(1)
                    .setSuggestedQuery(app.displayName)
                    .setTitle(app.displayName)
                    .build()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun searchResults(query: String, nextPageUrl: String): StreamBundle = runBlocking(Dispatchers.IO) {
        val (apps, totalPages) = ruStoreClient.search(query, pageNumber = 0, pageSize = 20)
        val cluster = StreamCluster(
            id = 1,
            clusterTitle = "Результаты поиска",
            clusterAppList = apps,
            clusterNextPageUrl = if (totalPages > 1) "1" else ""
        )
        StreamBundle(
            id = 1,
            streamClusters = mapOf(1 to cluster),
            streamNextPageUrl = if (totalPages > 1) "1" else ""
        )
    }

    override fun nextStreamBundle(query: String, nextPageUrl: String): StreamBundle = runBlocking(Dispatchers.IO) {
        val page = nextPageUrl.toIntOrNull() ?: 1
        val (apps, totalPages) = ruStoreClient.search(query, pageNumber = page, pageSize = 20)
        val next = page + 1
        val nextUrl = if (next < totalPages) next.toString() else ""
        val cluster = StreamCluster(
            id = page + 1,
            clusterTitle = "Результаты поиска",
            clusterAppList = apps,
            clusterNextPageUrl = nextUrl
        )
        StreamBundle(
            id = page + 1,
            streamClusters = mapOf(1 to cluster),
            streamNextPageUrl = nextUrl
        )
    }

    override fun nextStreamCluster(query: String, nextPageUrl: String): StreamCluster {
        return StreamCluster.EMPTY
    }
}

/**
 * Adapter that connects Aurora Store's CategoryStreamContract to RuStore's category content API.
 */
@Singleton
class RuStoreCategoryStreamHelper @Inject constructor(
    private val ruStoreClient: RuStoreClient
) : CategoryStreamContract {

    override fun fetch(browseUrl: String): StreamBundle = runBlocking(Dispatchers.IO) {
        val apps = ruStoreClient.getCategoryApps(category = browseUrl, limit = 30)
        val cluster = StreamCluster(
            id = 1,
            clusterTitle = browseUrl,
            clusterAppList = apps
        )
        StreamBundle(
            id = 1,
            streamClusters = mapOf(1 to cluster)
        )
    }

    override fun nextStreamCluster(id: Int, nextPageUrl: String): StreamCluster {
        return StreamCluster.EMPTY
    }

    override fun nextStreamBundle(
        id: Int,
        category: StreamContract.Category,
        nextPageUrl: String
    ): StreamBundle = runBlocking(Dispatchers.IO) {
        if (nextPageUrl.isBlank()) return@runBlocking StreamBundle.EMPTY
        val tab = when (category) {
            StreamContract.Category.GAME -> "GAMES"
            else -> "APPS"
        }
        ruStoreClient.getSections(tab, continuation = nextPageUrl)
    }
}

/**
 * Adapter that connects Aurora Store's TopChartsContract to RuStore's popular apps.
 */
@Singleton
class RuStoreTopChartsHelper @Inject constructor(
    private val ruStoreClient: RuStoreClient
) : TopChartsContract {

    override fun getCluster(type: String, chart: String): StreamCluster = runBlocking(Dispatchers.IO) {
        val tab = if (type.contains("GAME", ignoreCase = true)) "GAMES" else "APPS"
        val bundle = ruStoreClient.getSections(tab)
        bundle.streamClusters.values.firstOrNull() ?: StreamCluster.EMPTY
    }

    override fun getNextStreamCluster(id: Int, nextPageUrl: String): StreamCluster {
        return StreamCluster.EMPTY
    }
}

/**
 * Adapter that connects Aurora Store's CategoryContract to RuStore's categories API.
 */
@Singleton
class RuStoreCategoryHelper @Inject constructor(
    private val ruStoreClient: RuStoreClient
) : CategoryContract {

    override fun getAllCategories(type: Category.Type): List<Category> = runBlocking(Dispatchers.IO) {
        ruStoreClient.getAllCategories(type)
    }
}

/**
 * Adapter replacing AppDetailsHelper with RuStore implementation.
 */
@Singleton
class RuStoreAppDetailsHelper @Inject constructor(
    private val ruStoreClient: RuStoreClient
) : AppDetailsContract {

    override fun getAppByPackageName(packageName: String): App = runBlocking(Dispatchers.IO) {
        ruStoreClient.getAppDetails(packageName)
            ?: throw RuntimeException("Приложение $packageName не найдено в RuStore")
    }

    override fun getAppByPackageName(packageNames: List<String>): List<App> = runBlocking(Dispatchers.IO) {
        ruStoreClient.getBatchApps(packageNames)
    }

    fun getAppDetails(packageNames: List<String>): List<App> = runBlocking(Dispatchers.IO) {
        ruStoreClient.getBatchApps(packageNames)
    }

    fun getDetailsStream(id: Int, streamUrl: String): StreamBundle {
        return StreamBundle.EMPTY
    }

    fun getNextStreamCluster(id: Int, streamUrl: String): StreamCluster {
        return StreamCluster.EMPTY
    }

    fun getDeveloperStream(devId: String): DevStream = runBlocking(Dispatchers.IO) {
        val apps = ruStoreClient.getDeveloperApps(devId, limit = 50)
        val cluster = StreamCluster(
            id = 1,
            clusterTitle = devId,
            clusterAppList = apps
        )
        DevStream(
            id = 1,
            title = devId,
            description = "",
            imgUrl = "",
            streamBundle = StreamBundle(id = 1, streamClusters = mapOf(1 to cluster))
        )
    }

    fun testingProgram(packageName: String, subscribe: Boolean): TestingProgramStatus {
        return TestingProgramStatus(false, false)
    }
}

/**
 * Adapter replacing ReviewsHelper with RuStore reviews API.
 */
@Singleton
class RuStoreReviewsHelper @Inject constructor(
    private val ruStoreClient: RuStoreClient
) {
    private fun mapFilterToSortBy(filter: Review.Filter): String {
        return when (filter) {
            Review.Filter.NEWEST -> "NEW_FIRST"
            Review.Filter.POSITIVE, Review.Filter.FIVE, Review.Filter.FOUR -> "POSITIVE_FIRST"
            Review.Filter.CRITICAL, Review.Filter.ONE, Review.Filter.TWO, Review.Filter.THREE -> "NEGATIVE_FIRST"
            else -> "USEFUL_FIRST"
        }
    }

    fun getReviews(
        packageName: String,
        filter: Review.Filter = Review.Filter.ALL,
        offset: Int = 0
    ): ReviewCluster = runBlocking(Dispatchers.IO) {
        val sortBy = mapFilterToSortBy(filter)
        val (reviews, totalPages) = ruStoreClient.getReviewsWithPagination(
            packageName = packageName,
            page = 0,
            pageSize = 20,
            sortBy = sortBy
        )
        ReviewCluster(
            id = 1,
            nextPageUrl = if (totalPages > 1) "$packageName|$sortBy|1" else "",
            reviewList = reviews
        )
    }

    fun getReviewSummary(packageName: String): List<Review> = runBlocking(Dispatchers.IO) {
        ruStoreClient.getReviewsWithPagination(packageName, page = 0, pageSize = 5, sortBy = "USEFUL_FIRST").first
    }

    fun next(reviewsNextPageUrl: String): ReviewCluster = runBlocking(Dispatchers.IO) {
        val parts = reviewsNextPageUrl.split("|")
        val pkg = parts.getOrNull(0).orEmpty()
        val sortBy = if (parts.size >= 3) parts[1] else "USEFUL_FIRST"
        val page = if (parts.size >= 3) parts[2].toIntOrNull() ?: 1 else parts.getOrNull(1)?.toIntOrNull() ?: 1
        if (pkg.isBlank()) return@runBlocking ReviewCluster(1, "", emptyList())

        val (reviews, totalPages) = ruStoreClient.getReviewsWithPagination(
            packageName = pkg,
            page = page,
            pageSize = 20,
            sortBy = sortBy
        )
        val next = page + 1
        ReviewCluster(
            id = page + 1,
            nextPageUrl = if (next < totalPages) "$pkg|$sortBy|$next" else "",
            reviewList = reviews
        )
    }

    fun getUserReview(packageName: String, isTesting: Boolean = false): Review? = null

    fun addOrEditReview(
        packageName: String,
        title: String = "",
        comment: String = "",
        rating: Int = 5,
        isTesting: Boolean = false
    ): Review? = null

    fun deleteReview(packageName: String, isTesting: Boolean = false) {}
}

/**
 * Adapter replacing ExpandedBrowseHelper with RuStore implementation.
 */
@Singleton
class RuStoreExpandedBrowseHelper @Inject constructor(
    private val ruStoreClient: RuStoreClient
) {
    fun getExpandedCluster(browseUrl: String): StreamCluster = runBlocking(Dispatchers.IO) {
        val apps = ruStoreClient.getCategoryApps(category = browseUrl, limit = 30)
        StreamCluster(
            id = 1,
            clusterTitle = browseUrl,
            clusterAppList = apps
        )
    }

    fun getNextExpandedCluster(nextPageUrl: String): StreamCluster {
        return StreamCluster.EMPTY
    }
}
