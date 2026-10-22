/*
 * SPDX-FileCopyrightText: 2026 Aurora OSS
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.compose.ui.commons

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aurora.store.BuildConfig
import com.aurora.store.R
import com.aurora.store.compose.navigation.Destination

private data class MoreItem(
    @StringRes val titleRes: Int,
    @DrawableRes val iconRes: Int,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreSheet(
    onDismiss: () -> Unit,
    onNavigateTo: (Destination) -> Unit = {}
) {
    val mainItems = listOf(
        MoreItem(R.string.title_apps_games, R.drawable.ic_apps) {
            onNavigateTo(Destination.Installed)
        },
        MoreItem(R.string.title_blacklist_manager, R.drawable.ic_blacklist) {
            onNavigateTo(Destination.Blacklist)
        },
        MoreItem(R.string.title_favourites_manager, R.drawable.ic_favorite_unchecked) {
            onNavigateTo(Destination.Favourite)
        },
        MoreItem(R.string.title_spoof_manager, R.drawable.ic_spoof) {
            onNavigateTo(Destination.Spoof)
        }
    )
    val extraItems = listOf(
        MoreItem(R.string.title_settings, R.drawable.ic_menu_settings) {
            onNavigateTo(Destination.Settings)
        },
        MoreItem(R.string.title_about, R.drawable.ic_menu_about) { onNavigateTo(Destination.About) }
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_aurora),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalDivider()

        Column(
            Modifier.padding(
                horizontal = dimensionResource(R.dimen.spacing_small),
                vertical = dimensionResource(R.dimen.spacing_small)
            )
        ) {
            mainItems.forEach { item ->
                NavigationDrawerItem(
                    icon = {
                        Icon(
                            painter = painterResource(item.iconRes),
                            contentDescription = null
                        )
                    },
                    label = { Text(stringResource(item.titleRes)) },
                    selected = false,
                    onClick = {
                        item.onClick()
                        onDismiss()
                    }
                )
            }
        }

        HorizontalDivider()

        Column(
            Modifier.padding(
                horizontal = dimensionResource(R.dimen.spacing_small),
                vertical = dimensionResource(R.dimen.spacing_small)
            )
        ) {
            extraItems.forEach { item ->
                NavigationDrawerItem(
                    icon = {
                        Icon(
                            painter = painterResource(item.iconRes),
                            contentDescription = null
                        )
                    },
                    label = { Text(stringResource(item.titleRes)) },
                    selected = false,
                    onClick = {
                        item.onClick()
                        onDismiss()
                    }
                )
            }
        }

        Spacer(Modifier.navigationBarsPadding())
    }
}
