package com.missa.tv360.data.remote

import com.google.gson.annotations.SerializedName
import com.missa.tv360.R
import com.missa.tv360.data.ContentKind
import com.missa.tv360.data.Video
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Contrat d'API du BFF (Backend-for-Frontend) — conception originale clean-room.
 *
 * Schéma :
 *   GET v1/catalog/rows  -> lignes de catalogue (categories + items)
 *   GET v1/catalog/{id}  -> fiche d'un contenu
 *   GET v1/stream/{id}   -> url de lecture (+ format, drapeau live)
 *
 * kinds acceptes :
 *   live_tv, sport, movie, series, short, radio, podcast, music, kids, game
 */
interface BffApi {

    @GET("v1/catalog/rows")
    suspend fun rows(): List<RowDto>

    @GET("v1/catalog/{id}")
    suspend fun item(@Path("id") id: String): ItemDto

    @GET("v1/stream/{id}")
    suspend fun stream(@Path("id") id: String): StreamDto
}

data class RowDto(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("items") val items: List<ItemDto>,
)

data class ItemDto(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerializedName("kind") val kind: String? = null,
    @SerializedName("series_id") val seriesId: String? = null,
    @SerializedName("series_title") val seriesTitle: String? = null,
    @SerializedName("season_number") val seasonNumber: Int? = null,
    @SerializedName("episode_number") val episodeNumber: Int? = null,
    @SerializedName("duration_sec") val durationSec: Int? = null,
    @SerializedName("audio_only") val audioOnly: Boolean? = null,
) {
    /**
     * Conversion DTO -> modele local. L'URL de lecture precise vient de
     * v1/stream/{id} (voir [StreamDto.toVideo]) ; ici on construit la fiche.
     * Les vignettes distantes ne sont pas chargees dans la v1 (aucune lib image).
     */
    fun toVideo(streamUrl: String = "", format: String = "mp4"): Video = Video(
        id = id,
        title = title,
        description = description.orEmpty(),
        cardRes = R.drawable.card_tbd,
        streamUrl = streamUrl,
        kind = ContentKind.fromApi(kind),
        seriesTitle = seriesTitle,
        season = seasonNumber,
        episode = episodeNumber,
        durationSec = durationSec,
        format = format,
        audioOnly = audioOnly == true,
        originalKind = kind,
    )
}

data class StreamDto(
    @SerializedName("id") val id: String,
    @SerializedName("url") val url: String,
    @SerializedName("format") val format: String? = null, // mp4 | hls | mp3
    @SerializedName("live") val live: Boolean? = null,
    @SerializedName("kind") val kind: String? = null,
) {
    /** Resolution minimale a partir de v1/stream/{id} (sans fiche catalogue). */
    fun toVideo(): Video {
        val kindEnum = ContentKind.fromApi(kind)
        return Video(
            id = id,
            title = id,
            description = "",
            cardRes = R.drawable.card_tbd,
            streamUrl = url,
            kind = kindEnum,
            format = format ?: if (kindEnum.isAudioOnly) "mp3" else "mp4",
            forceLive = live == true,
            originalKind = kind,
        )
    }
}
