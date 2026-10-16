package io.github.audxius.pillar.data

import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import kotlinx.serialization.json.put
import kotlinx.serialization.json.buildJsonObject
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

@Serializable
data class RommPlatform(
    val id: Int,
    val slug: String = "",
    @SerialName("fs_slug") val fsSlug: String = "",
    val name: String = "",
    @SerialName("custom_name") val customName: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("rom_count") val romCount: Int = 0,
    @SerialName("url_logo") val urlLogo: String? = null,
) {
    val title: String get() = customName?.takeIf { it.isNotBlank() } ?: displayName?.takeIf { it.isNotBlank() } ?: name
}

@Serializable
data class RommMetadatum(
    val genres: List<String> = emptyList(),
    val companies: List<String> = emptyList(),
    val franchises: List<String> = emptyList(),
    @SerialName("first_release_date") val firstReleaseDate: Long? = null,
    @SerialName("average_rating") val averageRating: Double? = null,
    @SerialName("player_count") val playerCount: String? = null,
)

@Serializable
data class RommRom(
    val id: Int,
    val name: String? = null,
    @SerialName("fs_name") val fsName: String = "",
    @SerialName("fs_name_no_ext") val fsNameNoExt: String? = null,
    @SerialName("fs_extension") val fsExtension: String? = null,
    @SerialName("fs_size_bytes") val fsSizeBytes: Long = 0,
    @SerialName("platform_id") val platformId: Int = 0,
    @SerialName("platform_slug") val platformSlug: String? = null,
    @SerialName("platform_fs_slug") val platformFsSlug: String? = null,
    @SerialName("platform_display_name") val platformDisplayName: String? = null,
    val summary: String? = null,
    @SerialName("path_cover_large") val pathCoverLarge: String? = null,
    @SerialName("path_cover_small") val pathCoverSmall: String? = null,
    // Older RomM releases used these names.
    @SerialName("path_cover_l") val pathCoverL: String? = null,
    @SerialName("path_cover_s") val pathCoverS: String? = null,
    @SerialName("url_cover") val urlCover: String? = null,
    @SerialName("merged_screenshots") val screenshots: List<String> = emptyList(),
    @SerialName("ra_id") val raId: Int? = null,
    @SerialName("has_multiple_files") val hasMultipleFiles: Boolean? = null,
    val multi: Boolean? = null,
    val metadatum: RommMetadatum? = null,
    val regions: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    @SerialName("created_at") val createdAt: String? = null,
) {
    val title: String get() = name?.takeIf { it.isNotBlank() } ?: fsNameNoExt ?: fsName
    val isMultiFile: Boolean get() = hasMultipleFiles ?: multi ?: false
    val releaseYear: Int?
        get() = metadatum?.firstReleaseDate?.let { v ->
            val millis = if (v > 100_000_000_000L) v else v * 1000
            java.util.Calendar.getInstance().apply { timeInMillis = millis }.get(java.util.Calendar.YEAR)
        }
}

data class RommPage(val items: List<RommRom>, val total: Int)

class RommException(message: String) : IOException(message)

class RommClient(private val http: OkHttpClient, private val settings: () -> AppSettings) {

    private fun base(): HttpUrl {
        val raw = settings().rommUrl.trim().trimEnd('/')
        val withScheme = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "http://$raw"
        return withScheme.toHttpUrlOrNull() ?: throw RommException("The RomM address isn't a valid URL")
    }

    fun authHeader(s: AppSettings = settings()): String? = when {
        s.rommToken.isNotBlank() -> "Bearer ${s.rommToken.trim()}"
        s.rommUser.isNotBlank() -> Credentials.basic(s.rommUser.trim(), s.rommPassword)
        else -> null
    }

    fun request(url: HttpUrl): Request = Request.Builder().url(url).apply {
        authHeader()?.let { header("Authorization", it) }
        header("Accept", "application/json")
    }.build()

    private suspend fun get(build: HttpUrl.Builder.() -> Unit): JsonElement = withContext(Dispatchers.IO) {
        val url = base().newBuilder().apply(build).build()
        http.newCall(request(url)).execute().use { resp ->
            when (resp.code) {
                401, 403 -> throw RommException("RomM rejected the login (HTTP ${resp.code})")
                404 -> throw RommException("Not found on the RomM server (HTTP 404)")
            }
            if (!resp.isSuccessful) throw RommException("RomM returned HTTP ${resp.code}")
            val body = resp.body?.string() ?: throw RommException("Empty response from RomM")
            runCatching { PillarJson.parseToJsonElement(body) }
                .getOrElse { throw RommException("RomM sent a response Pillar couldn't read") }
        }
    }

    /** Returns the server version on success. */
    suspend fun test(): String {
        val version = runCatching {
            val hb = get { addPathSegments("api/heartbeat") }
            findVersion(hb)
        }.getOrNull()
        platforms() // verifies the credentials
        return version ?: "connected"
    }

    private fun findVersion(e: JsonElement): String? = when (e) {
        is JsonObject -> e["VERSION"]?.let { (it as? JsonPrimitive)?.contentOrNull }
            ?: e.values.firstNotNullOfOrNull { findVersion(it) }
        else -> null
    }

    suspend fun platforms(): List<RommPlatform> {
        val json = get { addPathSegments("api/platforms") }
        return PillarJson.decodeFromJsonElement(ListSerializer(RommPlatform.serializer()), json)
            .filter { it.romCount > 0 }
            .sortedBy { it.title.lowercase() }
    }

    suspend fun roms(
        platformId: Int? = null,
        search: String? = null,
        offset: Int = 0,
        limit: Int = 60,
        orderBy: String = "name",
        orderDir: String = "asc",
    ): RommPage {
        val json = get {
            addPathSegments("api/roms")
            platformId?.let {
                addQueryParameter("platform_ids", it.toString())
                addQueryParameter("platform_id", it.toString()) // older RomM releases
            }
            search?.takeIf { it.isNotBlank() }?.let { addQueryParameter("search_term", it) }
            addQueryParameter("offset", offset.toString())
            addQueryParameter("limit", limit.toString())
            addQueryParameter("order_by", orderBy)
            addQueryParameter("order_dir", orderDir)
            addQueryParameter("with_char_index", "false")
            addQueryParameter("with_filter_values", "false")
            addQueryParameter("with_rom_id_index", "false")
        }
        // Current RomM returns a page object; very old versions return a bare list.
        return when (json) {
            is JsonArray -> {
                val all = PillarJson.decodeFromJsonElement(ListSerializer(RommRom.serializer()), json)
                RommPage(all, all.size)
            }
            else -> {
                val obj = json.jsonObject
                val items = PillarJson.decodeFromJsonElement(
                    ListSerializer(RommRom.serializer()), obj["items"] ?: JsonArray(emptyList())
                )
                val total = (obj["total"] as? JsonPrimitive)?.intOrNull ?: (offset + items.size)
                RommPage(items, total)
            }
        }
    }

    suspend fun rom(id: Int): RommRom {
        val json = get { addPathSegments("api/roms/$id") }
        return PillarJson.decodeFromJsonElement(RommRom.serializer(), json)
    }

    /** Absolute URL for a RomM resource path, which may or may not include the /assets prefix. */
    fun resourceUrl(path: String?): String? {
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val b = runCatching { base() }.getOrNull() ?: return null
        val full = if (path.startsWith("/")) path else "/assets/romm/resources/$path"
        return b.toString().trimEnd('/') + full
    }

    fun coverUrl(rom: RommRom): String? =
        resourceUrl(rom.pathCoverLarge ?: rom.pathCoverL)
            ?: rom.urlCover?.takeIf { it.isNotBlank() }?.let { if (it.startsWith("//")) "https:$it" else it }

    fun downloadRequest(rom: RommRom): Request {
        val url = base().newBuilder()
            .addPathSegments("api/roms/${rom.id}/content")
            .addPathSegment(rom.fsName)
            .build()
        return Request.Builder().url(url).apply { authHeader()?.let { header("Authorization", it) } }.build()
    }

    fun host(): String? = runCatching { base().host }.getOrNull()

    /** Every save RomM keeps for a game, across slots and versions. */
    suspend fun saves(romId: Int): List<RommSave> =
        PillarJson.decodeFromJsonElement(ListSerializer(RommSave.serializer()), get { addPathSegments("api/saves"); addQueryParameter("rom_id", romId.toString()) })

    /** Downloads one save's file. */
    suspend fun downloadSave(save: RommSave, into: java.io.File) = withContext(Dispatchers.IO) {
        val url = base().newBuilder().addPathSegments("api/saves/${save.id}/content").build()
        http.newCall(Request.Builder().url(url).apply { authHeader()?.let { header("Authorization", it) } }.build()).execute().use { r ->
            if (!r.isSuccessful) throw RommException("Couldn't download that save (HTTP ${r.code})")
            into.outputStream().use { out -> r.body?.byteStream()?.copyTo(out) }
        }
    }

    /** Deletes games from RomM, and their files on the server so a rescan doesn't bring them back. */
    suspend fun deleteRoms(ids: List<Int>, fromDisk: Boolean) = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("roms", JsonArray(ids.map { JsonPrimitive(it) }))
            put("delete_from_fs", JsonArray((if (fromDisk) ids else emptyList()).map { JsonPrimitive(it) }))
        }.toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url(base().newBuilder().addPathSegments("api/roms/delete").build()).post(body)
            .apply { authHeader()?.let { header("Authorization", it) } }.build()
        http.newCall(req).execute().use { r ->
            if (r.code == 401 || r.code == 403) throw RommException("RomM didn't allow deleting (HTTP ${r.code}); the account needs edit rights")
            if (!r.isSuccessful) throw RommException("RomM couldn't delete it (HTTP ${r.code})")
        }
    }

    /**
     * Uploads a game file to a RomM platform with RomM's chunked upload (RomM 5.x), the same way
     * its web UI does: start, send 10 MB chunks, complete.
     */
    suspend fun upload(platformId: Int, fileName: String, size: Long, open: () -> java.io.InputStream, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val chunk = 10L * 1024 * 1024
        val total = ((size + chunk - 1) / chunk).toInt()
        fun call(b: Request.Builder, what: String): String {
            authHeader()?.let { b.header("Authorization", it) }
            http.newCall(b.build()).execute().use { r ->
                if (r.code == 404 || r.code == 405) throw RommException("This RomM server doesn't support uploads from apps (needs RomM 5)")
                if (r.code == 409) throw RommException("RomM already has a file with that name")
                if (r.code == 401 || r.code == 403) throw RommException("RomM didn't allow the upload (HTTP ${r.code}); the account needs upload rights")
                if (!r.isSuccessful) throw RommException("$what failed (HTTP ${r.code})")
                return r.body?.string().orEmpty()
            }
        }
        val uploadUrl = base().newBuilder().addPathSegments("api/roms/upload").build()
        val started = call(
            Request.Builder().url(uploadUrl.newBuilder().addPathSegment("start").build())
                .header("X-Upload-Platform", platformId.toString())
                .header("X-Upload-Filename", Uri.encode(fileName))
                .header("X-Upload-Total-Size", size.toString())
                .header("X-Upload-Total-Chunks", total.toString())
                // Like RomM's web app: the header carries the percent-encoded name (headers are
                // Latin-1 only) and the body the real one, which RomM prefers.
                .post(buildJsonObject { put("filename", fileName) }.toString().toRequestBody("application/json".toMediaType())),
            "Starting the upload",
        )
        val id = (PillarJson.parseToJsonElement(started) as? JsonObject)?.get("upload_id")?.let { (it as? JsonPrimitive)?.content }
            ?: throw RommException("RomM didn't start the upload")
        try {
            open().use { input ->
                val buf = ByteArray(chunk.toInt())
                for (i in 0 until total) {
                    var n = 0
                    while (n < buf.size) { val r = input.read(buf, n, buf.size - n); if (r < 0) break; n += r }
                    call(
                        Request.Builder().url(uploadUrl.newBuilder().addPathSegment(id).build())
                            .header("X-Chunk-Index", i.toString())
                            .put(buf.toRequestBody("application/octet-stream".toMediaType(), 0, n)),
                        "Uploading",
                    )
                    onProgress((i + 1f) / total)
                }
            }
            call(Request.Builder().url(uploadUrl.newBuilder().addPathSegment(id).addPathSegment("complete").build()).post(ByteArray(0).toRequestBody(null)), "Finishing the upload")
        } catch (e: Exception) {
            runCatching { call(Request.Builder().url(uploadUrl.newBuilder().addPathSegment(id).addPathSegment("cancel").build()).post(ByteArray(0).toRequestBody(null)), "Cancelling") }
            throw e
        }
    }
}

/** A save on RomM. Saves live in named slots; "autosave" is the one RetroArch sync uses. */
@Serializable
data class RommSave(
    val id: Int,
    @SerialName("rom_id") val romId: Int = 0,
    @SerialName("file_name") val fileName: String = "",
    @SerialName("file_extension") val extension: String = "",
    @SerialName("file_size_bytes") val size: Long = 0,
    val emulator: String? = null,
    val slot: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val labels: List<String> = emptyList(),
) {
    val updatedMillis: Long get() = updatedAt?.let { runCatching { java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() } ?: 0L
}
