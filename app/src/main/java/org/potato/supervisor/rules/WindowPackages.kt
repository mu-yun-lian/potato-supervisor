package org.potato.supervisor.rules

// Package identities come only from accessibility event metadata.
class WindowPackages {
    private val packages=mutableMapOf<Int,String>()
    private val pending=linkedMapOf<Int,Long>()
    fun hasIdentity(id: Int) = packages.containsKey(id)
    fun record(id: Int, pkg: String, now: Long, renew: Boolean = true): Boolean {
        if(id<0 || pkg.isBlank()) return false
        val changed=packages[id]!=pkg
        packages[id]=pkg
        if(changed || renew) {
            pending[id]=now
            if(pending.size>32) {
                val oldest=pending.keys.first(); pending.remove(oldest); packages.remove(oldest)
            }
        }
        return changed
    }
    fun visible(ids: Set<Int>, now: Long): Map<Int,String> {
        // The event can precede getWindows() seeing the new window after rotation.
        // Keep its identity briefly, but never report an invisible or expired window.
        pending.filterValues { now-it>1500 }.keys.toList().forEach { pending.remove(it); packages.remove(it) }
        packages.keys.retainAll(ids+pending.keys)
        pending.keys.removeAll(ids)
        return packages.filterKeys { it in ids }
    }
    fun clear() { packages.clear(); pending.clear() }
}
