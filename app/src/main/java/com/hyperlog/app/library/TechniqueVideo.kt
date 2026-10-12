package com.hyperlog.app.library

import java.net.URI

/** A YouTube video, and where in it the technique part starts. */
data class TechniqueVideo(val id: String, val startSeconds: Int?) {
    /** A link the YouTube app opens at [startSeconds]. */
    val watchUrl: String get() = "https://www.youtube.com/watch?v=$id" + (startSeconds?.let { "&t=${it}s" } ?: "")

    /** `10:53`, or `1:02:03` past an hour. */
    val startLabel: String? get() = startSeconds?.let { s ->
        if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }

    companion object {
        private val Id = Regex("[A-Za-z0-9_-]{6,}")

        /** Reads youtu.be, youtube.com/watch and /shorts links; null for anything else. */
        fun parse(link: String): TechniqueVideo? {
            val text = link.trim().let { if (it.contains("://")) it else "https://$it" }
            val uri = runCatching { URI(text) }.getOrNull() ?: return null
            val host = uri.host?.lowercase()?.removePrefix("www.")?.removePrefix("m.") ?: return null
            val query = uri.rawQuery.orEmpty().split('&').mapNotNull {
                it.split('=', limit = 2).takeIf { kv -> kv.size == 2 }?.let { kv -> kv[0] to kv[1] }
            }.toMap()
            val path = uri.path.orEmpty()
            val id = when (host) {
                "youtu.be" -> path.trim('/')
                "youtube.com" -> when {
                    path == "/watch" -> query["v"]
                    path.startsWith("/shorts/") -> path.removePrefix("/shorts/").trim('/')
                    else -> null
                }
                else -> null
            } ?: return null
            if (!Id.matches(id)) return null
            return TechniqueVideo(id, (query["t"] ?: query["start"])?.let(::seconds))
        }

        /** `653`, `653s`, `1m30s`, `1h2m3s`. */
        private fun seconds(t: String): Int? {
            t.toIntOrNull()?.let { return it }
            val m = Regex("(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?").matchEntire(t) ?: return null
            val (h, min, s) = m.destructured
            if (h.isEmpty() && min.isEmpty() && s.isEmpty()) return null
            return (h.toIntOrNull() ?: 0) * 3600 + (min.toIntOrNull() ?: 0) * 60 + (s.toIntOrNull() ?: 0)
        }
    }
}
