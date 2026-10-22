/*
 * SPDX-FileCopyrightText: 2021 Aurora OSS
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.viewmodel.homestream

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aurora.extensions.TAG
import com.aurora.gplayapi.data.models.StreamBundle
import com.aurora.gplayapi.data.models.StreamCluster
import com.aurora.gplayapi.helpers.contracts.StreamContract
import com.aurora.store.HomeStash
import com.aurora.store.data.model.ViewState
import com.aurora.store.data.rustore.RuStoreClient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@HiltViewModel
class StreamViewModel @Inject constructor(
    private val streamContract: StreamContract,
    private val ruStoreClient: RuStoreClient
) : ViewModel() {

    val liveData: MutableLiveData<ViewState> = MutableLiveData()

    private val stash: HomeStash = mutableMapOf()

    // Mutex to protect stash access for thread safety
    private val stashMutex = Mutex()

    fun refresh(category: StreamContract.Category) {
        viewModelScope.launch(Dispatchers.IO) {
            stashMutex.withLock {
                stash.remove(category)
            }
            val tab = when (category) {
                StreamContract.Category.GAME -> "GAMES"
                else -> "APPS"
            }
            ruStoreClient.invalidateSections(tab)
            liveData.postValue(ViewState.Loading)
            loadInitialBundle(category, StreamContract.Type.HOME)
        }
    }

    fun getStreamBundle(
        category: StreamContract.Category,
        type: StreamContract.Type,
        forceRefresh: Boolean = false
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            stashMutex.withLock {
                val bundle = targetBundle(category)
                if (!forceRefresh && bundle.hasCluster()) {
                    liveData.postValue(ViewState.Success(stash.toMap()))
                    return@launch
                }
            }
            liveData.postValue(ViewState.Loading)
            loadInitialBundle(category, type)
        }
    }

    private suspend fun loadInitialBundle(
        category: StreamContract.Category,
        type: StreamContract.Type
    ) {
        try {
            val initialBundle = streamContract.fetch(type, category)
            stashMutex.withLock {
                stash[category] = initialBundle
                liveData.postValue(ViewState.Success(stash.toMap()))
            }
        } catch (e: Exception) {
            liveData.postValue(ViewState.Error(e.message))
        }
    }

    fun observe(category: StreamContract.Category, type: StreamContract.Type) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                stashMutex.withLock {
                    val bundle = targetBundle(category)

                    if (!bundle.hasCluster()) {
                        liveData.postValue(ViewState.Loading)
                        val initialBundle = streamContract.fetch(type, category)
                        stash[category] = initialBundle
                        liveData.postValue(ViewState.Success(stash.toMap()))
                        return@launch
                    }

                    if (bundle.hasNext()) {
                        // Fetch next stream bundle page
                        val newBundle = streamContract.nextStreamBundle(
                            bundle.id,
                            category,
                            bundle.streamNextPageUrl
                        )

                        val mergedBundle = bundle.copy(
                            streamClusters = bundle.streamClusters + newBundle.streamClusters,
                            streamNextPageUrl = newBundle.streamNextPageUrl
                        )
                        stash[category] = mergedBundle

                        // Post updated to UI
                        liveData.postValue(ViewState.Success(stash.toMap()))
                    } else {
                        Log.i(TAG, "End of Bundle")
                    }
                }
            } catch (e: Exception) {
                liveData.postValue(ViewState.Error(e.message))
            }
        }
    }

    fun observeCluster(category: StreamContract.Category, streamCluster: StreamCluster) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (streamCluster.hasNext()) {
                    val newCluster = streamContract.nextStreamCluster(
                        streamCluster.id,
                        streamCluster.clusterNextPageUrl
                    )

                    stashMutex.withLock {
                        updateCluster(category, streamCluster.id, newCluster)
                    }

                    liveData.postValue(ViewState.Success(stash.toMap()))
                } else {
                    stashMutex.withLock {
                        postClusterEnd(category, streamCluster.id)
                    }

                    liveData.postValue(ViewState.Success(stash.toMap()))
                }
            } catch (e: Exception) {
                liveData.postValue(ViewState.Error(e.message))
            }
        }
    }

    private fun updateCluster(
        category: StreamContract.Category,
        clusterID: Int,
        newCluster: StreamCluster
    ) {
        val bundle = stash[category] ?: return
        val oldCluster = bundle.streamClusters[clusterID] ?: return

        val mergedCluster = oldCluster.copy(
            clusterNextPageUrl = newCluster.clusterNextPageUrl,
            clusterAppList = (oldCluster.clusterAppList + newCluster.clusterAppList)
                .distinctBy { it.packageName }
        )

        val updatedClusters = bundle.streamClusters.toMutableMap().apply {
            this[clusterID] = mergedCluster
        }

        stash[category] = bundle.copy(streamClusters = updatedClusters)
    }

    private fun postClusterEnd(category: StreamContract.Category, clusterID: Int) {
        val bundle = stash[category] ?: return
        val oldCluster = bundle.streamClusters[clusterID] ?: return

        val updatedCluster = oldCluster.copy(clusterNextPageUrl = "")
        val updatedClusters = bundle.streamClusters.toMutableMap().apply {
            this[clusterID] = updatedCluster
        }

        stash[category] = bundle.copy(streamClusters = updatedClusters)
    }

    private fun targetBundle(category: StreamContract.Category): StreamBundle =
        stash.getOrPut(category) {
            StreamBundle(id = category.value.hashCode())
        }
}
