package com.lhacenmed.sona.feature.player.swiper

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import java.lang.reflect.Field

// Auxio's ViewPager2 helpers (`util/FrameworkUtil.kt`), copied as is. The two fields read here are
// kept through minification by this module's consumer-rules.pro.

private val VP_RECYCLER_FIELD: Field by lazyReflectedField(ViewPager2::class.java, "mRecyclerView")
private val RV_TOUCH_SLOP_FIELD: Field by lazyReflectedField(RecyclerView::class.java, "mTouchSlop")

private fun lazyReflectedField(clazz: Class<*>, field: String) = lazy {
    clazz.getDeclaredField(field).also { it.isAccessible = true }
}

/**
 * Dampen a [ViewPager2] so that vertical scrolls can still easily occur.
 *
 * By default, ViewPager2's sensitivity is high enough to result in vertical scroll events being
 * registered as horizontal scroll events. Reflect into the internal RecyclerView and change the
 * touch slope so that touch actions will act more as a scroll than as a swipe. Derived from:
 * https://al-e-shevelev.medium.com/how-to-reduce-scroll-sensitivity-of-viewpager2-widget-87797ad02414
 */
internal fun ViewPager2.dampen() {
    val recycler = recycler()
    val slop = RV_TOUCH_SLOP_FIELD.get(recycler) as Int
    RV_TOUCH_SLOP_FIELD.set(recycler, slop * 3)
}

/** Reflect into a [ViewPager2]'s internal [RecyclerView]. */
internal fun ViewPager2.recycler() = (VP_RECYCLER_FIELD.get(this) as RecyclerView)

/**
 * Move to [item] using smooth drag gestures instead of the RecyclerView's scroll interpolator,
 * which is far faster/choppier/blunt and doesn't look as good at all.
 */
internal fun ViewPager2.smoothScrollByPageTo(item: Int, durationMs: Int = 300) {
    // Nothing to actually do if there's no data.
    val adapter = adapter ?: return
    if (adapter.itemCount <= 0) {
        return
    }

    val target = item.coerceIn(0, adapter.itemCount - 1)
    val delta = target - currentItem
    if (delta == 0) {
        return
    }

    val recycler = recycler()
    recycler.stopScroll()

    // Note: Assumption is horizonal ViewPager, less logic to manage.
    val direction = if (layoutDirection == View.LAYOUT_DIRECTION_RTL) -1 else 1
    recycler.smoothScrollBy(width * delta * direction, 0, null, durationMs)
}
