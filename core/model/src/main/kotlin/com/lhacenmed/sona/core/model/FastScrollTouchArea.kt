package com.lhacenmed.sona.core.model

/**
 * How wide the strip along a list's edge is that grabs the fast scroller's thumb: the wider it is, the
 * further from the edge a drag fast-scrolls instead of scrolling the list. [STANDARD] is Auxio's.
 */
enum class FastScrollTouchArea {
    NARROW,
    STANDARD,
    WIDE,
}
