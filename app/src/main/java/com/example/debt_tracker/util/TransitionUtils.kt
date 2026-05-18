package com.example.debt_tracker.util

import android.app.Activity
import android.os.Build
import com.example.debt_tracker.R

fun Activity.overrideSlideTransition(isEntering: Boolean) {
    val enterAnim = if (isEntering) R.anim.slide_in_right else R.anim.slide_in_left
    val exitAnim = if (isEntering) R.anim.slide_out_left else R.anim.slide_out_right

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        overrideActivityTransition(
            if (isEntering) Activity.OVERRIDE_TRANSITION_OPEN else Activity.OVERRIDE_TRANSITION_CLOSE,
            enterAnim,
            exitAnim
        )
    } else {
        @Suppress("DEPRECATION")
        overridePendingTransition(enterAnim, exitAnim)
    }
}
