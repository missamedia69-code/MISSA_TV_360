package com.missa.tv360.data.remote

import com.google.gson.annotations.SerializedName
import com.missa.tv360.data.Game

/**
 * DTO du fichier de jeux publie par l'engine « jeux » du depot GitHub.
 * Fichier servi par GitHub Pages : data/games.json
 */
data class GamesCatalogDto(
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("rows") val rows: List<GameRowDto> = emptyList(),
)

data class GameRowDto(
    @SerializedName("title") val title: String,
    @SerializedName("games") val games: List<GameDto> = emptyList(),
)

data class GameDto(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerializedName("download_url") val downloadUrl: String,
    @SerializedName("size_bytes") val sizeBytes: Long? = null,
    @SerializedName("sha256") val sha256: String? = null,
    @SerializedName("package_type") val packageType: String? = null,
    @SerializedName("version_name") val versionName: String? = null,
    @SerializedName("version_code") val versionCode: Int? = null,
    @SerializedName("genre") val genre: String? = null,
) {
    fun toGame(): Game = Game(
        id = id,
        title = title,
        description = description.orEmpty(),
        thumbnailUrl = thumbnailUrl,
        downloadUrl = downloadUrl,
        sizeBytes = sizeBytes ?: 0L,
        sha256 = sha256,
        packageType = packageType ?: "apk",
        versionName = versionName,
        versionCode = versionCode,
        genre = genre,
    )
}
