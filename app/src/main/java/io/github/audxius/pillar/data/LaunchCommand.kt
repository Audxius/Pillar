package io.github.audxius.pillar.data

/** Values substituted into an ES-DE launch command for one game. */
data class LaunchVars(
    val romPath: String,
    val romRoot: String,
    val docUri: String,
    val baseName: String,
)

enum class ExtraKind { STRING, ARRAY, INTEGER, BOOL }

data class LaunchExtra(val kind: ExtraKind, val name: String, val value: String)

/** An ES-DE command reduced to plain Intent parts, without any Android types so it can be unit tested. */
data class LaunchSpec(
    val action: String?,
    val category: String?,
    val mimeType: String?,
    val data: String?,
    val extras: List<LaunchExtra>,
    val clearTask: Boolean,
    val clearTop: Boolean,
    val noHistory: Boolean,
) {
    /** Every content:// URI the target app needs read access to. */
    val contentUris: List<String>
        get() = (listOfNotNull(data) + extras.flatMap { it.value.split(',') })
            .filter { it.startsWith("content://") }.distinct()
}

object LaunchCommand {
    private val extraRe = Regex("%(EXTRA|EXTRAARRAY|EXTRAINTEGER|EXTRABOOL)_([^%]+)%=(\"(?:[^\"\\\\]|\\\\.)*\"|\\S+)")
    private val pathVars = Regex("%ROM%|%ROMRAW%|%GAMEDIRRAW%|%ROMPATH%|%ROMPATHRAW%")

    fun needsRealPath(cmd: String): Boolean = pathVars.containsMatchIn(cmd)

    /**
     * Parses a command whose %EMULATOR_x%, %ANDROIDPACKAGE%, %INTERNALDATA%, %EXTERNALDATA% and
     * %INJECT% tokens were already resolved. Mirrors ES-DE's FileData::launchGame Android branch.
     */
    fun parse(cmd: String, vars: LaunchVars): LaunchSpec {
        fun expand(v: String): String {
            var s = v
                .replace("%ROMRAW%", vars.romPath)
                .replace("%ROMPATHRAW%", vars.romRoot)
                .replace("%ROMPATH%", vars.romRoot)
                .replace("%GAMEDIRRAW%", vars.romPath.substringBeforeLast('/', ""))
                .replace("%BASENAME%", vars.baseName)
                .replace("%ROM%", vars.romPath)
            // ES-DE collapses doubled slashes in path values; done before URIs go in so content:// survives.
            s = s.replace("//", "/").replace("\\/", "/")
            return s.replace("%ROMSAF%", vars.docUri).replace("%ROMPROVIDER%", vars.docUri)
        }

        fun valueOf(name: String): String? {
            val token = "%$name%="
            val pos = cmd.indexOf(token)
            if (pos < 0) return null
            val start = pos + token.length
            return if (cmd.getOrNull(start) == '"') {
                val end = cmd.indexOf('"', start + 1)
                if (end < 0) null else cmd.substring(start + 1, end)
            } else {
                val end = cmd.indexOf(' ', start).let { if (it < 0) cmd.length else it }
                cmd.substring(start, end)
            }
        }

        val extras = extraRe.findAll(cmd).mapNotNull { m ->
            val raw = m.groupValues[3].let { if (it.startsWith('"')) it.substring(1, it.length - 1) else it }
            val value = expand(raw).replace("\\\"", "\"")
            if (value.isEmpty()) return@mapNotNull null
            val kind = when (m.groupValues[1]) {
                "EXTRAARRAY" -> ExtraKind.ARRAY
                "EXTRAINTEGER" -> ExtraKind.INTEGER
                "EXTRABOOL" -> ExtraKind.BOOL
                else -> ExtraKind.STRING
            }
            LaunchExtra(kind, m.groupValues[2], value)
        }.toList()

        return LaunchSpec(
            action = valueOf("ACTION"),
            category = valueOf("CATEGORY"),
            mimeType = valueOf("MIMETYPE"),
            data = valueOf("DATA")?.let(::expand),
            extras = extras,
            clearTask = "%ACTIVITY_CLEAR_TASK%" in cmd,
            clearTop = "%ACTIVITY_CLEAR_TOP%" in cmd,
            noHistory = "%ACTIVITY_NO_HISTORY%" in cmd,
        )
    }
}
