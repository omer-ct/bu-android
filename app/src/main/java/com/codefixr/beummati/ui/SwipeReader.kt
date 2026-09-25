package com.codefixr.beummati.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codefixr.beummati.data.ContentBrowseMode
import kotlinx.coroutines.launch

@Composable
fun BrowseModeBar(
    mode: ContentBrowseMode,
    onMode: (ContentBrowseMode) -> Unit,
    modifier: Modifier = Modifier
) {
    SingleChoiceSegmentedButtonRow(modifier) {
        ContentBrowseMode.entries.forEachIndexed { index, option ->
            SegmentedButton(
                selected = mode == option,
                onClick = { onMode(option) },
                shape = SegmentedButtonDefaults.itemShape(index, ContentBrowseMode.entries.size),
                label = { Text(option.label) }
            )
        }
    }
}

/**
 * One item per page with horizontal swipe (+ prev/next).
 * Each page scrolls vertically when content is tall.
 */
@Composable
fun <T> SwipeItemPager(
    items: List<T>,
    pagerState: PagerState,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    label: (index: Int, item: T) -> String = { i, _ -> "${i + 1} / ${items.size}" },
    showChrome: Boolean = true,
    pageContent: @Composable (item: T, index: Int) -> Unit
) {
    if (items.isEmpty()) return
    val scope = rememberCoroutineScope()

    Column(modifier.fillMaxSize()) {
        if (showChrome) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = {
                        val prev = (pagerState.currentPage - 1).coerceAtLeast(0)
                        scope.launch { pagerState.animateScrollToPage(prev) }
                    },
                    enabled = pagerState.currentPage > 0
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous")
                }
                val page = pagerState.currentPage.coerceIn(0, items.lastIndex)
                Text(
                    label(page, items[page]),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(
                    onClick = {
                        val next = (pagerState.currentPage + 1).coerceAtMost(items.lastIndex)
                        scope.launch { pagerState.animateScrollToPage(next) }
                    },
                    enabled = pagerState.currentPage < items.lastIndex
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next")
                }
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            beyondViewportPageCount = 1,
            key = { page -> key(items[page]) },
            contentPadding = PaddingValues(horizontal = 12.dp)
        ) { page ->
            Box(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp, top = 4.dp)
            ) {
                pageContent(items[page], page)
            }
        }
    }
}
