package com.onikiri.open.data

/**
 * Case-insensitive search. Every whitespace-separated word of the query must appear in the
 * title, description, author/owner or id of the module.
 */
fun filterModules(
    modules: List<RootModule>,
    query: String,
    authorOf: (RootModule) -> String? = { null }
): List<RootModule> {
    val tokens = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return modules
    return modules.filter { module ->
        val haystack = buildString {
            append(module.title).append(' ')
            append(module.description).append(' ')
            append(module.owner).append(' ')
            append(module.id)
            authorOf(module)?.let { append(' ').append(it) }
        }.lowercase()
        tokens.all { haystack.contains(it) }
    }
}
