package io.github.audxius.pillar.data

/**
 * Save storage that consoles share between all their games (memory cards, VMUs, system save
 * folders) rather than one file per game. RetroArch keeps most of it in its system folder, which
 * also holds BIOS files, so only these patterns are ever read or synced from there.
 */
object SharedSaves {
    /** The console a shared save belongs to, or null for a per-game save (or anything else). */
    fun systemOf(key: String): String? {
        val k = key.lowercase()
        val name = k.substringAfterLast('/')
        val top = k.substringBefore('/')
        return when {
            name.startsWith("vmu_save") || name.endsWith(".vmu") -> "dreamcast"
            name.endsWith(".ps2") || (name.startsWith("mcd00") && name.endsWith(".ps2")) || "/memcards/" in k -> "ps2"
            "/wii/title/" in k || "/wii/shared2/" in k -> "wii"
            name.endsWith(".gci") || "/gc/" in k && (name.endsWith(".raw") || name.endsWith(".gci")) -> "gc"
            "/psp/savedata/" in k -> "psp"
            name.endsWith("_shared.bcr") || name.endsWith("_shared.smpc") -> "saturn"
            // Everything else in the system folder (BIOS, firmware) is never touched.
            top == "system" -> null
            else -> null
        }
    }

    /** Whether a file in RetroArch's system folder is save data Pillar may show and sync. */
    fun isSystemSave(key: String): Boolean = key.startsWith("system/", ignoreCase = true) && systemOf(key) != null

    /**
     * The name a per-game save is made from: "Game (USA).srm" -> "Game (USA)", and also
     * "Game (USA).A1.bin" (Flycast per-game VMUs) or "Game (USA).state3.png" -> "Game (USA)".
     * Returns every candidate, longest first.
     */
    fun stems(fileName: String): List<String> {
        var s = fileName.replace(Regex("\\.png$", RegexOption.IGNORE_CASE), "")
        val out = ArrayList<String>()
        s = s.substringBeforeLast('.', s)
        out += s
        // Peel short extra suffixes like ".A1", ".card1", ".state3", but not real title text.
        while (true) {
            val ext = s.substringAfterLast('.', "")
            if (ext.isEmpty() || ext.length > 6 || !ext.all { it.isLetterOrDigit() } || ext.all { it.isDigit() }) break
            s = s.substringBeforeLast('.')
            out += s
        }
        return out
    }
}
