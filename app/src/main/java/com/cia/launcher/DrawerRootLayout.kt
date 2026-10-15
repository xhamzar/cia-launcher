package com.cia.launcher

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import kotlin.math.abs

/**
 * Root layout of the app drawer. Gives swipe gestures first crack at touch
 * events (via onInterceptTouchEvent) so they work anywhere on screen:
 *
 *  - Horizontal swipe (left/right)  -> switch category tab.
 *  - Pull DOWN while the list is at the very top -> close the drawer,
 *    with the content following the finger for visual feedback.
 *
 * Taps, vertical scrolling and the A-Z fast-scroll strip are never stolen:
 * interception only happens once a gesture is clearly a horizontal swipe or
 * a pull-down, otherwise events flow to children untouched.
 */
class DrawerRootLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    interface Callbacks {
        /** True when the app list cannot scroll up any further (at top). */
        fun isAtTop(): Boolean
        fun onPullDownClose()
        fun onSwipeTabLeft()   // user swiped left  -> next tab
        fun onSwipeTabRight()  // user swiped right -> previous tab
    }

    var callbacks: Callbacks? = null
    /** View whose touches must never be intercepted (A-Z fast-scroll strip). */
    var exclusionView: View? = null

    private var downX = 0f
    private var downY = 0f
    private var tracking = false
    private var mode = MODE_UNDECIDED
    private var interceptedHorizontal = false
    private var lastDx = 0f

    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    // Forgiving thresholds: casual swipes are shorter and slower than before.
    private val horizontalConfirm = 56 * density   // intercept once clearly horizontal
    private val horizontalTrigger = 40 * density   // min travel at release to switch
    private val pullCloseThreshold = 110 * density // pull distance to close
    private val pullMaxOffset = 200 * density       // visual cap for follow-finger

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Never fight the A-Z strip: touches starting there belong to it.
                val ex = exclusionView
                if (ex != null && ex.visibility == VISIBLE) {
                    val loc = IntArray(2)
                    ex.getLocationOnScreen(loc)
                    val rawX = ev.rawX
                    if (rawX >= loc[0] - 8 * density &&
                        rawX <= loc[0] + ex.width + 8 * density
                    ) {
                        tracking = false
                        return false
                    }
                }
                downX = ev.x
                downY = ev.y
                lastDx = 0f
                tracking = true
                mode = MODE_UNDECIDED
                interceptedHorizontal = false
                // Cancel any in-flight spring-back so a new gesture starts clean.
                animate().cancel()
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // Multi-touch: bail out, reset any visual offset.
                tracking = false
                mode = MODE_UNDECIDED
                springBack()
            }
            MotionEvent.ACTION_MOVE -> if (tracking && !interceptedHorizontal) {
                val dx = ev.x - downX
                val dy = ev.y - downY
                val adx = abs(dx)
                val ady = abs(dy)
                lastDx = dx

                if (mode == MODE_UNDECIDED) {
                    when {
                        adx > touchSlop && adx > ady * 1.4f -> mode = MODE_HORIZONTAL
                        dy > touchSlop && ady > adx * 1.4f &&
                            callbacks?.isAtTop() == true -> mode = MODE_PULLDOWN
                        adx > touchSlop || ady > touchSlop ->
                            tracking = false // plain vertical scroll / diagonal: leave alone
                    }
                }

                if (mode == MODE_HORIZONTAL && adx > horizontalConfirm) {
                    // Take over: child (RecyclerView) gets CANCEL, stopping scroll.
                    interceptedHorizontal = true
                    return true
                }
                if (mode == MODE_PULLDOWN) {
                    if (dy < -touchSlop) {
                        // User reversed upward: cancel pull, spring back.
                        tracking = false
                        mode = MODE_UNDECIDED
                        springBack()
                    } else {
                        // Visual feedback: content follows the finger.
                        translationY = (dy * 0.45f).coerceAtMost(pullMaxOffset)
                        if (dy > pullCloseThreshold) {
                            tracking = false
                            animateClose()
                            return true // swallow the rest; we're closing anyway
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (mode == MODE_PULLDOWN && translationY > 0f && !interceptedHorizontal) {
                    springBack()
                }
                tracking = false
                mode = MODE_UNDECIDED
            }
        }
        return interceptedHorizontal
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        // Only reached after we intercepted a horizontal swipe.
        if (!interceptedHorizontal) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> lastDx = ev.x - downX
            MotionEvent.ACTION_UP -> {
                val dx = lastDx
                if (abs(dx) > horizontalTrigger) {
                    if (dx < 0) callbacks?.onSwipeTabLeft()
                    else callbacks?.onSwipeTabRight()
                }
                interceptedHorizontal = false
                tracking = false
                mode = MODE_UNDECIDED
            }
            MotionEvent.ACTION_CANCEL -> {
                interceptedHorizontal = false
                tracking = false
                mode = MODE_UNDECIDED
            }
        }
        return true
    }

    /** Reset any leftover offset when the drawer is shown again. */
    fun resetGestureState() {
        animate().cancel()
        translationY = 0f
        tracking = false
        mode = MODE_UNDECIDED
        interceptedHorizontal = false
    }

    private fun springBack() {
        animate().translationY(0f)
            .setDuration(180)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun animateClose() {
        // Slide the whole drawer down off-screen, then close.
        // The content STAYS off-screen (no snap-back): the activity finishes
        // with no system animation, so the finger-follow drag flows directly
        // into the close with zero visual jump.
        animate().translationY(height.toFloat())
            .setDuration(150)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                callbacks?.onPullDownClose()
            }
            .start()
    }

    companion object {
        private const val MODE_UNDECIDED = 0
        private const val MODE_HORIZONTAL = 1
        private const val MODE_PULLDOWN = 2
    }
}
