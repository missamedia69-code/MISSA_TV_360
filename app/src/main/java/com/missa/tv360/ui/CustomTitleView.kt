package com.missa.tv360.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.leanback.widget.SearchOrbView
import androidx.leanback.widget.TitleViewAdapter
import com.missa.tv360.R

/**
 * En-tête personnalisé pour Leanback BrowseFragment.
 * Affiche le logo de marque TV 360 juste devant le texte "MISSA TV 360" harmonisé,
 * ainsi que le bouton de recherche (SearchOrbView).
 */
class CustomTitleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr), TitleViewAdapter.Provider {

    private val logoView: ImageView
    private val textView: TextView
    private val searchOrbView: SearchOrbView
    private val brandingContainer: View

    init {
        LayoutInflater.from(context).inflate(R.layout.custom_title_view, this, true)
        logoView = findViewById(R.id.title_logo)
        textView = findViewById(R.id.title_text)
        searchOrbView = findViewById(R.id.title_orb)
        brandingContainer = findViewById(R.id.branding_container)
        clipChildren = false
        clipToPadding = false
    }

    private val titleViewAdapter = object : TitleViewAdapter() {
        override fun getSearchAffordanceView(): View = searchOrbView

        override fun setOnSearchClickedListener(listener: OnClickListener?) {
            searchOrbView.setOnOrbClickedListener(listener)
        }

        override fun setTitle(titleText: CharSequence?) {
            if (!titleText.isNullOrBlank()) {
                textView.text = titleText
            }
        }

        override fun getTitle(): CharSequence = textView.text

        override fun setBadgeDrawable(drawable: Drawable?) {
            if (drawable != null) {
                logoView.setImageDrawable(drawable)
            } else {
                logoView.setImageResource(R.drawable.ic_app_logo)
            }
            logoView.visibility = VISIBLE
            textView.visibility = VISIBLE
        }

        override fun getBadgeDrawable(): Drawable? = logoView.drawable

        override fun setSearchAffordanceColors(colors: SearchOrbView.Colors) {
            searchOrbView.setOrbColors(colors)
        }

        override fun getSearchAffordanceColors(): SearchOrbView.Colors =
            checkNotNull(searchOrbView.orbColors)

        override fun updateComponentsVisibility(flags: Int) {
            val brandingVisible = (flags and BRANDING_VIEW_VISIBLE) == BRANDING_VIEW_VISIBLE
            brandingContainer.visibility = if (brandingVisible) VISIBLE else GONE

            val searchVisible = (flags and SEARCH_VIEW_VISIBLE) == SEARCH_VIEW_VISIBLE
            searchOrbView.visibility = if (searchVisible) VISIBLE else INVISIBLE
        }
    }

    override fun getTitleViewAdapter(): TitleViewAdapter = titleViewAdapter
}
