package io.github.audxius.pillar.data

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Plays a game's own music while it stays in focus on the home screen. Music comes from the
 * game's soundtrack on RomM (RomM 5.4+) or from a local folder of files named after games;
 * Pillar never downloads music from anywhere else.
 */
class GameMusic(
    private val context: Context,
    private val romm: RommClient,
    private val store: SettingsStore,
    private val scope: CoroutineScope,
) {
    private class Source(val uri: Uri, val headers: Map<String, String>)

    private val audioExt = setOf("mp3", "ogg", "opus", "m4a", "aac", "flac", "wav")
    @Volatile private var localIndex: Pair<String, Map<String, Uri>>? = null
    private val rommTracks = ConcurrentHashMap<Int, List<RommTrack>>()
    private var player: MediaPlayer? = null
    private var job: Job? = null
    private var current: String? = null

    /** Starts the game's music after a short pause in focus; null (or another game) stops it. */
    fun focus(game: Game?, rommId: Int?) {
        val key = game?.id
        if (key == current) return
        current = key
        job?.cancel()
        job = scope.launch(Dispatchers.Main) {
            fadeOut()
            if (game == null || !store.settings.value.gameMusic) return@launch
            delay(1200)
            val src = withContext(Dispatchers.IO) { runCatching { find(game, rommId) }.getOrNull() } ?: return@launch
            val mp = MediaPlayer()
            try {
                mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                withContext(Dispatchers.IO) {
                    mp.setDataSource(context, src.uri, src.headers)
                    mp.prepare()
                }
                mp.isLooping = true
                mp.setVolume(0f, 0f)
                mp.start()
                player = mp
                for (i in 1..20) { mp.setVolume(VOLUME * i / 20f, VOLUME * i / 20f); delay(50) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) { runCatching { mp.release() }; throw e }
                runCatching { mp.release() }
            }
        }
    }

    fun stop() = focus(null, null)

    private suspend fun fadeOut() {
        val mp = player ?: return
        player = null
        for (i in 9 downTo 0) { runCatching { mp.setVolume(VOLUME * i / 10f, VOLUME * i / 10f) }; delay(25) }
        runCatching { mp.stop(); mp.release() }
    }

    private suspend fun find(game: Game, rommId: Int?): Source? {
        local(game)?.let { return Source(it, emptyMap()) }
        if (rommId != null && store.settings.value.rommConfigured) {
            val tracks = rommTracks[rommId] ?: romm.soundtracks(rommId).also { rommTracks[rommId] = it }
            pickTrack(tracks)?.let { t ->
                val (url, auth) = romm.trackUrl(t)
                return Source(Uri.parse(url), auth?.let { mapOf("Authorization" to it) }.orEmpty())
            }
        }
        return null
    }

    /** The main theme: a track called "title"/"theme"/"opening", else track 1, else the first. */
    private fun pickTrack(tracks: List<RommTrack>): RommTrack? {
        val named = tracks.firstOrNull { t ->
            val n = (t.meta?.title ?: t.fileName).lowercase()
            listOf("title", "main theme", "theme", "opening", "intro").any { it in n }
        }
        return named ?: tracks.minByOrNull { it.meta?.track ?: Int.MAX_VALUE } ?: tracks.firstOrNull()
    }

    /** Files in the music folder (or one sub-folder deep) whose name matches the game. */
    private fun local(game: Game): Uri? {
        val dir = store.settings.value.musicDir ?: return null
        val index = localIndex?.takeIf { it.first == dir }?.second ?: buildIndex(dir).also { localIndex = dir to it }
        val keys = listOfNotNull(game.fileName, "${game.title}.x", game.datName?.let { "$it.x" }).map { ThumbMatch.key(it) }
        return keys.firstNotNullOfOrNull { index[it] }
    }

    private fun buildIndex(dir: String): Map<String, Uri> {
        val tree = Uri.parse(dir)
        val resolver = context.contentResolver
        val out = HashMap<String, Uri>()
        val rootId = runCatching { Saf.rootDocId(tree) }.getOrNull() ?: return out
        for (e in Saf.list(resolver, tree, rootId)) {
            if (e.isDir) {
                // A folder per game: use its main theme (by name) or its first track.
                val files = Saf.list(resolver, tree, e.docId).filter { !it.isDir && it.name.substringAfterLast('.').lowercase() in audioExt }.sortedBy { it.name }
                val main = files.firstOrNull { f -> listOf("title", "theme", "opening", "intro").any { it in f.name.lowercase() } } ?: files.firstOrNull()
                if (main != null) out.putIfAbsent(ThumbMatch.key("${e.name}.x"), Saf.docUri(tree, main.docId))
            } else if (e.name.substringAfterLast('.').lowercase() in audioExt) {
                out.putIfAbsent(ThumbMatch.key(e.name), Saf.docUri(tree, e.docId))
            }
        }
        return out
    }

    /** Forgets the folder listing, e.g. after the folder changes. */
    fun rescan() { localIndex = null }

    private companion object { const val VOLUME = 0.6f }
}
