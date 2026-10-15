package io.github.audxius.pillar.data

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import java.io.File

sealed interface LaunchResult {
    data object Started : LaunchResult
    data class Failed(val message: String) : LaunchResult
}

/** An emulator package that can run a command, with its resolved activity. */
data class ResolvedEmulator(val packageName: String, val activity: String?, val label: String)

/**
 * Interprets ES-DE Android launch commands (es_systems.xml syntax) and turns them into Intents.
 * Supported: %EMULATOR_x%, %ACTION%, %CATEGORY%, %MIMETYPE%, %DATA%, %EXTRA_/EXTRAARRAY_/
 * EXTRAINTEGER_/EXTRABOOL_x%, %ACTIVITY_CLEAR_TASK/CLEAR_TOP/NO_HISTORY%, %ANDROIDAPP%,
 * %INJECT%, %FILEINJECT%, and the path variables %ROM%, %ROMSAF%, %ROMPROVIDER%, %ROMRAW%,
 * %BASENAME%, %GAMEDIRRAW%, %ROMPATH%, %ROMPATHRAW%, %ANDROIDPACKAGE%, %INTERNALDATA%,
 * %EXTERNALDATA%.
 */
class GameLauncher(private val context: Context, private val catalog: SystemCatalog) {

    private val pm: PackageManager get() = context.packageManager

    fun isInstalled(pkg: String): Boolean = runCatching {
        pm.getPackageInfo(pkg, 0); true
    }.getOrDefault(false)

    fun appLabel(pkg: String): String = runCatching {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    /** The first installed package that satisfies an ES-DE emulator id. */
    fun resolveEmulator(emulatorId: String): ResolvedEmulator? {
        val entries = catalog.findRules[emulatorId] ?: return null
        for (entry in entries) {
            val pkg = entry.substringBefore('/')
            if (!isInstalled(pkg)) continue
            val act = entry.substringAfter('/', "").takeIf { it.isNotEmpty() }
                ?.let { if (it.startsWith('.')) pkg + it else it }
            return ResolvedEmulator(pkg, act, appLabel(pkg))
        }
        return null
    }

    /** Candidate packages for an emulator id, used to show "not installed" hints. */
    fun candidatePackages(emulatorId: String): List<String> =
        catalog.findRules[emulatorId].orEmpty().map { it.substringBefore('/') }.distinct()

    fun commandsFor(system: String, settings: AppSettings): List<EmuCommand> {
        val builtin = catalog[system]?.commands.orEmpty()
        val custom = settings.customCommands[system].orEmpty().map { EmuCommand(it.label, it.command, custom = true) }
        return custom + builtin
    }

    fun isCommandAvailable(cmd: EmuCommand): Boolean {
        if (cmd.raw.contains("%ANDROIDAPP%")) return true
        val id = cmd.emulatorId ?: return false
        return resolveEmulator(id) != null
    }

    /**
     * The command used when the user hasn't picked one: the first installed emulator. App-launch
     * commands (%ANDROIDAPP%) only apply to .app files, so they're skipped for normal ROMs.
     */
    fun automaticCommand(system: String, settings: AppSettings, fileName: String? = null): EmuCommand? {
        val all = commandsFor(system, settings)
        val isAppFile = fileName?.substringAfterLast('.', "")?.equals("app", ignoreCase = true) == true
        val appCommands = all.filter { it.raw.contains("%ANDROIDAPP%") }
        if (isAppFile || (appCommands.isNotEmpty() && appCommands.size == all.size)) return appCommands.firstOrNull()
        val emulators = all - appCommands.toSet()
        return emulators.firstOrNull { isCommandAvailable(it) } ?: emulators.firstOrNull()
    }

    /** Per-game override, then the system default, then [automaticCommand]. */
    fun selectCommand(game: Game, settings: AppSettings, user: UserData): EmuCommand? {
        val all = commandsFor(game.system, settings)
        if (all.isEmpty()) return null
        user.emulatorOverride[game.id]?.let { label -> all.firstOrNull { it.label == label }?.let { return it } }
        settings.defaultEmulators[game.system]?.let { label -> all.firstOrNull { it.label == label }?.let { return it } }
        return automaticCommand(game.system, settings, game.fileName)
    }

    fun launch(game: Game, command: EmuCommand): LaunchResult = try {
        when (val built = buildIntent(game, command)) {
            is Built.Ok -> start(built.intent, built.uris)
            is Built.Error -> LaunchResult.Failed(built.message)
        }
    } catch (e: Exception) {
        LaunchResult.Failed(e.message ?: e.javaClass.simpleName)
    }

    fun launchApp(pkg: String): LaunchResult {
        val intent = pm.getLaunchIntentForPackage(pkg) ?: pm.getLeanbackLaunchIntentForPackage(pkg)
            ?: return LaunchResult.Failed("${appLabel(pkg)} can't be opened")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent); LaunchResult.Started
        } catch (e: Exception) {
            LaunchResult.Failed(e.message ?: "Couldn't open app")
        }
    }

    private sealed interface Built {
        data class Ok(val intent: Intent, val uris: List<Uri>) : Built
        data class Error(val message: String) : Built
    }

    private fun start(intent: Intent, uris: List<Uri>): LaunchResult {
        // Launched from the application context, and emulators should get their own task anyway.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pkg = intent.component?.packageName ?: intent.`package`
        if (pkg != null) {
            for (u in uris) {
                runCatching {
                    context.grantUriPermission(pkg, u, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
            }
        }
        return try {
            context.startActivity(intent)
            LaunchResult.Started
        } catch (e: ActivityNotFoundException) {
            // The activity name may have changed in a newer emulator build; retry by package only.
            if (intent.component != null && pkg != null) {
                val retry = Intent(intent).apply { component = null; setPackage(pkg) }
                try {
                    context.startActivity(retry); LaunchResult.Started
                } catch (_: Exception) {
                    LaunchResult.Failed("${appLabel(pkg)} didn't accept the game. Try another emulator for this system.")
                }
            } else {
                LaunchResult.Failed("No app can open this game")
            }
        } catch (e: SecurityException) {
            LaunchResult.Failed("Android blocked the launch: ${e.message}")
        }
    }

    private fun buildIntent(game: Game, command: EmuCommand): Built {
        var cmd = command.raw

        // Android apps and ports: the game file holds a package name.
        Regex("%ANDROIDAPP%=(\\S+)").find(cmd)?.let { m ->
            val value = m.groupValues[1]
            val pkg = if (value == "%FILEINJECT%") readText(Uri.parse(game.docUri))?.trim() else value
            if (pkg.isNullOrBlank()) return Built.Error("Couldn't read the package name from ${game.fileName}")
            val intent = pm.getLaunchIntentForPackage(pkg) ?: return Built.Error("$pkg isn't installed")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return Built.Ok(intent, emptyList())
        }

        val emulatorId = command.emulatorId ?: return Built.Error("This command doesn't name an emulator")
        val emu = resolveEmulator(emulatorId) ?: run {
            val pkgs = candidatePackages(emulatorId)
            val name = command.label.removeSuffix(" (Standalone)")
            return Built.Error(
                when {
                    pkgs.isEmpty() -> "Unknown emulator $emulatorId"
                    emulatorId == "RETROARCH" -> "RetroArch isn't installed. $name runs as a RetroArch core."
                    else -> "$name isn't installed. Install it or pick another emulator for this game."
                }
            )
        }

        val internalData = context.dataDir.parentFile?.path ?: "/data/user/0"
        val externalData = Environment.getExternalStorageDirectory().path
        cmd = cmd.replace("%EMULATOR_$emulatorId%", "")
            .replace("%ANDROIDPACKAGE%", emu.packageName)
            .replace("%INTERNALDATA%", internalData)
            .replace("%EXTERNALDATA%", externalData)

        // %INJECT%=file replaces the token with that file's contents (e.g. a PS3 game serial).
        val injectRe = Regex("%INJECT%=(\"[^\"]*\"|\\S+)")
        while (true) {
            val m = injectRe.find(cmd) ?: break
            val spec = m.groupValues[1].trim('"').replace("%BASENAME%", game.baseName)
            val content = if (spec == "%ROM%") {
                readText(Uri.parse(game.docUri))
            } else {
                readSibling(game, spec)
            }?.trim() ?: return Built.Error("Couldn't read $spec next to ${game.fileName}")
            cmd = cmd.replaceRange(m.range, content)
        }

        if (LaunchCommand.needsRealPath(cmd) && game.path == null) {
            return Built.Error("${emu.label} needs a real file path, but this ROM folder isn't on device storage. Use a folder on internal storage or SD card.")
        }

        val spec = LaunchCommand.parse(
            cmd,
            LaunchVars(game.path.orEmpty(), game.rootPath.orEmpty(), game.docUri, game.baseName),
        )
        val intent = Intent()
        intent.action = spec.action ?: Intent.ACTION_MAIN
        spec.category?.let { intent.addCategory(it) }
        when {
            spec.data != null && spec.mimeType != null -> intent.setDataAndType(Uri.parse(spec.data), spec.mimeType)
            spec.data != null -> intent.data = Uri.parse(spec.data)
            spec.mimeType != null -> intent.type = spec.mimeType
        }
        for (e in spec.extras) {
            when (e.kind) {
                ExtraKind.STRING -> intent.putExtra(e.name, e.value)
                ExtraKind.ARRAY -> intent.putExtra(e.name, e.value.split(',').toTypedArray())
                ExtraKind.INTEGER -> e.value.toIntOrNull()?.let { intent.putExtra(e.name, it) }
                ExtraKind.BOOL -> intent.putExtra(e.name, e.value.equals("true", true) || e.value == "1")
            }
        }

        if (emu.activity != null) intent.component = ComponentName(emu.packageName, emu.activity)
        else intent.setPackage(emu.packageName)

        var flags = 0
        if (spec.clearTask) flags = flags or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
        if (spec.clearTop) flags = flags or Intent.FLAG_ACTIVITY_CLEAR_TOP
        if (spec.noHistory) flags = flags or Intent.FLAG_ACTIVITY_NO_HISTORY
        intent.addFlags(flags)

        // URI grants only travel with the data URI or ClipData, so extras that carry content URIs
        // are mirrored into ClipData for emulators that read the ROM from an extra.
        val uris = spec.contentUris.map(Uri::parse)
        if (uris.isNotEmpty()) {
            val clip = ClipData.newRawUri(game.fileName, uris.first())
            uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
            intent.clipData = clip
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        return Built.Ok(intent, uris)
    }

    private fun readText(uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
    }.getOrNull()

    private fun readSibling(game: Game, name: String): String? {
        game.path?.let { p ->
            val f = File(p.substringBeforeLast('/'), name)
            runCatching { if (f.canRead()) return f.readText() }
        }
        val tree = Uri.parse(game.treeUri)
        val parentId = game.id.substringBeforeLast('/', "")
        if (parentId.isEmpty()) return null
        val sibling = Saf.findChild(context.contentResolver, tree, parentId, name) ?: return null
        return readText(Saf.docUri(tree, sibling.docId))
    }
}
