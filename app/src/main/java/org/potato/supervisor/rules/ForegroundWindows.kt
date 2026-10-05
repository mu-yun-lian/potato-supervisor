package org.potato.supervisor.rules

// Android window constants: application=1, input method=2, system=3.
data class WindowMetadata(val type: Int, val pkg: String?, val focused: Boolean, val active: Boolean)
object ForegroundWindows {
    fun packageName(windows: List<WindowMetadata>): String? {
        if(windows.any { it.type==3 && (it.focused || it.active) }) return null
        val apps=windows.filter { it.type==1 }
        if(apps.isEmpty() || apps.any { it.pkg==null }) return null
        val packages=apps.map { it.pkg!! }.distinct()
        if(packages.size!=1 || packages.single() in setOf("android","com.android.permissioncontroller","com.google.android.permissioncontroller")) return null
        val focused=apps.filter { it.focused }.ifEmpty { apps.filter { it.active } }
        // A keyboard may own focus, but a sole positively identified app remains foreground.
        if(focused.size!=1 && !(focused.isEmpty() && windows.any { it.type==2 && (it.focused || it.active) })) return null
        return packages.single()
    }
}
