package com.techport.tvremote.data.apps

import com.techport.tvremote.domain.model.AppShortcut

/** A public web address that a TV app may claim as an app link. */
data class AppLinkSuggestion(val label: String, val link: String)

/**
 * Well-known public URLs offered as one-tap shortcuts. They are plain web addresses, not
 * package names: the TV opens them only if an installed app handles the link (otherwise it
 * may open a browser or do nothing). Nothing here is fetched from the network.
 */
object AppLinkSuggestions {
    val ALL: List<AppLinkSuggestion> = listOf(
        AppLinkSuggestion("YouTube", "https://www.youtube.com"),
        AppLinkSuggestion("Netflix", "https://www.netflix.com"),
        AppLinkSuggestion("Prime Video", "https://www.primevideo.com"),
        AppLinkSuggestion("Spotify", "https://open.spotify.com"),
        AppLinkSuggestion("Disney+", "https://www.disneyplus.com"),
        AppLinkSuggestion("Plex", "https://app.plex.tv"),
    )

    /** Suggestions whose link is not saved yet (case-insensitive, ignoring a trailing slash). */
    fun notSaved(saved: List<AppShortcut>): List<AppLinkSuggestion> {
        val links = saved.mapTo(HashSet()) { normalize(it.link) }
        return ALL.filterNot { normalize(it.link) in links }
    }

    private fun normalize(link: String) = link.trim().trimEnd('/').lowercase()
}
