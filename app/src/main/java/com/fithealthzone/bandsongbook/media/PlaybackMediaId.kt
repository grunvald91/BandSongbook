package com.fithealthzone.bandsongbook.media

object PlaybackMediaId {
    private const val SETLIST_PREFIX = "setlist:"

    fun forSetlistItem(setlistItemId: String, audioId: String): String =
        "$SETLIST_PREFIX$setlistItemId:$audioId"

    fun audioId(mediaId: String): String =
        if (mediaId.startsWith(SETLIST_PREFIX)) mediaId.substringAfterLast(':') else mediaId
}
