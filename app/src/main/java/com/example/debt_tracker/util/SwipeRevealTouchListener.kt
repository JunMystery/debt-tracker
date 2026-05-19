package com.example.debt_tracker.util

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs

class SwipeRevealTouchListener(
    val foregroundView: View,
    private val deleteButtonWidthDp: Float = 80f,
    private val onSwipeStateChanged: ((SwipeRevealTouchListener) -> Boolean)? = null
) : View.OnTouchListener {

    private val deleteButtonWidth = (foregroundView.resources.displayMetrics.density * deleteButtonWidthDp).toInt()
    private val swipeThreshold = deleteButtonWidth / 2

    private var startX = 0f
    private var isSwiping = false
    private var currentTranslationX = 0f
    private val touchSlop = ViewConfiguration.get(foregroundView.context).scaledTouchSlop

    val itemView: View
        get() = foregroundView.parent as View

    init {
        foregroundView.translationX = 0f
    }

    fun isOpened(): Boolean = currentTranslationX != 0f

    private fun animateScale(targetScale: Float) {
        foregroundView.animate()
            .scaleX(targetScale)
            .scaleY(targetScale)
            .setDuration(120)
            .start()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.rawX
                isSwiping = false
                val handled = onSwipeStateChanged?.invoke(this) ?: false
                if (handled) {
                    // Consume the touch event so that click/long-click on the target card are blocked
                    return true
                }
                if (isOpened()) {
                    // Consume the touch event so that click/long-click on this card are blocked
                    return true
                }
                animateScale(0.97f)
                // Don't fully consume so that standard click listener on foreground can still run if it's a tap
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.rawX - startX
                // Determine if we should start swiping
                val isSwipeGesture = if (currentTranslationX == 0f) {
                    // Closed card can only be swiped left
                    deltaX < -touchSlop && abs(deltaX) > touchSlop
                } else {
                    // Open card can be swiped left or right (to close it)
                    abs(deltaX) > touchSlop
                }

                if (isSwiping || isSwipeGesture) {
                    if (!isSwiping) {
                        // Cancel pending long press and animate scale back to normal size
                        view.cancelLongPress()
                        animateScale(1.0f)
                    }
                    isSwiping = true
                    view.parent.requestDisallowInterceptTouchEvent(true)
                    // Calculate translation. We only swipe left, so translationX must be <= 0.
                    // Max swipe distance is deleteButtonWidth
                    val newTranslationX = (currentTranslationX + deltaX).coerceIn(-deleteButtonWidth.toFloat(), 0f)
                    foregroundView.translationX = newTranslationX
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                animateScale(1.0f)
                if (isSwiping) {
                    val finalTranslationX = foregroundView.translationX
                    val targetTranslationX = if (finalTranslationX < -swipeThreshold) {
                        -deleteButtonWidth.toFloat()
                    } else {
                        0f
                    }
                    animateSwipe(targetTranslationX)
                    return true
                } else if (isOpened()) {
                    // Tap on already open card -> close it and consume the event
                    animateSwipe(0f)
                    return true
                }
            }
        }
        return false
    }

    fun animateSwipe(targetX: Float) {
        ObjectAnimator.ofFloat(foregroundView, "translationX", targetX).apply {
            duration = 200
            interpolator = DecelerateInterpolator()
            start()
        }
        currentTranslationX = targetX
    }

    fun reset() {
        foregroundView.translationX = 0f
        foregroundView.scaleX = 1.0f
        foregroundView.scaleY = 1.0f
        currentTranslationX = 0f
        isSwiping = false
    }
}
