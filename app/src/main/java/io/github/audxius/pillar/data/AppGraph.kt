package io.github.audxius.pillar.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.audxius.pillar.BuildConfig
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

sealed interface Loadable<out T> {
    data object Idle : Loadable<Nothing>
    data object Loading : Loadable<Nothing>
    data class Ready<T>(val value: T) : Loadable<T>
    data class Error(val message: String) : Loadable<Nothing>
}

val <T> Loadable<T>.valueOrNull: T? get() = (this as? Loadable.Ready<T>)?.value

/** Wires every repository together; created once by [io.github.audxius.pillar.PillarApp]. */
class AppGraph(val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", "Pillar/${BuildConfig.VERSION_NAME} (Android)").build())
        }
        .build()

    val store = SettingsStore(context, scope)
    val catalog = SystemCatalog(context)
    val library = LibraryRepository(context, scope, catalog, store)
    val launcher = GameLauncher(context, catalog)
    val romm = RommClient(http) { store.settings.value }
    val raClient = RaClient(context, http) { store.settings.value }
    val ra = RaRepository(raClient, store, scope)
    val rommRepo = RommRepository(romm, store, scope)
    val downloads = Downloads(context, http, romm, catalog, store, library)
    val apps = AppsRepository(context, scope)

    fun markPlayed(game: Game) {
        scope.launch {
            store.updateUser { u ->
                u.copy(
                    lastPlayed = u.lastPlayed + (game.id to System.currentTimeMillis()),
                    playCount = u.playCount + (game.id to ((u.playCount[game.id] ?: 0) + 1)),
                )
            }
        }
    }
}

class RaRepository(private val client: RaClient, private val store: SettingsStore, private val scope: CoroutineScope) {
    private val _profile = MutableStateFlow<Loadable<RaProfile>>(Loadable.Idle)
    val profile: StateFlow<Loadable<RaProfile>> = _profile.asStateFlow()
    private val _recent = MutableStateFlow<Loadable<List<RaRecentAchievement>>>(Loadable.Idle)
    val recent: StateFlow<Loadable<List<RaRecentAchievement>>> = _recent.asStateFlow()
    private val _played = MutableStateFlow<Loadable<List<RaRecentGame>>>(Loadable.Idle)
    val played: StateFlow<Loadable<List<RaRecentGame>>> = _played.asStateFlow()

    private var lastRefresh = 0L
    private val progressCache = HashMap<Int, Pair<Long, RaGameProgress>>()

    init {
        // Credentials changed: drop everything so stale data from another account never shows.
        scope.launch {
            store.settings.map { it.raUser to it.raApiKey }.distinctUntilChanged().drop(1).collect {
                lastRefresh = 0
                progressCache.clear()
                _profile.value = Loadable.Idle; _recent.value = Loadable.Idle; _played.value = Loadable.Idle
                refresh(force = true)
            }
        }
    }

    fun refresh(force: Boolean = false) {
        if (!store.settings.value.raConfigured) return
        val now = System.currentTimeMillis()
        if (!force && now - lastRefresh < 2 * 60 * 1000) return
        lastRefresh = now
        scope.launch {
            if (_profile.value !is Loadable.Ready) _profile.value = Loadable.Loading
            _profile.value = runCatching { Loadable.Ready(client.profile()) }.getOrElse { Loadable.Error(it.message ?: "Failed") }
            if (_recent.value !is Loadable.Ready) _recent.value = Loadable.Loading
            _recent.value = runCatching { Loadable.Ready(client.recentAchievements(14)) }.getOrElse { Loadable.Error(it.message ?: "Failed") }
            if (_played.value !is Loadable.Ready) _played.value = Loadable.Loading
            _played.value = runCatching { Loadable.Ready(client.recentlyPlayed(15)) }.getOrElse { Loadable.Error(it.message ?: "Failed") }
        }
    }

    suspend fun progress(gameId: Int, force: Boolean = false): RaGameProgress {
        val cached = progressCache[gameId]
        if (!force && cached != null && System.currentTimeMillis() - cached.first < 5 * 60 * 1000) return cached.second
        return client.gameProgress(gameId).also { progressCache[gameId] = System.currentTimeMillis() to it }
    }

    /** Known id (from RomM or a previous match), otherwise a title match against RA's game list. */
    suspend fun idFor(game: Game, catalog: SystemCatalog): Int? {
        store.userData.value.raIds[game.id]?.let { return it }
        val settings = store.settings.value
        if (!settings.raConfigured || !settings.raMatchTitles) return null
        val console = catalog.meta(game.system).raConsole ?: return null
        val title = store.userData.value.customTitle[game.id] ?: game.title
        val id = runCatching { client.matchTitle(console, title) }.getOrNull() ?: return null
        store.updateUser { it.copy(raIds = it.raIds + (game.id to id)) }
        return id
    }
}

class RommRepository(private val client: RommClient, private val store: SettingsStore, private val scope: CoroutineScope) {
    private val _platforms = MutableStateFlow<Loadable<List<RommPlatform>>>(Loadable.Idle)
    val platforms: StateFlow<Loadable<List<RommPlatform>>> = _platforms.asStateFlow()
    private val _latest = MutableStateFlow<Loadable<List<RommRom>>>(Loadable.Idle)
    val latest: StateFlow<Loadable<List<RommRom>>> = _latest.asStateFlow()
    private var lastRefresh = 0L

    init {
        scope.launch {
            store.settings.map { listOf(it.rommUrl, it.rommUser, it.rommPassword, it.rommToken) }
                .distinctUntilChanged().drop(1).collect {
                    lastRefresh = 0
                    _platforms.value = Loadable.Idle; _latest.value = Loadable.Idle
                    refresh(force = true)
                }
        }
    }

    fun refresh(force: Boolean = false) {
        if (!store.settings.value.rommConfigured) return
        val now = System.currentTimeMillis()
        if (!force && now - lastRefresh < 2 * 60 * 1000) return
        lastRefresh = now
        scope.launch {
            if (_platforms.value !is Loadable.Ready) _platforms.value = Loadable.Loading
            _platforms.value = runCatching { Loadable.Ready(client.platforms()) }.getOrElse { Loadable.Error(it.message ?: "Failed") }
            if (_latest.value !is Loadable.Ready) _latest.value = Loadable.Loading
            _latest.value = runCatching {
                Loadable.Ready(client.roms(limit = 20, orderBy = "id", orderDir = "desc").items)
            }.getOrElse { Loadable.Error(it.message ?: "Failed") }
        }
    }
}

data class InstalledApp(val packageName: String, val label: String)

class AppsRepository(private val context: Context, private val scope: CoroutineScope) {
    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val apps: StateFlow<List<InstalledApp>> = _apps.asStateFlow()
    private val icons = LruCache<String, Bitmap>(120)

    fun refresh() {
        scope.launch(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            _apps.value = pm.queryIntentActivities(intent, 0)
                .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
                .filter { it.packageName != context.packageName }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
        }
    }

    suspend fun icon(pkg: String): Bitmap? = icons.get(pkg) ?: withContext(Dispatchers.IO) {
        runCatching { context.packageManager.getApplicationIcon(pkg).toBitmap(144, 144) }.getOrNull()
            ?.also { icons.put(pkg, it) }
    }
}
