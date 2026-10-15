package io.github.audxius.pillar.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.InputStream
import java.util.zip.ZipInputStream

sealed interface DownloadState {
    val rom: RommRom
    data class Queued(override val rom: RommRom) : DownloadState
    data class Running(override val rom: RommRom, val bytes: Long, val total: Long) : DownloadState {
        val fraction: Float get() = if (total <= 0) 0f else (bytes.toFloat() / total).coerceIn(0f, 1f)
    }
    data class Done(override val rom: RommRom, val system: String) : DownloadState
    data class Failed(override val rom: RommRom, val message: String) : DownloadState
}

class Downloads(
    private val context: Context,
    private val http: OkHttpClient,
    private val romm: RommClient,
    private val catalog: SystemCatalog,
    private val store: SettingsStore,
    private val library: LibraryRepository,
) {
    private val _states = MutableStateFlow<Map<Int, DownloadState>>(emptyMap())
    val states: StateFlow<Map<Int, DownloadState>> = _states.asStateFlow()

    private val cancelled: MutableSet<Int> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    private class UserCancelled : Exception("cancelled")

    fun targetSystem(rom: RommRom): String? = catalog.resolveRommPlatform(rom.platformSlug, rom.platformFsSlug)

    fun enqueue(rom: RommRom) {
        val current = _states.value[rom.id]
        if (current is DownloadState.Queued || current is DownloadState.Running) return
        cancelled.remove(rom.id)
        _states.update { it + (rom.id to DownloadState.Queued(rom)) }
        ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
    }

    fun cancel(romId: Int) {
        cancelled.add(romId)
        _states.update { it - romId }
    }

    fun clearFinished() {
        _states.update { m -> m.filterValues { it is DownloadState.Queued || it is DownloadState.Running } }
    }

    fun nextQueued(): RommRom? = _states.value.values.firstOrNull { it is DownloadState.Queued }?.rom

    /** Downloads one rom into the matching system folder, extracting multi-file zips. */
    suspend fun run(rom: RommRom) = withContext(Dispatchers.IO) {
        val settings = store.settings.value
        try {
            val rootUri = (settings.downloadRoot ?: settings.romRoots.firstOrNull())
                ?: throw RommException("Pick a ROM folder in Settings → Library first")
            val tree = Uri.parse(rootUri)
            val resolver = context.contentResolver
            val rootId = Saf.rootDocId(tree)
            val system = targetSystem(rom)
            val folderName = system ?: rom.platformFsSlug?.takeIf { it.isNotBlank() } ?: rom.platformSlug ?: "romm"

            // If the picked root is itself a system folder, drop files straight into it.
            val rootName = Saf.displayName(resolver, tree, rootId)
            val systemDir = if (rootName != null && catalog.resolveFolder(rootName) == system && system != null) rootId
            else Saf.ensureDir(resolver, tree, rootId, folderName) ?: throw RommException("Couldn't create the $folderName folder")

            set(DownloadState.Running(rom, 0, rom.fsSizeBytes))
            http.newCall(romm.downloadRequest(rom)).execute().use { resp ->
                if (!resp.isSuccessful) throw RommException("RomM returned HTTP ${resp.code}")
                val body = resp.body ?: throw RommException("Empty download")
                val total = body.contentLength().takeIf { it > 0 } ?: rom.fsSizeBytes
                val disposition = resp.header("Content-Disposition").orEmpty()
                val isZipBundle = rom.isMultiFile && disposition.contains(".zip", ignoreCase = true)
                body.byteStream().use { input ->
                    if (isZipBundle) {
                        val dirId = Saf.ensureDir(resolver, tree, systemDir, rom.fsName)
                            ?: throw RommException("Couldn't create a folder for ${rom.fsName}")
                        try {
                            extractZip(input, tree, dirId, rom, total)
                        } catch (e: Exception) {
                            Saf.delete(resolver, Saf.docUri(tree, dirId))
                            throw e
                        }
                    } else {
                        val existing = Saf.findChild(resolver, tree, systemDir, rom.fsName)
                        existing?.let { Saf.delete(resolver, Saf.docUri(tree, it.docId)) }
                        val target = Saf.createFile(resolver, tree, systemDir, rom.fsName)
                            ?: throw RommException("Couldn't create ${rom.fsName}")
                        try {
                            resolver.openOutputStream(target, "w")?.use { out -> copy(input, out, rom, total, 0) }
                                ?: throw RommException("Couldn't write ${rom.fsName}")
                        } catch (e: Exception) {
                            Saf.delete(resolver, target)
                            throw e
                        }
                    }
                }
            }

            if (system != null) {
                library.rescanSystem(system)
                linkToLibrary(rom, system)
            }
            set(DownloadState.Done(rom, system ?: folderName))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (rom.id in cancelled) return@withContext
            set(DownloadState.Failed(rom, e.message ?: "Download failed"))
        }
    }

    private suspend fun linkToLibrary(rom: RommRom, system: String) {
        val stem = LibraryRepository.stem(rom.fsName)
        val game = library.games.value.firstOrNull { it.system == system && it.fileName == rom.fsName }
            ?: library.games.value.firstOrNull { it.system == system && it.id.contains("/${rom.fsName}/") }
            ?: library.games.value.firstOrNull { it.system == system && LibraryRepository.stem(it.fileName) == stem }
            ?: return
        val cover = romm.coverUrl(rom)
        store.updateUser { u ->
            u.copy(
                rommIds = u.rommIds + (game.id to rom.id),
                raIds = rom.raId?.let { u.raIds + (game.id to it) } ?: u.raIds,
                remoteCover = cover?.let { u.remoteCover + (game.id to it) } ?: u.remoteCover,
            )
        }
    }

    private suspend fun copy(input: InputStream, out: java.io.OutputStream, rom: RommRom, total: Long, startAt: Long): Long {
        val buf = ByteArray(256 * 1024)
        var done = startAt
        var lastReport = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            if (rom.id in cancelled) throw UserCancelled()
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            done += n
            if (done - lastReport > 512 * 1024) {
                lastReport = done
                set(DownloadState.Running(rom, done, total))
            }
        }
        return done
    }

    private suspend fun extractZip(input: InputStream, tree: Uri, dirId: String, rom: RommRom, total: Long) {
        val resolver = context.contentResolver
        val counting = CountingStream(input)
        ZipInputStream(counting).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val parts = entry.name.split('/').filter { it.isNotEmpty() && it != ".." }
                if (parts.isEmpty()) continue
                var parent = dirId
                for (p in parts.dropLast(1)) {
                    parent = Saf.ensureDir(resolver, tree, parent, p) ?: throw RommException("Couldn't create $p")
                }
                val name = parts.last()
                Saf.findChild(resolver, tree, parent, name)?.let { Saf.delete(resolver, Saf.docUri(tree, it.docId)) }
                val target = Saf.createFile(resolver, tree, parent, name) ?: throw RommException("Couldn't create $name")
                resolver.openOutputStream(target, "w")?.use { out ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        if (rom.id in cancelled) throw UserCancelled()
                        val n = zip.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        set(DownloadState.Running(rom, counting.count, total))
                    }
                }
            }
        }
    }

    private fun set(state: DownloadState) {
        if (state.rom.id in cancelled) return
        _states.update { it + (state.rom.id to state) }
    }

    private class CountingStream(private val inner: InputStream) : InputStream() {
        var count = 0L
        override fun read(): Int = inner.read().also { if (it >= 0) count++ }
        override fun read(b: ByteArray, off: Int, len: Int): Int = inner.read(b, off, len).also { if (it > 0) count += it }
        override fun close() = inner.close()
    }
}
