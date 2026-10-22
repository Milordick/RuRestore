/*
 * SPDX-FileCopyrightText: 2025 The Calyx Institute
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.compose.composable.details

import android.text.format.DateUtils
import android.widget.RatingBar
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.aurora.gplayapi.data.models.Review
import com.aurora.store.R
import com.aurora.store.compose.preview.ReviewPreviewProvider
import com.aurora.store.compose.preview.ThemePreviewProvider
import kotlin.math.abs

/**
 * Avatar for review author. Renders user photo if available, or a stylized monogram circle
 * with the user's initial uppercase letter and a deterministic Material palette color.
 */
@Composable
fun ReviewAvatar(
    userName: String,
    userPhotoUrl: String,
    modifier: Modifier = Modifier
) {
    val size = dimensionResource(R.dimen.icon_size_small)
    val shape = CircleShape

    val cleanName = userName.trim()
    val initial = cleanName.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()

    val palette = remember {
        listOf(
            Color(0xFF1E88E5), // Blue
            Color(0xFF43A047), // Green
            Color(0xFFE53935), // Red
            Color(0xFFFB8C00), // Orange
            Color(0xFF8E24AA), // Purple
            Color(0xFF00ACC1), // Cyan
            Color(0xFF3949AB), // Indigo
            Color(0xFFD81B60), // Pink
            Color(0xFF00897B), // Teal
            Color(0xFF5E35B1)  // Deep Purple
        )
    }
    val hash = abs(cleanName.hashCode())
    val bgColor = palette[hash % palette.size]

    if (userPhotoUrl.isNotBlank()) {
        val painter = rememberAsyncImagePainter(
            model = ImageRequest.Builder(LocalContext.current)
                .data(userPhotoUrl)
                .crossfade(true)
                .build()
        )
        val state by painter.state.collectAsStateWithLifecycle()

        if (state is AsyncImagePainter.State.Success) {
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = modifier
                    .requiredSize(size)
                    .clip(shape)
            )
        } else {
            MonogramAvatar(
                initial = initial,
                bgColor = bgColor,
                modifier = modifier
                    .requiredSize(size)
                    .clip(shape)
            )
        }
    } else {
        MonogramAvatar(
            initial = initial,
            bgColor = bgColor,
            modifier = modifier
                .requiredSize(size)
                .clip(shape)
        )
    }
}

@Composable
private fun MonogramAvatar(
    initial: Char?,
    bgColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        if (initial != null) {
            Text(
                text = initial.toString(),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_account),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.requiredSize(24.dp)
            )
        }
    }
}

/**
 * Composable for viewing a review about an app
 * @param modifier The modifier to be applied to the composable
 * @param review [Review] about an app
 */
@Composable
fun ReviewListItem(modifier: Modifier = Modifier, review: Review) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = dimensionResource(R.dimen.spacing_medium),
                vertical = dimensionResource(R.dimen.spacing_small)
            )
    ) {
        ReviewAvatar(
            userName = review.userName,
            userPhotoUrl = review.userPhotoUrl
        )
        Column(
            modifier = Modifier.padding(horizontal = dimensionResource(R.dimen.spacing_small))
        ) {
            Text(
                text = review.userName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = DateUtils.formatDateTime(
                    LocalContext.current,
                    review.timeStamp,
                    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR
                ),
                style = MaterialTheme.typography.bodySmall
            )
            AndroidView(
                factory = { context ->
                    RatingBar(context, null, android.R.attr.ratingBarStyleSmall)
                },
                update = { view ->
                    view.rating = review.rating.toFloat()
                }
            )
            Text(
                text = review.comment,
                style = MaterialTheme.typography.bodySmall,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@Preview(showBackground = true)
@Composable
private fun ReviewListItemPreview(@PreviewParameter(ReviewPreviewProvider::class) review: Review) {
    ReviewListItem(review = review)
}
