package com.missa.tv360.extensions

import com.missa.tv360.api.Resolution
import com.missa.tv360.api.ResolvingProvider
import com.missa.tv360.data.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object StreamResolver {
    suspend fun resolve(video: Video): Resolution {
        val page = video.pageUrl ?: return Resolution(error = "Pas de page")
        val id = video.providerId ?: return Resolution(error = "Pas de fournisseur")
        val provider = ExtensionManager.providers().firstOrNull { it.id == id }
            as? ResolvingProvider ?: return Resolution(error = "Fournisseur non résolvable")
        return withContext(Dispatchers.IO) { provider.resolve(page) }
    }
}
