package io.github.audxius.pillar

import io.github.audxius.pillar.data.PillarJson
import io.github.audxius.pillar.data.RaClient
import io.github.audxius.pillar.data.RaProfile
import io.github.audxius.pillar.data.RaRecentAchievement
import io.github.audxius.pillar.data.RaRecentGame
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Samples follow the RetroAchievements API docs (github.com/RetroAchievements/api-docs). */
class RaParsingTest {
    @Test
    fun profile() {
        val p = PillarJson.decodeFromString(RaProfile.serializer(), """
            {"User":"MaxMilyin","ULID":"00003EMFWR7XB8SDPEHB3K56ZQ","UserPic":"/UserPic/MaxMilyin.png",
             "MemberSince":"2016-01-02 00:43:04","RichPresenceMsg":"Playing ~Hack~ 11th Annual Vanilla Level Design Contest, The",
             "LastGameID":19504,"ContribCount":0,"ContribYield":0,"TotalPoints":399597,"TotalSoftcorePoints":0,
             "TotalTruePoints":1599212,"Permissions":1,"Untracked":0,"ID":16446,"UserWallActive":1,"Motto":"Join me"}
        """)
        assertEquals("MaxMilyin", p.user)
        assertEquals(399597, p.totalPoints)
        assertEquals("/UserPic/MaxMilyin.png", p.userPic)
    }

    @Test
    fun recentAchievementsAndGames() {
        val a = PillarJson.decodeFromString(ListSerializer(RaRecentAchievement.serializer()), """
            [{"Date":"2023-12-27 16:04:50","HardcoreMode":1,"AchievementID":98012,"Title":"Beginner I",
              "Description":"Clear stages 01 - 05 in Quest.","BadgeName":"108302","Points":5,"TrueRatio":25,"Type":null,
              "Author":"jos","GameTitle":"Pokemon Pinball mini","GameIcon":"/Images/028399.png","GameID":14715,
              "ConsoleName":"Pokemon Mini","BadgeURL":"/Badge/108302.png","GameURL":"/game/14715"}]
        """).single()
        assertEquals("/Badge/108302.png", a.badgeUrl)
        assertEquals(1, a.hardcore)

        val g = PillarJson.decodeFromString(ListSerializer(RaRecentGame.serializer()), """
            [{"GameID":11332,"ConsoleID":12,"ConsoleName":"PlayStation","Title":"Final Fantasy Origins",
              "ImageIcon":"/Images/060249.png","ImageTitle":"/Images/026707.png","ImageIngame":"/Images/026708.png",
              "ImageBoxArt":"/Images/046257.png","LastPlayed":"2023-10-27 00:30:04","AchievementsTotal":119,
              "NumPossibleAchievements":119,"PossibleScore":945,"NumAchieved":38,"ScoreAchieved":382,
              "NumAchievedHardcore":38,"ScoreAchievedHardcore":382}]
        """).single()
        assertEquals(119, g.total)
        assertEquals(38f / 119f, g.progress, 0.0001f)
    }

    @Test
    fun gameProgressWithAchievementMap() {
        val p = RaClient.parseGameProgress("""
            {"ID":1,"Title":"Sonic the Hedgehog","ConsoleID":1,"ImageIcon":"/Images/067895.png","ImageBoxArt":"/Images/051872.png",
             "ConsoleName":"Mega Drive","NumDistinctPlayers":27080,"NumAchievements":2,
             "Achievements":{
               "9":{"ID":9,"NumAwarded":24273,"Title":"That Was Easy","Description":"Complete the first act","Points":3,
                    "BadgeName":"250336","DisplayOrder":1,"type":"progression",
                    "DateEarnedHardcore":"2016-03-12 17:47:29","DateEarned":"2016-03-12 17:47:29"},
               "10":{"ID":10,"Title":"Later","Description":"x","Points":10,"BadgeName":"250337","DisplayOrder":2}
             },
             "NumAwardedToUser":1,"UserCompletion":"50.00%"}
        """)
        assertEquals("Sonic the Hedgehog", p.title)
        assertEquals(2, p.achievements.size)
        assertEquals(1, p.earned)
        assertEquals(3, p.points)
        assertEquals(13, p.totalPoints)
        assertTrue(p.achievements.first().earned)
        assertFalse(p.achievements.last().earned)
        assertEquals("https://media.retroachievements.org/Badge/250337_lock.png", p.achievements.last().lockedBadgeUrl)
    }

    @Test
    fun gameWithoutAchievementsUsesEmptyArray() {
        val p = RaClient.parseGameProgress("""{"ID":5,"Title":"No Cheevos","ConsoleName":"SNES","Achievements":[]}""")
        assertTrue(p.achievements.isEmpty())
        assertEquals(0f, p.progress)
    }
}
