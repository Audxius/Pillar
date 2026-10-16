package io.github.audxius.pillar.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What sync knows about one file, keyed like RetroArch: "saves/Snes9x/Game.srm". */
enum class SyncFileState { SYNCED, LOCAL_CHANGED, SERVER_CHANGED, CONFLICT, LOCAL_ONLY, SERVER_ONLY }

@Serializable
data class SyncConflict(
    val key: String,
    val localHash: String,
    val serverHash: String,
    /** When each copy was last written, 0 when unknown (or the copy was deleted). */
    val localModified: Long = 0,
    val serverModified: Long = 0,
    val localSize: Long = -1,
    val serverSize: Long = -1,
)

@Serializable
data class SyncResult(
    val finishedAt: Long = 0,
    val uploaded: Int = 0,
    val downloaded: Int = 0,
    val deletedLocal: Int = 0,
    /** Saves deleted on this device that were deliberately left on the server. */
    val keptOnServer: Int = 0,
    val conflicts: List<SyncConflict> = emptyList(),
    /** Files the server refused (e.g. RomM can't match them to a game). */
    val skipped: List<String> = emptyList(),
    val error: String? = null,
)

sealed interface SyncStatus {
    data object Off : SyncStatus
    data object Idle : SyncStatus
    data class Running(val message: String) : SyncStatus
}

@Serializable
private data class SyncState(
    /** What this device and the server agreed on after the last sync ("" = deleted). */
    val local: Map<String, String> = emptyMap(),
    /** key -> "size:mtime:md5" so unchanged files aren't re-hashed every sync. */
    val hashCache: Map<String, String> = emptyMap(),
    val last: SyncResult? = null,
)

/** A local file in one of the synced folders. */
data class LocalSaveFile(val key: String, val name: String, val tree: Uri, val docId: String, val size: Long, val modified: Long)

/** Pure three-way merge, a port of RetroArch's tasks/task_cloudsync.c decision table. */
object SyncPlanner {
    sealed interface Action {
        val key: String
        data class Upload(override val key: String) : Action
        data class Download(override val key: String, val hash: String) : Action
        data class DeleteLocal(override val key: String) : Action
        data class DeleteRemote(override val key: String) : Action
        data class Record(override val key: String, val hash: String) : Action
        data class Conflict(override val key: String, val local: String, val server: String) : Action
        data class Drop(override val key: String) : Action
    }

    /**
     * [server], [local] and [current] map keys to MD5 hashes; "" in server/local is a tombstone.
     * Keys are compared case-insensitively, like RetroArch.
     */
    fun plan(server: Map<String, String>, local: Map<String, String>, current: Map<String, String>): List<Action> {
        fun ci(m: Map<String, String>) = m.entries.associate { it.key.lowercase() to (it.key to it.value) }
        val s = ci(server); val l = ci(local); val c = ci(current)
        val keys = (s.keys + l.keys + c.keys).toSortedSet()
        val out = ArrayList<Action>()
        for (k in keys) {
            val key = c[k]?.first ?: s[k]?.first ?: l[k]!!.first
            val sh = s[k]?.second
            val lh = l[k]?.second
            val ch = c[k]?.second
            out += when {
                sh == null && ch != null -> Action.Upload(key)
                sh == null && lh != null -> Action.Drop(key)
                sh == null -> continue
                ch == null && sh.isEmpty() -> Action.Record(key, "")
                ch == null && lh == null -> Action.Download(key, sh)
                ch == null && lh!!.isEmpty() -> Action.Download(key, sh)
                ch == null -> if (sh == lh) Action.DeleteRemote(key) else Action.Conflict(key, "", sh)
                // A file that already matches the server is in sync no matter what we recorded.
                ch == sh -> Action.Record(key, sh)
                lh == null -> Action.Conflict(key, ch, sh)
                sh.isEmpty() -> if (ch == lh) Action.DeleteLocal(key) else Action.Conflict(key, ch, sh)
                sh != lh && ch != lh -> Action.Conflict(key, ch, sh)
                ch != lh -> Action.Upload(key)
                else -> Action.Download(key, sh)
            }
        }
        return out
    }

    fun encodeManifest(entries: Map<String, String>): String {
        val sorted = entries.entries.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.key })
        return buildString {
            append("[\n")
            sorted.forEachIndexed { i, e ->
                append("  {\n    \"path\": ").append(jsonString(e.key)).append(",\n    \"hash\": ").append(jsonString(e.value)).append("\n  }")
                if (i < sorted.lastIndex) append(",")
                append("\n")
            }
            append("]\n")
        }
    }

    private fun jsonString(s: String) = JsonPrimitive(s).toString()

    /** Mirrors RetroArch's parser: any string member other than "path" is the hash; null means deleted. */
    fun decodeManifest(body: String): Map<String, String> {
        if (body.isBlank()) return emptyMap()
        val arr = PillarJson.parseToJsonElement(body) as? JsonArray ?: throw IOException("manifest.server isn't a JSON array")
        val out = LinkedHashMap<String, String>()
        for (el in arr) {
            val obj = el as? JsonObject ?: continue
            val path = (obj["path"] as? JsonPrimitive)?.contentOrNull ?: continue
            var hash = ""
            for ((k, v) in obj) {
                if (k == "path") continue
                if (v is JsonPrimitive && v.isString) hash = v.content
                if (v is JsonNull && k == "hash") hash = ""
            }
            out[path] = hash
        }
        return out
    }

    fun ignored(key: String): Boolean {
        val name = key.substringAfterLast('/')
        return name == ".DS_Store" || name.endsWith(".rafetching") || name.endsWith(".tmp") || name.endsWith(".pillartmp")
    }
}

/**
 * Keeps local save folders in sync with the WebDAV server used by RetroArch's Cloud Sync, using
 * the same manifest.server format and decision rules so both can share one server safely.
 */
class SaveSync(
    private val context: Context,
    private val http: OkHttpClient,
    private val store: SettingsStore,
    private val scope: CoroutineScope,
) {
    private val stateFile = File(context.filesDir, "sync-state.json")
    private val backupDir = File(context.filesDir, "save-backups").apply { mkdirs() }
    private val tmpDir = File(context.cacheDir, "sync").apply { mkdirs() }
    private val mutex = Mutex()
    private var lastRun = 0L

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()
    private val _last = MutableStateFlow<SyncResult?>(null)
    val last: StateFlow<SyncResult?> = _last.asStateFlow()

    private var state: SyncState = runCatching {
        PillarJson.decodeFromString(SyncState.serializer(), stateFile.readText())
    }.getOrDefault(SyncState()).also { _last.value = it.last }

    private fun persist() = runCatching { stateFile.writeText(PillarJson.encodeToString(SyncState.serializer(), state)) }

    val configured: Boolean
        get() = store.settings.value.let { it.webdavUrl.isNotBlank() && (it.savesDir != null || it.statesDir != null) }

    /** Runs a sync unless one ran very recently. Called whenever Pillar comes to the foreground. */
    fun syncIfDue(minIntervalMs: Long = 30_000) {
        val s = store.settings.value
        if (!configured || !s.syncOnOpen) return
        if (System.currentTimeMillis() - lastRun < minIntervalMs) return
        scope.launch { sync() }
    }

    suspend fun awaitIdle() = mutex.withLock { }

    // ---- WebDAV --------------------------------------------------------------------------------

    private fun base(): HttpUrl {
        var raw = store.settings.value.webdavUrl.trim()
        if (!raw.contains("://")) raw = "http://$raw"
        if (!raw.endsWith("/")) raw += "/"
        return raw.toHttpUrlOrNull() ?: throw IOException("The WebDAV address isn't a valid URL")
    }

    private fun url(key: String): HttpUrl = base().newBuilder().addPathSegments(key).build()

    private fun req(url: HttpUrl): Request.Builder = Request.Builder().url(url).apply {
        val s = store.settings.value
        if (s.webdavUser.isNotBlank() || s.webdavPassword.isNotBlank()) {
            header("Authorization", Credentials.basic(s.webdavUser, s.webdavPassword))
        }
    }

    private fun check(code: Int, what: String) {
        if (code == 401 || code == 403) throw IOException("The WebDAV server rejected the login ($what, HTTP $code)")
        if (code !in 200..299) throw IOException("$what failed with HTTP $code")
    }

    private data class Fetched(val body: String?, val etag: String?)

    private fun getManifest(): Fetched {
        http.newCall(req(url("manifest.server")).get().build()).execute().use { r ->
            if (r.code == 404) return Fetched(null, null)
            check(r.code, "Reading manifest.server")
            return Fetched(r.body?.string(), r.header("ETag"))
        }
    }

    private fun putManifest(body: String, etag: String?, existed: Boolean) {
        val b = req(url("manifest.server")).put(body.toRequestBody("application/json".toMediaType()))
        // Guards against overwriting a manifest RetroArch wrote while we were syncing.
        if (etag != null) b.header("If-Match", etag) else if (!existed) b.header("If-None-Match", "*")
        http.newCall(b.build()).execute().use { r ->
            if (r.code == 412) throw ManifestChanged()
            check(r.code, "Writing manifest.server")
        }
    }

    private class ManifestChanged : IOException("manifest.server changed during sync")

    private fun mkcolParents(key: String) {
        val parts = key.split('/').dropLast(1)
        var path = ""
        for (p in parts) {
            path += "$p/"
            val u = base().newBuilder().addPathSegments(path.trimEnd('/')).addPathSegment("").build()
            http.newCall(req(u).method("MKCOL", null).build()).execute().use { r ->
                // 405 = already exists; some servers answer 301 for existing collections.
                if (r.code !in 200..299 && r.code != 405 && r.code != 301 && r.code != 409) check(r.code, "Creating folder $path")
            }
        }
    }

    /** The server refused one file; the rest of the sync carries on. */
    private class Rejected(message: String) : IOException(message)

    private fun upload(key: String, file: File) {
        mkcolParents(key)
        http.newCall(req(url(key)).put(file.asRequestBody("application/octet-stream".toMediaType())).build()).execute().use { r ->
            if (r.code == 409 || r.code == 415 || r.code == 422) throw Rejected("server refused it (HTTP ${r.code})")
            check(r.code, "Uploading $key")
        }
    }

    /** Saves the server's current copy of a file into Pillar's backups before it gets replaced. */
    private fun backupRemote(key: String) {
        val stamp = SimpleDateFormat("yyMMdd-HHmmss", Locale.US).format(Date())
        val dest = File(backupDir, "server/$key-$stamp").apply { parentFile?.mkdirs() }
        runCatching { download(key, dest) }.onFailure { dest.delete(); throw IOException("Couldn't back up the server copy of $key, so it wasn't replaced") }
    }

    /** Last-Modified and size of the server's copy, from a HEAD request; (0, -1) when unknown. */
    private fun remoteInfo(key: String): Pair<Long, Long> = runCatching {
        http.newCall(req(url(key)).head().build()).execute().use { r ->
            if (!r.isSuccessful) 0L to -1L
            else (r.headers.getDate("Last-Modified")?.time ?: 0L) to (r.header("Content-Length")?.toLongOrNull() ?: -1L)
        }
    }.getOrDefault(0L to -1L)

    private fun download(key: String, into: File) {
        http.newCall(req(url(key)).get().build()).execute().use { r ->
            check(r.code, "Downloading $key")
            into.outputStream().use { out -> r.body?.byteStream()?.copyTo(out) }
        }
    }

    suspend fun testConnection(): String = withContext(Dispatchers.IO) {
        val m = getManifest()
        val entries = m.body?.let { SyncPlanner.decodeManifest(it) }.orEmpty()
        when {
            m.body == null -> "Connected. No RetroArch manifest yet; Pillar will create one."
            else -> entries.count { it.value.isNotEmpty() }.let { n -> "Connected. Server tracks $n file${if (n == 1) "" else "s"}." }
        }
    }

    // ---- Local files ---------------------------------------------------------------------------

    private fun roots(): List<Pair<String, Uri>> {
        val s = store.settings.value
        return listOfNotNull(s.savesDir?.let { "saves" to Uri.parse(it) }, s.statesDir?.let { "states" to Uri.parse(it) })
    }

    private val coreFile = File(context.filesDir, "core-folders.txt")
    private val _coreFolders = MutableStateFlow(runCatching { coreFile.readLines().filter { it.isNotBlank() }.toSet() }.getOrDefault(emptySet()))
    /**
     * RetroArch keeps saves and states in one folder per core ("Snes9x", "mGBA"), so these are the
     * cores the user has actually played with, i.e. ones that are installed.
     */
    val coreFolders: StateFlow<Set<String>> = _coreFolders.asStateFlow()

    fun listLocal(): List<LocalSaveFile> {
        val out = ArrayList<LocalSaveFile>()
        val cores = HashSet<String>()
        for ((top, tree) in roots()) {
            val rootId = runCatching { Saf.rootDocId(tree) }.getOrNull() ?: continue
            fun walk(docId: String, rel: String, depth: Int) {
                for (e in Saf.list(context.contentResolver, tree, docId)) {
                    val path = if (rel.isEmpty()) e.name else "$rel/${e.name}"
                    if (e.isDir && depth == 0) cores += e.name
                    if (e.isDir) { if (depth < 6) walk(e.docId, path, depth + 1); continue }
                    val key = "$top/$path"
                    if (SyncPlanner.ignored(key)) continue
                    out += LocalSaveFile(key, e.name, tree, e.docId, e.size, e.modified)
                }
            }
            walk(rootId, "", 0)
        }
        if (cores.isNotEmpty() && cores != _coreFolders.value) {
            // Only ever grows: a core whose saves were cleaned up is still installed.
            _coreFolders.value = _coreFolders.value + cores
            runCatching { coreFile.writeText(_coreFolders.value.joinToString("\n")) }
        }
        return out
    }

    private fun md5(f: LocalSaveFile): String {
        val cacheKey = "${f.size}:${f.modified}"
        state.hashCache[f.key]?.let { if (it.substringBeforeLast(':') == cacheKey) return it.substringAfterLast(':') }
        val hash = readMd5(f)
        state = state.copy(hashCache = state.hashCache + (f.key to "$cacheKey:$hash"))
        return hash
    }

    private fun readMd5(f: LocalSaveFile): String {
        val digest = MessageDigest.getInstance("MD5")
        context.contentResolver.openInputStream(Saf.docUri(f.tree, f.docId))?.use { input ->
            val buf = ByteArray(256 * 1024)
            while (true) { val n = input.read(buf); if (n < 0) break; digest.update(buf, 0, n) }
        } ?: throw IOException("Can't read ${f.key}")
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Hashes for status display only, kept apart from the sync state so a running sync is never overwritten. */
    private val displayHashes = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun fileMd5(f: File): String {
        val digest = MessageDigest.getInstance("MD5")
        f.inputStream().use { input ->
            val buf = ByteArray(256 * 1024)
            while (true) { val n = input.read(buf); if (n < 0) break; digest.update(buf, 0, n) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun rootFor(key: String): Pair<Uri, String>? {
        val top = key.substringBefore('/')
        val tree = roots().firstOrNull { it.first == top }?.second ?: return null
        return tree to key.substringAfter('/')
    }

    /** Copies a downloaded file into place, creating folders, and backs up whatever it replaces. */
    private fun writeLocal(key: String, src: File, existing: LocalSaveFile?) {
        val (tree, rel) = rootFor(key) ?: throw IOException("No local folder for $key")
        val resolver = context.contentResolver
        existing?.let { backup(it) }
        var parent = Saf.rootDocId(tree)
        for (dir in rel.split('/').dropLast(1)) {
            parent = Saf.ensureDir(resolver, tree, parent, dir) ?: throw IOException("Can't create folder $dir")
        }
        val name = rel.substringAfterLast('/')
        val target = existing?.let { Saf.docUri(it.tree, it.docId) }
            ?: Saf.findChild(resolver, tree, parent, name)?.let { Saf.docUri(tree, it.docId) }
            ?: Saf.createFile(resolver, tree, parent, name)
            ?: throw IOException("Can't create $name")
        resolver.openOutputStream(target, "wt")?.use { out -> src.inputStream().use { it.copyTo(out) } }
            ?: throw IOException("Can't write $name")
    }

    /**
     * Puts a save file in place as this device's copy of [key] (e.g. "saves/Snes9x/Game.srm"),
     * backing up whatever was there. The next sync then uploads it like any local change.
     */
    suspend fun installSave(key: String, from: File) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val existing = listLocal().firstOrNull { it.key.equals(key, true) }
            writeLocal(key, from, existing)
        }
    }

    private fun backup(f: LocalSaveFile) {
        val stamp = SimpleDateFormat("yyMMdd-HHmmss", Locale.US).format(Date())
        val dest = File(backupDir, "${f.key}-$stamp").apply { parentFile?.mkdirs() }
        context.contentResolver.openInputStream(Saf.docUri(f.tree, f.docId))?.use { input ->
            dest.outputStream().use { input.copyTo(it) }
        }
    }

    fun backups(): List<File> = backupDir.walkTopDown().filter { it.isFile }.sortedByDescending { it.lastModified() }.toList()

    /** Restores a backup over the current file; the next sync uploads it as a local change. */
    suspend fun restoreBackup(file: File) = withContext(Dispatchers.IO) {
        // Backups are "<key>-yymmdd-HHmmss", server copies live under "server/".
        val key = file.relativeTo(backupDir).path.replace(File.separatorChar, '/').removePrefix("server/")
            .substringBeforeLast('-').substringBeforeLast('-')
        val existing = listLocal().firstOrNull { it.key.equals(key, ignoreCase = true) }
        writeLocal(key, file, existing)
    }

    suspend fun deleteLocal(f: LocalSaveFile) = withContext(Dispatchers.IO) {
        backup(f)
        Saf.delete(context.contentResolver, Saf.docUri(f.tree, f.docId))
    }

    /** Per-file status for the save manager, based on the last sync. */
    fun fileStates(files: List<LocalSaveFile>): Map<String, SyncFileState> {
        val conflicts = _last.value?.conflicts.orEmpty().map { it.key.lowercase() }.toSet()
        return files.associate { f ->
            val recorded = state.local.entries.firstOrNull { it.key.equals(f.key, true) }?.value
            // A file sync just wrote has a new timestamp, so hash it again rather than call it changed.
            val stamp = "${f.size}:${f.modified}"
            val current = listOfNotNull(state.hashCache[f.key], displayHashes[f.key])
                .firstOrNull { it.substringBeforeLast(':') == stamp }?.substringAfterLast(':')
                ?: if (recorded == null) null else runCatching { readMd5(f) }.getOrNull()?.also { displayHashes[f.key] = "$stamp:$it" }
            val sameAsRecorded = current != null && current == recorded
            f.key to when {
                f.key.lowercase() in conflicts -> SyncFileState.CONFLICT
                recorded == null -> SyncFileState.LOCAL_ONLY
                sameAsRecorded -> SyncFileState.SYNCED
                else -> SyncFileState.LOCAL_CHANGED
            }
        }
    }

    // ---- Sync ----------------------------------------------------------------------------------

    suspend fun sync(resolve: Map<String, Boolean> = emptyMap()): SyncResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            lastRun = System.currentTimeMillis()
            if (!configured) return@withContext SyncResult(error = "Sync isn't set up").also { _status.value = SyncStatus.Off }
            val result = runCatching {
                try { syncOnce(resolve) } catch (e: ManifestChanged) {
                    // RetroArch wrote the manifest while we were working; start over with its version.
                    syncOnce(resolve)
                }
            }.getOrElse { e -> SyncResult(finishedAt = System.currentTimeMillis(), error = e.message ?: e.javaClass.simpleName) }
            state = state.copy(last = result)
            persist()
            _last.value = result
            _status.value = SyncStatus.Idle
            result
        }
    }

    /** [resolve] maps conflicted keys to true = keep this device's file, false = keep the server's. */
    private fun syncOnce(resolve: Map<String, Boolean>): SyncResult {
        _status.value = SyncStatus.Running("Checking server…")
        val fetched = getManifest()
        val server = LinkedHashMap(fetched.body?.let { SyncPlanner.decodeManifest(it) }.orEmpty())
        val tops = roots().map { it.first + "/" }
        fun ours(key: String) = tops.any { key.startsWith(it, ignoreCase = true) }

        _status.value = SyncStatus.Running("Reading local saves…")
        val files = listLocal()
        val byKey = files.associateBy { it.key.lowercase() }
        val current = files.associate { it.key to md5(it) }

        val plan = SyncPlanner.plan(
            server.filterKeys { ours(it) },
            state.local.filterKeys { ours(it) },
            current,
        )
        val newLocal = LinkedHashMap(state.local)
        var serverChanged = false
        var up = 0; var down = 0; var delL = 0; var kept = 0
        val conflicts = ArrayList<SyncConflict>()
        val skipped = ArrayList<String>()

        fun setServer(key: String, hash: String) {
            server.keys.firstOrNull { it.equals(key, true) }?.let { server.remove(it) }
            server[key] = hash
            serverChanged = true
        }
        fun setLocal(key: String, hash: String) {
            newLocal.keys.firstOrNull { it.equals(key, true) }?.let { newLocal.remove(it) }
            newLocal[key] = hash
        }
        fun doUpload(key: String, serverHash: String?) {
            val f = byKey[key.lowercase()] ?: return
            // Non-destructive: keep the server's current copy before replacing it.
            if (!serverHash.isNullOrEmpty()) backupRemote(f.key)
            val tmp = File(tmpDir, "up.pillartmp")
            context.contentResolver.openInputStream(Saf.docUri(f.tree, f.docId))?.use { i -> tmp.outputStream().use { i.copyTo(it) } }
            val hash = fileMd5(tmp)
            try {
                upload(f.key, tmp)
            } catch (e: Rejected) {
                // e.g. RomM answers 409 when a save's name matches no game; skip just this file.
                skipped += "${f.key}: ${e.message}"
                return
            } finally {
                tmp.delete()
            }
            setServer(f.key, hash); setLocal(f.key, hash); up++
        }
        fun doDownload(key: String, expected: String) {
            val tmp = File(tmpDir, "down.pillartmp")
            download(key, tmp)
            val actual = fileMd5(tmp)
            writeLocal(key, tmp, byKey[key.lowercase()])
            tmp.delete()
            // Like RetroArch: if the server file doesn't match its manifest, record what we really got.
            if (actual != expected) setServer(key, actual)
            setLocal(key, actual); down++
        }

        for ((i, a) in plan.withIndex()) {
            _status.value = SyncStatus.Running("Syncing ${i + 1} of ${plan.size}…")
            val serverHash = server.entries.firstOrNull { it.key.equals(a.key, true) }?.value
            when (a) {
                is SyncPlanner.Action.Upload -> doUpload(a.key, serverHash)
                is SyncPlanner.Action.Download -> doDownload(a.key, a.hash)
                is SyncPlanner.Action.Record -> setLocal(a.key, a.hash)
                is SyncPlanner.Action.Drop -> newLocal.keys.firstOrNull { it.equals(a.key, true) }?.let { newLocal.remove(it) }
                is SyncPlanner.Action.DeleteLocal -> {
                    // Only happens with servers that record deletions (RomM never does). Backed up first.
                    byKey[a.key.lowercase()]?.let { backup(it); Saf.delete(context.contentResolver, Saf.docUri(it.tree, it.docId)) }
                    setLocal(a.key, ""); delL++
                }
                // Non-destructive: a save deleted on this device is never deleted from the server.
                is SyncPlanner.Action.DeleteRemote -> kept++
                is SyncPlanner.Action.Conflict -> when (resolve[a.key]) {
                    true -> if (byKey.containsKey(a.key.lowercase())) doUpload(a.key, a.server) else {
                        // Keeping a local deletion: leave the server alone and stop flagging it.
                        setLocal(a.key, a.server); kept++
                    }
                    false -> if (a.server.isNotEmpty()) doDownload(a.key, a.server) else {
                        byKey[a.key.lowercase()]?.let { backup(it); Saf.delete(context.contentResolver, Saf.docUri(it.tree, it.docId)) }
                        setLocal(a.key, ""); delL++
                    }
                    null -> {
                        val local = byKey[a.key.lowercase()]
                        val (serverTime, serverSize) = if (a.server.isNotEmpty()) remoteInfo(a.key) else 0L to -1L
                        conflicts += SyncConflict(
                            a.key, a.local, a.server,
                            localModified = local?.modified ?: 0, serverModified = serverTime,
                            localSize = local?.size ?: -1, serverSize = serverSize,
                        )
                    }
                }
            }
        }

        if (serverChanged) {
            _status.value = SyncStatus.Running("Updating manifest…")
            putManifest(SyncPlanner.encodeManifest(server), fetched.etag, existed = fetched.body != null)
        }
        // Our record only moves forward once the server manifest is safely written.
        state = state.copy(local = newLocal)
        persist()
        return SyncResult(System.currentTimeMillis(), up, down, delL, kept, conflicts, skipped = skipped)
    }
}
