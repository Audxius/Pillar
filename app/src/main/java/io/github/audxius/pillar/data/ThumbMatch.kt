package io.github.audxius.pillar.data

/**
 * Matches a ROM file name against the names available on the libretro thumbnail server, so covers
 * load even when files aren't named exactly like the No-Intro / Redump databases.
 */
object ThumbMatch {
    internal val stopWords = setOf("the", "a", "an", "of", "and", "version", "edition", "game")
    private val badTags = listOf("beta", "proto", "demo", "sample", "kiosk", "pirate", "unl", "hack", "aftermarket", "program")
    private val regionOrder = listOf("usa", "world", "europe", "en", "uk", "australia", "canada", "japan")

    private val roman = mapOf(
        "i" to "1", "ii" to "2", "iii" to "3", "iv" to "4", "v" to "5", "vi" to "6", "vii" to "7",
        "viii" to "8", "ix" to "9", "x" to "10", "xi" to "11", "xii" to "12", "xiii" to "13",
    )
    private val tagRe = Regex("\\s*[(\\[][^)\\]]*[)\\]]")
    private val tagContentRe = Regex("[(\\[]([^)\\]]+)[)\\]]")
    private val articleRe = Regex(",\\s*(the|a|an)\\b")
    private val splitRe = Regex("[^a-z0-9]+")

    /** Removes a real file extension only, so "Super Mario Bros. 3" keeps its dot. */
    fun withoutExtension(name: String): String {
        val ext = name.substringAfterLast('.', "")
        return if (ext.length in 1..5 && ext.all { it.isLetterOrDigit() }) name.substringBeforeLast('.') else name
    }

    fun stripTags(name: String): String = withoutExtension(name).replace(tagRe, "").trim()

    internal fun tags(name: String): List<String> =
        tagContentRe.findAll(name).flatMap { m -> m.groupValues[1].split(',').map { it.trim().lowercase() } }.toList()

    /** Title words with articles moved, punctuation dropped and roman numerals turned into digits. */
    fun tokens(name: String): List<String> {
        var t = stripTags(name).lowercase().replace('_', ' ').replace("&", " and ")
        t = t.replace(articleRe, " ")
        return t.split(splitRe).filter { it.isNotEmpty() }.map { roman[it] ?: it }
    }

    fun key(name: String): String = keyOf(tokens(name))
    internal fun keyOf(tokens: List<String>) = tokens.filter { it !in setOf("the", "a", "an") }.joinToString("")
    internal fun significant(tokens: List<String>) = tokens.filter { it !in stopWords }.toSet()

    /** One-off match; for many lookups against the same list build a [ThumbIndex] once. */
    fun best(fileName: String, candidates: Collection<String>): String? = ThumbIndex(candidates).best(fileName)

    internal fun tagScore(candidate: List<String>, wanted: List<String>): Double {
        var s = 0.0
        if (candidate.any { t -> badTags.any { t.startsWith(it) } } && wanted.none { t -> badTags.any { t.startsWith(it) } }) s -= 60
        s += candidate.count { it in wanted } * 8.0
        val region = candidate.firstNotNullOfOrNull { t -> regionOrder.indexOf(t).takeIf { it >= 0 } }
        s += if (region != null) (regionOrder.size - region).toDouble() else 0.0
        if (candidate.any { it.startsWith("rev") }) s += 0.5
        // Plain entries without extra tags are usually the main release.
        s -= candidate.size * 0.3
        return s
    }
}

/**
 * A thumbnail name list parsed once, so matching a whole library stays fast even for systems
 * with tens of thousands of entries.
 */
class ThumbIndex(names: Collection<String>) {
    private class Entry(val name: String, val key: String, val tokens: Set<String>, val numbers: Set<String>, val tags: List<String>)

    private val byKey = HashMap<String, MutableList<Entry>>()
    private val byToken = HashMap<String, MutableList<Entry>>()

    init {
        for (n in names) {
            val t = ThumbMatch.tokens(n)
            val sig = ThumbMatch.significant(t)
            val e = Entry(n, ThumbMatch.keyOf(t), sig, sig.filter { s -> s.all(Char::isDigit) }.toSet(), ThumbMatch.tags(n))
            if (e.key.isEmpty()) continue
            byKey.getOrPut(e.key) { ArrayList() } += e
            for (tok in sig) byToken.getOrPut(tok) { ArrayList() } += e
        }
    }

    fun best(fileName: String): String? {
        val wantTokensList = ThumbMatch.tokens(fileName)
        val wantKey = ThumbMatch.keyOf(wantTokensList)
        if (wantKey.isEmpty()) return null
        val wantTags = ThumbMatch.tags(fileName)

        byKey[wantKey]?.let { exact ->
            return exact.maxByOrNull { ThumbMatch.tagScore(it.tags, wantTags) }?.name
        }

        val want = ThumbMatch.significant(wantTokensList)
        if (want.isEmpty()) return null
        val wantNumbers = want.filter { it.all(Char::isDigit) }.toSet()
        // Only names sharing at least one meaningful word can be close enough to matter.
        val pool = want.flatMap { byToken[it].orEmpty() }.distinct()

        var bestName: String? = null
        var bestScore = 0.0
        for (c in pool) {
            // Sequels must not match each other: every number has to agree.
            if (c.numbers != wantNumbers) continue
            val inter = c.tokens.intersect(want).size.toDouble()
            val jaccard = inter / c.tokens.union(want).size
            val containment = inter / minOf(c.tokens.size, want.size)
            if (jaccard < 0.5 && !(containment == 1.0 && jaccard >= 0.34)) continue
            val score = 500.0 * jaccard + 100.0 * containment + ThumbMatch.tagScore(c.tags, wantTags)
            if (score > bestScore) {
                bestScore = score
                bestName = c.name
            }
        }
        return bestName
    }
}
