# The cover swiper reaches into ViewPager2's RecyclerView and loosens its touch slop, as Auxio does
# (swiper/ViewPagerUtil.kt). Both are private fields read by name, so a minified build must keep them.
-keepclassmembers class androidx.viewpager2.widget.ViewPager2 {
    androidx.recyclerview.widget.RecyclerView mRecyclerView;
}
-keepclassmembers class androidx.recyclerview.widget.RecyclerView {
    int mTouchSlop;
}
