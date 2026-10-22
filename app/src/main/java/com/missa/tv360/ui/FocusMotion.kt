package com.missa.tv360.ui

import android.provider.Settings
import android.view.View

/** Animation de focus commune, immédiate lorsque Android désactive les animations. */
object FocusMotion {
    fun apply(view: View, focused: Boolean, focusedScale: Float = 1.04f) {
        val scale = if (focused) focusedScale else 1f
        view.animate().cancel()
        val animatorScale = runCatching {
            Settings.Global.getFloat(
                view.context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrDefault(1f)
        if (animatorScale == 0f) {
            view.scaleX = scale
            view.scaleY = scale
        } else {
            view.animate()
                .scaleX(scale)
                .scaleY(scale)
                .setDuration(140L)
                .start()
        }
    }
}
