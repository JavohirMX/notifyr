package com.javohirmx.notifyr.ui.history

internal fun shouldShowScrollToTopButton(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    indexThreshold: Int,
    offsetThresholdPx: Int
): Boolean {
    return firstVisibleItemIndex > indexThreshold ||
        (firstVisibleItemIndex == indexThreshold && firstVisibleItemScrollOffset > offsetThresholdPx)
}
