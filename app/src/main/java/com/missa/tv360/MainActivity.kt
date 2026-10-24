package com.missa.tv360

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.missa.tv360.data.CatalogStore
import com.missa.tv360.data.CatalogViewModel
import com.missa.tv360.extensions.ExtensionManager
import com.missa.tv360.ui.BrowseFragment
import com.missa.tv360.ui.ThumbnailLoader
import com.missa.tv360.ui.SystemBarInsets
import com.missa.tv360.ui.mobile.MobileHomeFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Hôte unique : Leanback sur TV, composants tactiles sur téléphone/tablette. */
class MainActivity : AppCompatActivity() {
    private lateinit var catalogModel: CatalogViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        val television = DeviceForm.isTelevision(this)
        setTheme(if (television) R.style.Theme_MissaTv else R.style.Theme_MissaTv_Mobile)
        super.onCreate(savedInstanceState)
        setContentView(if (television) R.layout.activity_main else R.layout.activity_mobile_main)
        if (!television) SystemBarInsets.apply(findViewById(R.id.mobile_root))

        lifecycleScope.launch(Dispatchers.IO) { ThumbnailLoader.initDiskCache(applicationContext) }
        catalogModel = ViewModelProvider(this)[CatalogViewModel::class.java]
        catalogModel.initialize()

        // Initialiser le moteur d'extensions et charger les extensions déjà installées
        // au démarrage, afin que leurs providers soient enregistrés avant l'affichage du catalogue.
        lifecycleScope.launch(Dispatchers.IO) {
            ExtensionManager.init(this@MainActivity)
            ExtensionManager.bootstrapIfNeeded()
        }

        findViewById<Button>(R.id.catalog_retry).setOnClickListener { catalogModel.refresh() }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                catalogModel.state.collect { renderCatalogState(it) }
            }
        }

        if (savedInstanceState == null) {
            val fragment = if (television) BrowseFragment() else MobileHomeFragment()
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_browse_container, fragment)
                .commitNow()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::catalogModel.isInitialized) CatalogStore.refreshUserRows(this)
    }

    private fun renderCatalogState(state: CatalogViewModel.UiState) {
        findViewById<View>(R.id.catalog_loading).isVisible = state.initialLoading
        val message = when {
            state.initialLoading -> null
            state.refreshing -> getString(R.string.catalog_refreshing)
            state.networkFailed -> getString(R.string.catalog_offline)
            state.remoteFailed -> getString(R.string.catalog_stale)
            else -> null
        }
        findViewById<View>(R.id.catalog_status).isVisible = message != null
        findViewById<TextView>(R.id.catalog_status_text).text = message.orEmpty()
        findViewById<Button>(R.id.catalog_retry).isVisible =
            state.remoteFailed && !state.refreshing
    }
}
