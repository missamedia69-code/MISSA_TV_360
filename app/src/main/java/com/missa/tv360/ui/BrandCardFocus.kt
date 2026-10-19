package com.missa.tv360.ui

import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.leanback.widget.ImageCardView
import com.missa.tv360.R

/** Bright MISSA-yellow selected state with dark, readable card labels. */
object BrandCardFocus {

    fun apply(card: ImageCardView, focused: Boolean) {
        val titleColor = ContextCompat.getColor(
            card.context,
            if (focused) R.color.text_on_yellow else R.color.text_primary,
        )
        val contentColor = ContextCompat.getColor(
            card.context,
            if (focused) R.color.text_on_yellow else R.color.text_secondary,
        )
        card.setInfoAreaBackgroundColor(
            ContextCompat.getColor(
                card.context,
                if (focused) R.color.missa_yellow else R.color.missa_card_bg,
            ),
        )
        card.findViewById<TextView>(androidx.leanback.R.id.title_text)?.setTextColor(titleColor)
        card.findViewById<TextView>(androidx.leanback.R.id.content_text)?.setTextColor(contentColor)
    }
}
