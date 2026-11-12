package app.borrowedtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {
    private fun member(used: Int, limit: Int = 30, granted: Int = 0, seen: Long = 1000) =
        Member("m", "Ravi", limit, used, granted, true, 2, seen, 330)

    @Test fun allowanceIncludesGrantedMinutes() {
        val m = member(used = 29 * 60, limit = 30, granted = 10)
        assertEquals(40 * 60, m.allowanceSec)
        assertEquals(11 * 60, m.leftSec)
    }

    @Test fun leftNeverGoesNegative() {
        assertEquals(0, member(used = 99 * 60).leftSec)
    }

    @Test fun silentAfterTenMinutes() {
        assertFalse(member(0, seen = 1000).silent(1000 + 600))
        assertTrue(member(0, seen = 1000).silent(1000 + 601))
        assertFalse(member(0, seen = 0).silent(99999))
    }

    @Test fun minutesLabels() {
        assertEquals("45 min", minutesLabel(45))
        assertEquals("2 h", minutesLabel(120))
        assertEquals("1 h 30 min", minutesLabel(90))
    }

    @Test fun leftLabels() {
        assertEquals("1", leftLabel(1))
        assertEquals("1", leftLabel(60))
        assertEquals("2", leftLabel(61))
        assertEquals("1:05", leftLabel(65 * 60))
    }

    @Test fun parsesSnapshot() {
        val raw = """{"now":1700000000,"group":{"name":"G","code":"ABC234"},
          "me":{"id":"a","name":"Aditya","requests_today":1,"granted_today":10},
          "members":[{"id":"a","name":"Aditya","limit":45,"used":600,"granted":10,"guard_ok":true,"apps":2,"seen":null,"tz":330}],
          "requests":[{"id":"r","requester_id":"b","requester_name":"Ravi","minutes":10,"note":null,"status":"pending","granted":0,"created":1699999000,"decided":null,"decided_by":null,"my_vote":null}]}"""
        val s = Snapshot.parse(raw)
        assertEquals(2, s.asksLeft)
        assertEquals("ABC234", s.code)
        assertEquals(1, s.toAnswer.size)
        assertEquals(0L, s.members[0].seen)
        assertEquals(55 * 60, s.me!!.allowanceSec)
    }
}
