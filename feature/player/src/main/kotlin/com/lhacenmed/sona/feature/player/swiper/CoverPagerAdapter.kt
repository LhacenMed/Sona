package com.lhacenmed.sona.feature.player.swiper

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
 * `CoverPagerAdapter`.
 *
 * @param onTap Called for a tap on a cover, as the finger lifts.
 */
internal class CoverPagerAdapter(
    private val cornerRadius: Dp,
    private val onTap: () -> Unit,
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
        CoverViewHolder.from(parent, cornerRadius, onTap)

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
            onTap: () -> Unit,
        ): CoverViewHolder {
            val page =
                MaskableFrameLayout(parent.context).apply {
                    layoutParams =
                        ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    // A click, so it answers as the finger lifts and is announced as one; the page has no ripple to show.
                    // Only taps are the page's: the pager still intercepts a swipe that starts on it, and the player
                    // sheet still takes a vertical drag.
                    setOnClickListener { onTap() }
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
