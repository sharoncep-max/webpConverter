package com.example.webpconverter.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private class ScrollMetrics(val viewport: Float, val content: Float, val offset: Float) {
    val maxScroll: Float get() = (content - viewport).coerceAtLeast(0f)
}

/** Draggable, tappable scrollbar for a [LazyListState]; item sizes are learned as items are measured. */
@Composable
fun VerticalScrollbar(state: LazyListState, modifier: Modifier = Modifier) {
    val knownSizes = remember(state) { HashMap<Int, Int>() }
    val metrics by remember(state) {
        derivedStateOf {
            val info = state.layoutInfo
            val count = info.totalItemsCount
            if (count == 0 || info.visibleItemsInfo.isEmpty()) return@derivedStateOf null
            knownSizes.keys.removeAll { it >= count }
            info.visibleItemsInfo.forEach { knownSizes[it.index] = it.size }
            val average = knownSizes.values.average().toFloat()
            val spacing = info.mainAxisItemSpacing.toFloat()
            val first = state.firstVisibleItemIndex
            var content = (info.beforeContentPadding + info.afterContentPadding) + spacing * (count - 1)
            var offset = state.firstVisibleItemScrollOffset.toFloat()
            for (index in 0 until count) {
                val size = knownSizes[index]?.toFloat() ?: average
                content += size
                if (index < first) offset += size + spacing
            }
            ScrollMetrics(info.viewportSize.height.toFloat(), content, offset)
        }
    }
    val current = metrics ?: return
    if (current.maxScroll < 1f) return

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var dragging by remember { mutableStateOf(false) }
    val active = dragging || state.isScrollInProgress
    val thumbAlpha by animateFloatAsState(if (active) 1f else 0.55f, label = "scrollbarAlpha")
    val barWidth by animateDpAsState(if (active) 8.dp else 5.dp, label = "scrollbarWidth")
    val thumbColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)

    BoxWithConstraints(modifier.width(16.dp)) {
        val trackHeight = constraints.maxHeight.toFloat()
        val minThumb = with(density) { 40.dp.toPx() }.coerceAtMost(trackHeight)
        val thumbHeight = (trackHeight * current.viewport / current.content).coerceIn(minThumb, trackHeight)
        val travel = (trackHeight - thumbHeight).coerceAtLeast(1f)
        val fraction = when {
            !state.canScrollBackward -> 0f
            !state.canScrollForward -> 1f
            else -> (current.offset / current.maxScroll).coerceIn(0f, 1f)
        }
        val latestMetrics by rememberUpdatedState(current)
        val latestTravel by rememberUpdatedState(travel)
        val latestThumb by rememberUpdatedState(thumbHeight)

        Box(
            Modifier.align(Alignment.TopCenter).fillMaxHeight().width(barWidth)
                .clip(CircleShape).background(trackColor)
        )
        Box(
            Modifier.align(Alignment.TopCenter)
                .offset { IntOffset(0, (travel * fraction).roundToInt()) }
                .height(with(density) { thumbHeight.toDp() }).width(barWidth)
                .clip(CircleShape).background(thumbColor.copy(alpha = thumbAlpha))
        )
        Box(
            Modifier.fillMaxSize()
                .pointerInput(state) {
                    detectVerticalDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false }
                    ) { change, dy ->
                        change.consume()
                        state.dispatchRawDelta(dy * latestMetrics.maxScroll / latestTravel)
                    }
                }
                .pointerInput(state) {
                    detectTapGestures { position ->
                        val target = ((position.y - latestThumb / 2) / latestTravel).coerceIn(0f, 1f) *
                            latestMetrics.maxScroll
                        scope.launch { state.animateScrollBy(target - latestMetrics.offset) }
                    }
                }
        )
    }
}
