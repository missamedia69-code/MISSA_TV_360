package com.missa.tv360

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.missa.tv360.ui.BrowseFragment
import com.missa.tv360.ui.ThumbnailLoader

/**
 * Activité principale — héberge le parcours Leanback (lignes de contenus).
 */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Cache disque des vignettes : évite de re-télécharger les affiches à
        // chaque défilement.
        ThumbnailLoader.initDiskCache(this)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_browse_container, BrowseFragment())
                .commitNow()
        }
    }
}
