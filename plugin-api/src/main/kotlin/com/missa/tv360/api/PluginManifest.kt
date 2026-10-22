package com.missa.tv360.api

/**
 * Métadonnées lues par l'application dans le `manifest.json` de l'archive.
 *
 * Exemple de contenu (racine du `.msa`) :
 * ```json
 * {
 *   "name": "BipBop Demo",
 *   "version": 3,
 *   "apiVersion": 1,
 *   "pluginClassName": "com.missa.extensions.bipbop.BipBopPlugin",
 *   "requiresResources": false
 * }
 * ```
 */
data class PluginManifest(
    val name: String? = null,
    val version: Int? = null,
    val apiVersion: Int = 0,
    val pluginClassName: String? = null,
    val requiresResources: Boolean = false,
)
