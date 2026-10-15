package io.github.audxius.pillar.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

const val RA_MEDIA = "https://media.retroachievements.org"

fun raMedia(path: String?): String? = path?.takeIf { it.isNotBlank() }?.let {
    if (it.startsWith("http")) it else RA_MEDIA + (if (it.startsWith("/")) it else "/$it")
}

@Serializable
data class RaProfile(
    @SerialName("User") val user: String = "",
    @SerialName("UserPic") val userPic: String? = null,
    @SerialName("MemberSince") val memberSince: String? = null,
    @SerialName("RichPresenceMsg") val richPresence: String? = null,
    @SerialName("LastGameID") val lastGameId: Int? = null,
    @SerialName("TotalPoints") val totalPoints: Int = 0,
    @SerialName("TotalSoftcorePoints") val totalSoftcorePoints: Int = 0,
    @SerialName("TotalTruePoints") val totalTruePoints: Int = 0,
    @SerialName("Motto") val motto: String? = null,
)

@Serializable
data class RaRecentAchievement(
    @SerialName("Date") val date: String = "",
    @SerialName("HardcoreMode") val hardcore: Int = 0,
    @SerialName("AchievementID") val id: Int = 0,
    @SerialName("Title") val title: String = "",
    @SerialName("Description") val description: String = "",
    @SerialName("Points") val points: Int = 0,
    @SerialName("BadgeURL") val badgeUrl: String? = null,
    @SerialName("GameTitle") val gameTitle: String = "",
    @SerialName("GameIcon") val gameIcon: String? = null,
    @SerialName("GameID") val gameId: Int = 0,
    @SerialName("ConsoleName") val consoleName: String = "",
)

@Serializable
data class RaRecentGame(
    @SerialName("GameID") val gameId: Int = 0,
    @SerialName("ConsoleName") val consoleName: String = "",
    @SerialName("Title") val title: String = "",
    @SerialName("ImageIcon") val imageIcon: String? = null,
    @SerialName("ImageBoxArt") val imageBoxArt: String? = null,
    @SerialName("ImageIngame") val imageIngame: String? = null,
    @SerialName("LastPlayed") val lastPlayed: String? = null,
    @SerialName("NumPossibleAchievements") val numPossible: Int = 0,
    @SerialName("AchievementsTotal") val achievementsTotal: Int = 0,
    @SerialName("NumAchieved") val numAchieved: Int = 0,
    @SerialName("NumAchievedHardcore") val numAchievedHardcore: Int = 0,
    @SerialName("ScoreAchieved") val scoreAchieved: Int = 0,
    @SerialName("PossibleScore") val possibleScore: Int = 0,
) {
    val total: Int get() = maxOf(numPossible, achievementsTotal)
    val progress: Float get() = if (total == 0) 0f else maxOf(numAchieved, numAchievedHardcore).toFloat() / total
}

@Serializable
data class RaAchievement(
    @SerialName("ID") val id: Int = 0,
    @SerialName("Title") val title: String = "",
    @SerialName("Description") val description: String = "",
    @SerialName("Points") val points: Int = 0,
    @SerialName("BadgeName") val badgeName: String = "",
    @SerialName("DisplayOrder") val displayOrder: Int = 0,
    @SerialName("NumAwarded") val numAwarded: Int = 0,
    @SerialName("DateEarned") val dateEarned: String? = null,
    @SerialName("DateEarnedHardcore") val dateEarnedHardcore: String? = null,
    @SerialName("type") val type: String? = null,
) {
    val earned: Boolean get() = !dateEarned.isNullOrBlank() || !dateEarnedHardcore.isNullOrBlank()
    val badgeUrl: String get() = "$RA_MEDIA/Badge/$badgeName.png"
    val lockedBadgeUrl: String get() = "$RA_MEDIA/Badge/${badgeName}_lock.png"
}

data class RaGameProgress(
    val id: Int,
    val title: String,
    val consoleName: String,
    val imageIcon: String?,
    val imageBoxArt: String?,
    val numDistinctPlayers: Int,
    val achievements: List<RaAchievement>,
) {
    val earned: Int get() = achievements.count { it.earned }
    val points: Int get() = achievements.filter { it.earned }.sumOf { it.points }
    val totalPoints: Int get() = achievements.sumOf { it.points }
    val progress: Float get() = if (achievements.isEmpty()) 0f else earned.toFloat() / achievements.size
}

@Serializable
private data class RaGameRaw(
    @SerialName("ID") val id: Int = 0,
    @SerialName("Title") val title: String = "",
    @SerialName("ConsoleName") val consoleName: String = "",
    @SerialName("ImageIcon") val imageIcon: String? = null,
    @SerialName("ImageBoxArt") val imageBoxArt: String? = null,
    @SerialName("NumDistinctPlayers") val numDistinctPlayers: Int = 0,
)

@Serializable
internal data class RaListedGame(
    @SerialName("ID") val id: Int = 0,
    @SerialName("Title") val title: String = "",
    @SerialName("NumAchievements") val numAchievements: Int = 0,
    @SerialName("Hashes") val hashes: List<String> = emptyList(),
)

/** One console's RetroAchievements games, looked up by ROM hash or, failing that, by title. */
class RaGameIndex private constructor(games: List<RaListedGame>) {
    private val hashes = HashMap<String, Int>()
    private val byName = HashMap<String, Int>()
    private val names = HashMap<Int, String>()
    private val titles: ThumbIndex

    init {
        for (g in games) {
            // "~Hack~", "~Homebrew~", "~Test Kit~" and subsets never belong to a retail dump.
            if (g.id == 0 || g.title.startsWith("~") || g.title.contains("[Subset", ignoreCase = true)) continue
            g.hashes.forEach { hashes.putIfAbsent(it.lowercase(), g.id) }
            names[g.id] = g.title.substringBefore('|').trim()
            // RA lists alternate names as "Title | Alt Title". The fake extension keeps
            // ThumbMatch from reading a trailing ".3" as one.
            g.title.split('|').map { it.trim() }.filter { it.isNotEmpty() }.forEach { byName.putIfAbsent("$it.ra", g.id) }
        }
        titles = ThumbIndex(byName.keys)
    }

    fun byHash(md5: String?): Int? = md5?.let { hashes[it.lowercase()] }

    /** RetroAchievements' title for a game id (the first of its alternate names). */
    fun title(id: Int): String? = names[id]

    /** Match for a file name; its tags (region, revision) help pick between close titles. */
    fun byFileName(fileName: String?): Int? = fileName?.takeIf { it.isNotBlank() }?.let(titles::best)?.let { byName[it] }

    /** Match for a plain title without an extension, like a user's custom title. */
    fun byTitle(title: String?): Int? = title?.takeIf { it.isNotBlank() }?.let { byFileName("$it.ra") }

    companion object {
        /** Parses an API_GetGameList response; throws when it isn't a game list. */
        fun parse(body: String): RaGameIndex = RaGameIndex(
            try {
                PillarJson.decodeFromString(ListSerializer(RaListedGame.serializer()), body)
            } catch (e: IllegalArgumentException) {
                throw RaException("Unexpected game list from RetroAchievements")
            }
        )
    }
}

class RaException(message: String) : IOException(message)

class RaClient(
    context: Context,
    private val http: OkHttpClient,
    private val settings: () -> AppSettings,
) {
    private val cacheDir = File(context.cacheDir, "ra").apply { mkdirs() }
    private val indexes = ConcurrentHashMap<Int, RaGameIndex>()

    init {
        // Game lists cached before hashes were requested.
        cacheDir.listFiles { f -> f.name.matches(Regex("games_\\d+\\.json")) }?.forEach { it.delete() }
    }

    private suspend fun call(endpoint: String, params: Map<String, String>): String = withContext(Dispatchers.IO) {
        val s = settings()
        if (!s.raConfigured) throw RaException("Add your RetroAchievements username and web API key in Settings")
        val url: HttpUrl = "https://retroachievements.org/API/$endpoint".toHttpUrl().newBuilder().apply {
            addQueryParameter("y", s.raApiKey.trim())
            params.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()
        val req = Request.Builder().url(url).header("Accept", "application/json").build()
        http.newCall(req).execute().use { resp ->
            if (resp.code == 401 || resp.code == 403) throw RaException("RetroAchievements rejected the API key")
            if (resp.code == 429) throw RaException("RetroAchievements is rate limiting requests, try again shortly")
            if (!resp.isSuccessful) throw RaException("RetroAchievements returned HTTP ${resp.code}")
            resp.body?.string() ?: throw RaException("Empty response from RetroAchievements")
        }
    }

    private val user get() = settings().raUser.trim()

    suspend fun profile(): RaProfile =
        PillarJson.decodeFromString(RaProfile.serializer(), call("API_GetUserProfile.php", mapOf("u" to user)))

    suspend fun recentAchievements(days: Int = 7): List<RaRecentAchievement> =
        PillarJson.decodeFromString(
            ListSerializer(RaRecentAchievement.serializer()),
            call("API_GetUserRecentAchievements.php", mapOf("u" to user, "m" to (days * 24 * 60).toString())),
        ).sortedByDescending { it.date }

    suspend fun recentlyPlayed(count: Int = 12): List<RaRecentGame> =
        PillarJson.decodeFromString(
            ListSerializer(RaRecentGame.serializer()),
            call("API_GetUserRecentlyPlayedGames.php", mapOf("u" to user, "c" to count.toString())),
        )

    suspend fun gameProgress(gameId: Int): RaGameProgress =
        parseGameProgress(call("API_GetGameInfoAndUserProgress.php", mapOf("u" to user, "g" to gameId.toString())))

    /**
     * RA's games with achievements for a console, with their ROM hashes. The list is cached on
     * disk for a week to keep API use low.
     */
    suspend fun gameIndex(consoleId: Int): RaGameIndex {
        indexes[consoleId]?.let { return it }
        val file = File(cacheDir, "games_h_$consoleId.json")
        val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < 7L * 24 * 3600 * 1000
        val cached = if (fresh) withContext(Dispatchers.IO) { runCatching { RaGameIndex.parse(file.readText()) }.getOrNull() } else null
        val index = cached ?: run {
            val body = call("API_GetGameList.php", mapOf("i" to consoleId.toString(), "f" to "1", "h" to "1"))
            RaGameIndex.parse(body).also { withContext(Dispatchers.IO) { runCatching { file.writeText(body) } } }
        }
        return index.also { indexes[consoleId] = it }
    }

    companion object {
        fun parseGameProgress(body: String): RaGameProgress {
            val obj = PillarJson.parseToJsonElement(body) as? JsonObject ?: throw RaException("Unexpected response")
            val raw = PillarJson.decodeFromJsonElement(RaGameRaw.serializer(), obj)
            // "Achievements" is a map keyed by id, and is an empty array when a game has none.
            val achievements = (obj["Achievements"] as? JsonObject)?.values
                ?.map { PillarJson.decodeFromJsonElement(RaAchievement.serializer(), it) }
                ?.sortedBy { it.displayOrder }
                .orEmpty()
            return RaGameProgress(raw.id, raw.title, raw.consoleName, raw.imageIcon, raw.imageBoxArt, raw.numDistinctPlayers, achievements)
        }

        fun normalize(title: String): String {
            var t = title.lowercase()
                .replace(Regex("\\s*[(\\[][^)\\]]*[)\\]]"), "")
                .replace("&", "and")
                .replace("é", "e")
            t = t.replace(Regex(",\\s*(the|a|an)\\b"), "")
            t = t.replace(Regex("^(the|a|an)\\s+"), "")
            return t.replace(Regex("[^a-z0-9]"), "")
        }
    }
}
