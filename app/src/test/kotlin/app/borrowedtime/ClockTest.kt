package app.borrowedtime

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ClockTest {
    private class Pins : PinStore {
        override var tzPin = Clock.NO_TZ
        override var tzCand = Clock.NO_TZ
        override var tzCandSince = 0L
    }

    private val hour = 3_600_000L
    private val pins = Pins()
    private var dev = 1_700_000_000_000L
    private var offset = 330

    @Before fun setUp() {
        Clock.pins = pins
        Clock.skewMs = 0
        Clock.deviceNow = { dev }
        Clock.deviceOffsetMin = { offset }
    }

    @After fun tearDown() {
        Clock.pins = null
        Clock.skewMs = 0
        Clock.deviceNow = { System.currentTimeMillis() }
        Clock.deviceOffsetMin = { java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000 }
    }

    @Test fun skewShiftsTheDay() {
        val real = dev
        val day = Clock.localDay()
        dev = real + 36 * hour        // phone clock pushed forward a day and a half
        Clock.skewMs = -36 * hour     // the next sync measures the difference
        assertEquals(day, Clock.localDay())
    }

    @Test fun pinnedZoneIgnoresQuickChange() {
        Clock.pinNow()
        val day = Clock.localDay()
        offset = -480                 // phone moved to another zone
        Clock.observeTz()
        dev += 10 * hour
        Clock.observeTz()
        assertEquals(330, Clock.tzOffsetMin())
        assertEquals(day, Clock.localDay(dev - 10 * hour))
    }

    @Test fun zoneIsFollowedAfterADay() {
        Clock.pinNow()
        offset = 60
        Clock.observeTz()
        dev += 25 * hour
        Clock.observeTz()
        assertEquals(60, Clock.tzOffsetMin())
    }

    @Test fun candidateResetsWhenPhoneGoesBack() {
        Clock.pinNow()
        offset = 60
        Clock.observeTz()
        dev += 20 * hour
        offset = 330
        Clock.observeTz()             // back to the pinned zone: candidate cleared
        offset = 60
        dev += 2 * hour
        Clock.observeTz()             // new candidate starts now
        dev += 23 * hour
        Clock.observeTz()
        assertEquals(330, Clock.tzOffsetMin())
        dev += 2 * hour
        Clock.observeTz()
        assertEquals(60, Clock.tzOffsetMin())
    }
}
