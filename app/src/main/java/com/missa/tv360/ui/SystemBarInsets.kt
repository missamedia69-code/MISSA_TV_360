package com.missa.tv360.ui

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** Conserve les marges des écrans tactiles lorsque les barres système sont superposées. */
object SystemBarInsets {
    fun apply(root: View) {
        val left = root.paddingLeft
        val top = root.paddingTop
        val right = root.paddingRight
        val bottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                left + bars.left,
                top + bars.top,
                right + bars.right,
                bottom + bars.bottom,
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }
}
