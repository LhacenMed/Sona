package com.lhacenmed.sona.feature.player.swiper

import android.annotation.SuppressLint
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.Dp
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.carousel.MaskableFrameLayout
import com.google.android.material.shape.ShapeAppearanceModel
import com.lhacenmed.sona.core.designsystem.component.SonaCoverImage
import com.lhacenmed.sona.feature.player.QueueTrack

/**
 * The queue's covers, one page per slot, for [CarouselTransformer] to mask. Auxio's
 * `CoverPagerAdapter`, with a double tap on either half of a cover in place of its tap.
 *
 * @param onDoubleTap Called with whether the tap landed on the half that seeks backward.
 */
internal class CoverPagerAdapter(
    private val cornerRadius: Dp,
    private val onDoubleTap: (isBackward: Boolean) -> Unit,
) : ListAdapter<QueueTrack, CoverViewHolder>(CoverViewHolder.DIFF_CALLBACK) {

    /**
     * Round mode, for the masks: every page's is cut to it, the pages already shown included, as it
     * changes. The cover inside follows it of its own accord, as every cover does.
     */
    var isRounded: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            notifyItemRangeChanged(0, itemCount, MaskPayload)
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        CoverViewHolder.from(parent, cornerRadius, onDoubleTap)

    override fun onBindViewHolder(holder: CoverViewHolder, position: Int) {
        holder.bind(getItem(position))
        holder.cutMask(isRounded)
    }

    override fun onBindViewHolder(holder: CoverViewHolder, position: Int, payloads: List<Any>) {
        // Only the mask changed: the cover is left as it is rather than bound again.
        if (payloads.isNotEmpty() && payloads.all { it == MaskPayload }) {
            holder.cutMask(isRounded)
        } else {
            onBindViewHolder(holder, position)
        }
    }

    private companion object {
        val MaskPayload = Any()
    }
}

/**
 * A page of [CoverPagerAdapter]: a [MaskableFrameLayout], which [CarouselTransformer] masks, around
 * the cover it moves for its parallax - the layout of Auxio's `item_cover.xml`.
 */
internal class CoverViewHolder private constructor(
    private val page: MaskableFrameLayout,
    private val cornerRadius: Dp,
) : RecyclerView.ViewHolder(page) {

    private var coverArtUri by mutableStateOf<String?>(null)

    init {
        page.addView(
            ComposeView(page.context).apply {
                setContent {
                    SonaCoverImage(
                        coverArtUri = coverArtUri,
                        contentDescription = null,
                        cornerRadius = cornerRadius,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            },
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
    }

    fun bind(item: QueueTrack) {
        coverArtUri = item.track.coverArtUri
    }

    /**
     * The mask is what shows, so it is the mask that carries the cover's corners - every sliver of a
     * cover mid-swipe is rounded, as a carousel's items are - and square while round mode is off.
     */
    fun cutMask(isRounded: Boolean) {
        val cornerSizePx = if (isRounded) cornerRadius.value * page.resources.displayMetrics.density else 0f
        page.shapeAppearanceModel = ShapeAppearanceModel.builder().setAllCornerSizes(cornerSizePx).build()
    }

    companion object {
        fun from(
            parent: ViewGroup,
            cornerRadius: Dp,
            onDoubleTap: (isBackward: Boolean) -> Unit,
        ): CoverViewHolder {
            val page =
                MaskableFrameLayout(parent.context).apply {
                    layoutParams =
                        ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    setOnDoubleTapListener(onDoubleTap)
                }
            return CoverViewHolder(page, cornerRadius)
        }

        /** Covers are one slot of the queue each, and a slot keeps its key wherever it moves. */
        val DIFF_CALLBACK =
            object : DiffUtil.ItemCallback<QueueTrack>() {
                override fun areItemsTheSame(oldItem: QueueTrack, newItem: QueueTrack) =
                    oldItem.entry.key == newItem.entry.key

                override fun areContentsTheSame(oldItem: QueueTrack, newItem: QueueTrack) =
                    oldItem.track.coverArtUri == newItem.track.coverArtUri
            }
    }
}

/**
 * Reports a double tap and on which half of the page it landed. Only taps are the page's: the pager
 * still intercepts a swipe that starts on it, and the player sheet still takes a vertical drag.
 */
@SuppressLint("ClickableViewAccessibility")
private fun View.setOnDoubleTapListener(onDoubleTap: (isBackward: Boolean) -> Unit) {
    val detector =
        GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent) = true

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    val isOnLeftHalf = e.x < width / 2f
                    onDoubleTap(if (layoutDirection == View.LAYOUT_DIRECTION_RTL) !isOnLeftHalf else isOnLeftHalf)
                    return true
                }
            },
        )
    setOnTouchListener { _, event -> detector.onTouchEvent(event) }
}
