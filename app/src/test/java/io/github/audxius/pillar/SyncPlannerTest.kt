package io.github.audxius.pillar

import io.github.audxius.pillar.data.SyncPlanner
import io.github.audxius.pillar.data.SyncPlanner.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Each case mirrors a row of RetroArch's task_cloudsync.c decision table. */
class SyncPlannerTest {
    private val k = "saves/Game.srm"
    private fun one(s: String?, l: String?, c: String?): Action =
        SyncPlanner.plan(listOfNotNull(s?.let { k to it }).toMap(), listOfNotNull(l?.let { k to it }).toMap(), listOfNotNull(c?.let { k to it }).toMap()).single()

    @Test fun newOnServer() = assertEquals(Action.Download(k, "a"), one("a", null, null))
    @Test fun tombstoneOnly() = assertEquals(Action.Record(k, ""), one("", null, null))
    @Test fun firstSyncSameContent() = assertEquals(Action.Record(k, "a"), one("a", null, "a"))
    @Test fun firstSyncDifferent() = assertTrue(one("a", null, "b") is Action.Conflict)
    @Test fun newLocal() = assertEquals(Action.Upload(k), one(null, null, "a"))
    @Test fun inSync() = assertEquals(Action.Record(k, "a"), one("a", "a", "a"))
    @Test fun localChanged() = assertEquals(Action.Upload(k), one("a", "a", "b"))
    @Test fun serverChanged() = assertEquals(Action.Download(k, "b"), one("b", "a", "a"))
    @Test fun bothChanged() = assertTrue(one("b", "a", "c") is Action.Conflict)
    @Test fun bothChangedToSameContentIsNotAConflict() = assertEquals(Action.Record(k, "b"), one("b", "a", "b"))
    @Test fun deletedOnServer() = assertEquals(Action.DeleteLocal(k), one("", "a", "a"))
    @Test fun deletedOnServerButEditedHere() = assertTrue(one("", "a", "b") is Action.Conflict)
    @Test fun deletedLocally() = assertEquals(Action.DeleteRemote(k), one("a", "a", null))
    @Test fun deletedLocallyButChangedOnServer() = assertTrue(one("b", "a", null) is Action.Conflict)
    @Test fun cameBackOnServer() = assertEquals(Action.Download(k, "a"), one("a", "", null))
    @Test fun tombstoneAndMissing() = assertEquals(Action.Record(k, ""), one("", "a", null))
    @Test fun serverResetReuploads() = assertEquals(Action.Upload(k), one(null, "a", "a"))
    @Test fun goneEverywhereIsDropped() = assertEquals(Action.Drop(k), one(null, "a", null))

    @Test fun keysAreCaseInsensitive() {
        val plan = SyncPlanner.plan(mapOf("saves/GAME.srm" to "a"), mapOf("saves/game.srm" to "a"), mapOf("saves/Game.srm" to "a"))
        assertEquals(1, plan.size)
        assertTrue(plan.single() is Action.Record)
    }

    @Test fun manifestRoundTripMatchesRetroArchShape() {
        val text = SyncPlanner.encodeManifest(mapOf("states/b.state" to "", "saves/A.srm" to "d41d8cd98f00b204e9800998ecf8427e"))
        assertTrue(text.indexOf("saves/A.srm") < text.indexOf("states/b.state"))
        assertTrue(text.contains("\"path\": \"saves/A.srm\""))
        assertEquals(mapOf("saves/A.srm" to "d41d8cd98f00b204e9800998ecf8427e", "states/b.state" to ""), SyncPlanner.decodeManifest(text))
    }

    @Test fun parserTreatsNullHashAsDeleted() {
        val m = SyncPlanner.decodeManifest("""[{"path":"saves/x.srm","hash":null},{"path":"config/retroarch.cfg","hash":"abc"},{"hash":"orphan"}]""")
        assertEquals(mapOf("saves/x.srm" to "", "config/retroarch.cfg" to "abc"), m)
    }
}
