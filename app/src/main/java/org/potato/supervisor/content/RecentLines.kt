package org.potato.supervisor.content

/** Least recently used within the actual scene/tone pool; unseen ties are random. */
class RecentLines(history: List<String> = emptyList()) {
    val history = history.distinct().toMutableList()
    fun select(ids: List<String>): String? {
        if(ids.isEmpty()) return null
        val oldest=ids.minOf { history.indexOf(it) }
        val chosen=ids.filter { history.indexOf(it)==oldest }.random()
        history.remove(chosen); history.add(chosen)
        return chosen
    }
}
