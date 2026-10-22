package com.missa.tv360.ui.mobile

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView

/** Image dont la hauteur suit la largeur disponible, sans étirer le contenu. */
class RatioImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppCompatImageView(context, attrs, defStyleAttr) {
    var aspectRatio: Float = 16f / 9f
        set(value) {
            field = value.coerceAtLeast(0.1f)
            requestLayout()
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        if (width > 0) {
            val height = (width / aspectRatio).toInt().coerceAtLeast(1)
            super.onMeasure(
                widthMeasureSpec,
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
            )
        } else super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
