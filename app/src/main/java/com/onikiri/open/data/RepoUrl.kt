package com.onikiri.open.data

import java.net.URI

object RepoUrl {
    const val BUILT_IN =
        "https://raw.githubusercontent.com/dtech-special/Onikiri/refs/heads/main/modules.jsonl"

    private val shortForm = Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/\\S+$")
    private val blobForm = Regex("^https://github\\.com/([^/]+)/([^/]+)/blob/(.+)$")

    /**
     * Accepts a full https URL, a github.com blob URL, or the legacy short form
     * `USER/REPO/refs/heads/main/NAME.jsonl`. Returns null for anything unusable
     * (empty, contains spaces, plain http).
     */
    fun normalize(input: String): String? {
        var value = input.trim()
        if (value.isEmpty() || value.any { it.isWhitespace() }) return null
        if (value.startsWith("http://", ignoreCase = true)) return null
        if (value.startsWith("https://", ignoreCase = true)) {
            value = "https://" + value.substring("https://".length)
            val blob = blobForm.matchEntire(value)
            if (blob != null) {
                value = "https://raw.githubusercontent.com/" +
                    "${blob.groupValues[1]}/${blob.groupValues[2]}/${blob.groupValues[3]}"
            }
            val host = try {
                URI(value).host
            } catch (_: Exception) {
                null
            }
            return if (host.isNullOrBlank()) null else value
        }
        return if (shortForm.matches(value)) "https://raw.githubusercontent.com/$value" else null
    }
}
