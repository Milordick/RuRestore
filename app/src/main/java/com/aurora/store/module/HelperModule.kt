/*
 * SPDX-FileCopyrightText: 2026 RuOpenStore
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.module

import com.aurora.gplayapi.helpers.contracts.CategoryContract
import com.aurora.gplayapi.helpers.contracts.CategoryStreamContract
import com.aurora.gplayapi.helpers.contracts.SearchContract
import com.aurora.gplayapi.helpers.contracts.StreamContract
import com.aurora.gplayapi.helpers.contracts.TopChartsContract
import com.aurora.gplayapi.helpers.web.WebDataSafetyHelper
import com.aurora.gplayapi.network.IHttpClient
import com.aurora.store.data.providers.SpoofProvider
import com.aurora.store.data.rustore.RuStoreAppDetailsHelper
import com.aurora.store.data.rustore.RuStoreCategoryHelper
import com.aurora.store.data.rustore.RuStoreCategoryStreamHelper
import com.aurora.store.data.rustore.RuStoreClient
import com.aurora.store.data.rustore.RuStoreExpandedBrowseHelper
import com.aurora.store.data.rustore.RuStoreReviewsHelper
import com.aurora.store.data.rustore.RuStoreSearchHelper
import com.aurora.store.data.rustore.RuStoreStreamHelper
import com.aurora.store.data.rustore.RuStoreTopChartsHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Module providing RuStore service adapters to Aurora Store UI contracts.
 */
@Module
@InstallIn(SingletonComponent::class)
object HelperModule {

    @Singleton
    @Provides
    fun providesStreamContract(helper: RuStoreStreamHelper): StreamContract = helper

    @Singleton
    @Provides
    fun providesSearchContract(helper: RuStoreSearchHelper): SearchContract = helper

    @Singleton
    @Provides
    fun providesCategoryStreamContract(helper: RuStoreCategoryStreamHelper): CategoryStreamContract = helper

    @Singleton
    @Provides
    fun providesTopChartsContract(helper: RuStoreTopChartsHelper): TopChartsContract = helper

    @Singleton
    @Provides
    fun providesCategoryContract(helper: RuStoreCategoryHelper): CategoryContract = helper

    @Singleton
    @Provides
    fun providesRuStoreAppDetailsHelper(ruStoreClient: RuStoreClient): RuStoreAppDetailsHelper =
        RuStoreAppDetailsHelper(ruStoreClient)

    @Singleton
    @Provides
    fun providesRuStoreReviewsHelper(ruStoreClient: RuStoreClient): RuStoreReviewsHelper =
        RuStoreReviewsHelper(ruStoreClient)

    @Singleton
    @Provides
    fun providesRuStoreExpandedBrowseHelper(ruStoreClient: RuStoreClient): RuStoreExpandedBrowseHelper =
        RuStoreExpandedBrowseHelper(ruStoreClient)

    @Singleton
    @Provides
    fun providesWebDataSafetyHelperInstance(
        spoofProvider: SpoofProvider,
        httpClient: IHttpClient
    ): WebDataSafetyHelper = WebDataSafetyHelper()
        .using(httpClient)
        .with(spoofProvider.locale)
}
