package com.missa.tv360.extensions

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.missa.tv360.DeviceForm
import com.missa.tv360.R
import com.missa.tv360.ui.SystemBarInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Écran Extensions : gestion des dépôts et des extensions installées.
 *
 * Accessible depuis la carte « Extensions » de l'accueil. Tout le travail
 * réseau est fait sur le dispatcher IO ; l'écran ne fait qu'observer
 * [ExtensionManager.state].
 */
class ExtensionsActivity : AppCompatActivity() {

    private lateinit var adapter: ExtensionsAdapter
    private lateinit var statusView: TextView
    private lateinit var addButton: Button
    private lateinit var refreshButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(if (DeviceForm.isTelevision(this)) R.style.Theme_MissaTv_Extensions else R.style.Theme_MissaTv_Mobile)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_extensions)
        SystemBarInsets.apply(findViewById(R.id.extensions_root))
        findViewById<Button>(R.id.extensions_back).setOnClickListener { finish() }
        ExtensionManager.init(this)

        statusView = findViewById(R.id.extensions_status)
        addButton = findViewById(R.id.extensions_add)
        refreshButton = findViewById(R.id.extensions_refresh)

        adapter = ExtensionsAdapter(
            onPluginClick = { remote -> install(remote) },
            onPluginLongClick = { installed -> confirmUninstall(installed) },
            onRepoLongClick = { repo -> confirmRemoveRepository(repo) },
        )

        findViewById<RecyclerView>(R.id.extensions_list).apply {
            layoutManager = LinearLayoutManager(this@ExtensionsActivity)
            adapter = this@ExtensionsActivity.adapter
            setHasFixedSize(false)
        }

        addButton.setOnClickListener { showAddDialog() }
        refreshButton.setOnClickListener { refresh() }

        lifecycleScope.launch {
            ExtensionManager.state.collect { state ->
                adapter.submit(state)
                render(state)
            }
        }

        // Chargement local immédiat (aucun réseau) puis rafraîchissement.
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { ExtensionManager.loadInstalled() }
            adapter.submit(ExtensionManager.state.value)
            refresh()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) adapter.submit(ExtensionManager.state.value)
    }

    // ── Rendu ─────────────────────────────────────────────────────────────

    private fun render(state: ExtensionsState) {
        val text = when {
            state.busy -> getString(R.string.extensions_busy)
            !state.message.isNullOrBlank() -> state.message
            state.lastRefreshAt == 0L -> getString(R.string.extensions_never_refreshed)
            else -> getString(
                R.string.extensions_summary,
                state.repositories.size,
                state.installed.size,
            )
        }
        statusView.text = text
        addButton.isEnabled = !state.busy
        refreshButton.isEnabled = !state.busy
    }

    // ── Actions ───────────────────────────────────────────────────────────

    private fun refresh() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { ExtensionManager.refresh(installMissing = false) }
            adapter.submit(ExtensionManager.state.value)
        }
    }

    private fun install(remote: RemotePlugin) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { ExtensionManager.install(remote) }
            adapter.submit(ExtensionManager.state.value)
            result.onFailure { toast(it.message ?: getString(R.string.extensions_error)) }
            // Une extension installée publie de nouveaux contenus : on relance
            // la lecture du catalogue au retour sur l'accueil.
        }
    }

    private fun showAddDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.extensions_repo_hint)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine()
            setText(clipboardCandidate())
            setSelection(text.length)
        }
        val container = FrameLayout(this).apply {
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, 0)
            addView(
                input,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.extensions_add_repo)
            .setView(container)
            .setPositiveButton(R.string.extensions_add) { _, _ ->
                val url = input.text?.toString().orEmpty().trim()
                if (url.isBlank()) toast(getString(R.string.extensions_url_required))
                else addRepository(url)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun addRepository(url: String) {
        lifecycleScope.launch {
            statusView.text = getString(R.string.extensions_busy)
            val result = withContext(Dispatchers.IO) { ExtensionManager.addRepository(url) }
            result.onFailure { toast(it.message ?: getString(R.string.extensions_error)) }
            refresh()
        }
    }

    private fun confirmRemoveRepository(repo: RepositoryState) {
        AlertDialog.Builder(this)
            .setTitle(R.string.extensions_remove_repo)
            .setMessage(repo.manifest?.name ?: repo.entry.name)
            .setPositiveButton(R.string.extensions_remove) { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { ExtensionManager.removeRepository(repo.entry.url) }
                    adapter.submit(ExtensionManager.state.value)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmUninstall(extension: InstalledExtension) {
        AlertDialog.Builder(this)
            .setTitle(R.string.extensions_remove_plugin)
            .setMessage(extension.name)
            .setPositiveButton(R.string.extensions_remove) { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { ExtensionManager.uninstall(extension.internalName) }
                    adapter.submit(ExtensionManager.state.value)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Pré-remplit le champ avec un lien de dépôt présent dans le presse-papier. */
    private fun clipboardCandidate(): String {
        val manager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return ""
        val text = manager.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()?.trim().orEmpty()
        if (text.length in 4..400 && !text.contains('\n')) {
            return text
        }
        return ""
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
