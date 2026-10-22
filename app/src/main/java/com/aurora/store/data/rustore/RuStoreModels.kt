/*
 * SPDX-FileCopyrightText: 2026 RuOpenStore
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.data.rustore

import kotlinx.serialization.Serializable

@Serializable
data class RuStoreEnvelope<T>(
    val code: String? = null,
    val message: String? = null,
    val body: T? = null,
    val timestamp: String? = null
)

@Serializable
data class RuStoreSearchBody(
    val content: List<RuStoreSearchItem> = emptyList(),
    val pageNumber: Int = 0,
    val pageSize: Int = 20,
    val totalElements: Int = 0,
    val totalPages: Int = 0
)

@Serializable
data class RuStoreSearchItem(
    val appId: Long = 0,
    val packageName: String = "",
    val appName: String = "",
    val shortDescription: String = "",
    val iconUrl: String = "",
    val appType: String = "MAIN",
    val versionCode: Long = 0,
    val price: Int = 0,
    val averageUserRating: Float = 0f,
    val totalRatings: Long = 0,
    val roundedDownloadsText: String = "",
    val minSdkVersion: Int = 21
)

@Serializable
data class RuStoreAppDetails(
    val appId: Long = 0,
    val packageName: String = "",
    val appName: String = "",
    val categories: List<String> = emptyList(),
    val companyName: String = "",
    val publicCompanyId: String? = null,
    val shortDescription: String = "",
    val fullDescription: String = "",
    val roundedDownloadsText: String = "",
    val fileSize: Long = 0,
    val versionName: String = "",
    val versionCode: Long = 0,
    val minSdkVersion: Int = 21,
    val targetSdkVersion: Int? = null,
    val maxSdkVersion: Int? = null,
    val whatsNew: String = "",
    val iconUrl: String = "",
    val banner: String? = null,
    val fileUrls: List<RuStoreScreenshot> = emptyList(),
    val price: Int = 0,
    val rating: RuStoreRating? = null,
    val developerContacts: RuStoreDeveloperContacts? = null
)

@Serializable
data class RuStoreScreenshot(
    val fileUrl: String = "",
    val url: String = "",
    val deviceType: String? = null,
    val orientation: String? = null,
    val ordinal: Int = 0,
    val type: String? = null,
    val blurhash: String? = null
) {
    val effectiveUrl: String
        get() = fileUrl.ifBlank { url }
}

@Serializable
data class RuStoreRating(
    val average: Float = 0f,
    val votes: Long = 0
)

@Serializable
data class RuStoreBatchRequest(
    val packageNames: List<String>
)

@Serializable
data class RuStoreDeveloperContacts(
    val email: String? = null,
    val website: String? = null
)

@Serializable
data class RuStoreBatchAppItem(
    val id: Long = 0,
    val packageName: String = "",
    val name: String = "",
    val description: String = "",
    val iconUrl: String = "",
    val versionCode: Long = 0,
    val fileSize: Long = 0,
    val price: Int = 0,
    val minSdkVersion: Int = 21,
    val rating: RuStoreRating? = null,
    val screenshots: List<RuStoreScreenshot> = emptyList()
)

@Serializable
data class RuStoreCommentItem(
    val commentId: Long = 0,
    val appRating: Int = 5,
    val commentStatus: String? = null,
    val feedbackType: String? = null,
    val firstName: String? = null,
    val commentDate: String? = null,
    val commentText: String = "",
    val likeCounter: Int = 0,
    val dislikeCounter: Int = 0,
    val userVerCode: Long? = null,
    val devResponse: String? = null,
    val devResponseDate: String? = null
)

@Serializable
data class RuStoreCommentsBody(
    val content: List<RuStoreCommentItem> = emptyList(),
    val pageNumber: Int = 0,
    val pageSize: Int = 0,
    val totalElements: Int = 0,
    val totalPages: Int = 0
)

@Serializable
data class RuStoreCategoriesBody(
    val content: List<RuStoreCategoryItem> = emptyList(),
    val contentGames: List<RuStoreCategoryItem> = emptyList()
)

@Serializable
data class RuStoreCategoryItem(
    val category: String = "",
    val categoryName: String = "",
    val iconUrl: String = "",
    val appCount: Int = 0,
    val iconColor: String = ""
)

@Serializable
data class RuStoreCategoryContentPage(
    val elements: List<RuStoreCategoryElement> = emptyList(),
    val continuation: String? = null
)

@Serializable
data class RuStoreCategoryElement(
    val appId: Long = 0,
    val packageName: String = "",
    val appName: String = "",
    val shortDescription: String = "",
    val iconUrl: String = "",
    val versionCode: Long = 0,
    val averageUserRating: Float = 0f,
    val totalRatings: Long = 0,
    val price: Int = 0
)

@Serializable
data class RuStoreDownloadLinkRequest(
    val appId: Long,
    val firstInstall: Boolean = true,
    val supportedAbis: List<String> = listOf("arm64-v8a", "armeabi-v7a"),
    val screenDensity: Int = 480,
    val supportedLocales: List<String> = listOf("ru"),
    val sdkVersion: Int = 34,
    val withoutSplits: Boolean = true
)

@Serializable
data class RuStoreDownloadLinkResponse(
    val appId: Long = 0,
    val versionCode: Long = 0,
    val versionId: Long = 0,
    val downloadUrls: List<RuStoreDownloadUrlItem> = emptyList()
)

@Serializable
data class RuStoreDownloadUrlItem(
    val url: String = "",
    val size: Long = 0,
    val hash: String? = null
)

@Serializable
data class RuStoreReviewsPage(
    val elements: List<RuStoreReviewItem> = emptyList(),
    val continuation: String? = null
)

@Serializable
data class RuStoreReviewItem(
    val commentId: Long = 0,
    val appRating: Int = 5,
    val packageName: String = "",
    val user: RuStoreReviewUser? = null,
    val text: String = "",
    val createdAt: String = "",
    val userVerCode: Long = 0,
    val devResponse: RuStoreDevResponse? = null
)

@Serializable
data class RuStoreReviewUser(
    val id: Long = 0,
    val name: String = "",
    val avatarUrl: String = ""
)

@Serializable
data class RuStoreDevResponse(
    val text: String = "",
    val createdAt: String = ""
)

@Serializable
data class RuStoreSectionsPage(
    val elements: List<RuStoreSectionElement> = emptyList(),
    val continuation: String? = null
)

@Serializable
data class RuStoreSectionElement(
    val type: String = "",
    val selection: RuStoreSelection? = null
)

@Serializable
data class RuStoreSelection(
    val id: String = "",
    val displayType: String = "",
    val title: RuStoreTextValue? = null,
    val subtitle: RuStoreTextValue? = null,
    val content: RuStoreSelectionContent? = null
)

@Serializable
data class RuStoreTextValue(
    val value: String = ""
)

@Serializable
data class RuStoreSelectionContent(
    val contentId: String = "",
    val title: String = "",
    val appInfo: List<RuStoreSelectionAppInfo> = emptyList()
)

@Serializable
data class RuStoreSelectionAppInfo(
    val packageName: String? = null,
    val type: String = ""
)

@Serializable
data class RuStoreVersionItem(
    val versionName: String = "",
    val versionCode: Long = 0L,
    val whatsNew: String? = null,
    val appVerUpdatedAt: String? = null,
    val status: String? = null
)

@Serializable
data class RuStoreVersionHistoryBody(
    val content: List<RuStoreVersionItem> = emptyList(),
    val pageNumber: Int = 0,
    val pageSize: Int = 0,
    val totalElements: Int = 0,
    val totalPages: Int = 0
)

@Serializable
data class RuStoreDevAppItem(
    val appId: Long = 0L,
    val packageName: String = "",
    val versionCode: Long = 0L,
    val minSdkVersion: Int = 24,
    val appName: String = "",
    val appType: String = "MAIN",
    val shortDescription: String = "",
    val iconUrl: String = "",
    val banner: String = "",
    val price: Int = 0,
    val isPurchased: Boolean = false,
    val averageUserRating: Float = 0f,
    val totalRatings: Long = 0L
)

@Serializable
data class RuStoreDevAppsBody(
    val elementsCount: Int = 0,
    val continuation: String? = null,
    val elements: List<RuStoreDevAppItem> = emptyList()
)
