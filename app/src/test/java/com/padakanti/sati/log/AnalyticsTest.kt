package com.padakanti.sati.log

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsTest {
    // A practice that is due every day from day 0, unless a test says otherwise.
    private fun prac(
        id: String = "p",
        created: Long = 0,
        every: Int = 1,
        start: Long = 0,
        archived: Boolean = false,
        position: Int = 0,
    ) = Practice(id, id, Measure.Done, null, created, archived, position, askFocus = false, everyDays = every, scheduleStart = start)

    private fun e(practiceId: String, day: Long, value: Int = 1, focus: Int? = null) =
        Entry("$practiceId-$day-$value", practiceId, day, value, focus, null, 0)

    private fun doneOn(id: String, days: Iterable<Long>) = days.map { e(id, it) }

    // The first entry day is read from the entries, as the database would give it.
    private fun track(
        p: Practice,
        entries: List<Entry> = emptyList(),
        periods: List<ArchivePeriod> = emptyList(),
        first: Long? = entries.minOfOrNull { it.day },
    ) = Track(p, first, periods, entries)

    private fun period(id: String, from: Long, to: Long?) = ArchivePeriod(practiceId = id, fromDay = from, toDay = to)

    private fun epoch(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()

    // ---- Track.start and active ----

    @Test fun startIsTheEarlierOfCreationAndFirstEntry() {
        val t = track(prac(created = 10), first = 5)
        assertEquals(5, t.start)
        assertFalse(t.active(4))
        assertTrue(t.active(5))
        assertEquals(DayMark.Inactive, t.mark(4, 50))
    }

    @Test fun startIsTheCreationDayWhenTheFirstEntryIsLater() {
        assertEquals(10, track(prac(created = 10), first = 20).start)
        assertEquals(10, track(prac(created = 10), first = null).start)
    }

    @Test fun anArchivePeriodCoversItsFirstDayAndNotTheReturnDay() {
        val t = track(prac(), periods = listOf(period("p", 20, 30)))
        assertTrue(t.active(19))
        assertFalse(t.active(20))
        assertFalse(t.active(29))
        assertTrue(t.active(30))
        assertTrue(t.active(31))
    }

    @Test fun anOpenPeriodMakesEveryLaterDayInactive() {
        val t = track(prac(archived = true), periods = listOf(period("p", 20, null)))
        assertTrue(t.active(19))
        assertFalse(t.active(20))
        assertFalse(t.active(5000))
    }

    @Test fun periodsOfOtherPracticesAreIgnored() {
        val t = track(prac(), periods = listOf(period("other", 0, null)))
        assertTrue(t.active(10))
    }

    // ---- mark ----

    @Test fun markDoneOnANotDueDayIsDone() {
        val p = prac(every = 3)
        val t = track(p, listOf(e("p", 8)))
        assertEquals(DayMark.Done, t.mark(8, 20))
    }

    @Test fun markNotDueWhenNothingIsDone() {
        assertEquals(DayMark.NotDue, track(prac(every = 3)).mark(8, 20))
    }

    @Test fun markMissedOnlyForPastDueDays() {
        val t = track(prac(every = 3))
        assertEquals(DayMark.Missed, t.mark(6, 20))
        assertEquals(DayMark.Done, track(prac(every = 3), listOf(e("p", 6))).mark(6, 20))
    }

    @Test fun aNotDoneEntryOnAPastDueDayIsMissed() {
        val t = track(prac(), listOf(e("p", 5, value = 0)), first = 0)
        assertEquals(DayMark.Missed, t.mark(5, 10))
    }

    @Test fun todayDueAndNotDoneIsOpenNeverMissed() {
        val t = track(prac(), first = 0)
        assertEquals(DayMark.Open, t.mark(10, 10))
        assertEquals(DayMark.Open, track(prac(), listOf(e("p", 10, value = 0)), first = 0).mark(10, 10))
    }

    @Test fun todayDoneIsDone() {
        assertEquals(DayMark.Done, track(prac(), listOf(e("p", 10)), first = 0).mark(10, 10))
    }

    @Test fun todayNotDueAndNotDoneIsNotDue() {
        assertEquals(DayMark.NotDue, track(prac(every = 3)).mark(10, 10))
    }

    @Test fun futureDaysAreInactiveEvenWithAnEntry() {
        val t = track(prac(), listOf(e("p", 11)), first = 0)
        assertEquals(DayMark.Inactive, t.mark(11, 10))
        assertEquals(DayMark.Inactive, t.mark(500, 10))
    }

    @Test fun anEntryOnAnArchivedDayStaysInactive() {
        val t = track(prac(), listOf(e("p", 25)), periods = listOf(period("p", 20, 30)), first = 0)
        assertEquals(DayMark.Inactive, t.mark(25, 100))
    }

    @Test fun weekMarksListSevenDaysEndingWithTodayOldestFirst() {
        val t = track(prac(), listOf(e("p", 94)), first = 0)
        val marks = Analytics.weekMarks(t, 100)
        assertEquals(7, marks.size)
        assertEquals(DayMark.Done, marks[0])
        assertEquals(DayMark.Missed, marks[1])
        assertEquals(DayMark.Missed, marks[5])
    }

    @Test fun theLastWeekMarkIsToday() {
        assertEquals(DayMark.Open, Analytics.weekMarks(track(prac(), first = 0), 100).last())
        assertEquals(DayMark.Done, Analytics.weekMarks(track(prac(), listOf(e("p", 100)), first = 0), 100).last())
        assertEquals(DayMark.NotDue, Analytics.weekMarks(track(prac(every = 3), first = 0), 100).last())
        assertEquals(94, Analytics.weekFrom(100))
    }

    // ---- tally ----

    @Test fun tallyIgnoresExtraDoneDays() {
        // Due on 0, 3, 6, 9. Day 1 is an extra entry.
        val t = track(prac(every = 3), listOf(e("p", 1), e("p", 3)), first = 0)
        assertEquals(Tally(1, 3), t.tally(0, 20, 9))
    }

    @Test fun tallyCountsTodayOnlyWhenDone() {
        val notDone = track(prac(every = 3), listOf(e("p", 3)), first = 0)
        assertEquals(Tally(1, 3), notDone.tally(0, 9, 9))
        val done = track(prac(every = 3), listOf(e("p", 3), e("p", 9)), first = 0)
        assertEquals(Tally(2, 4), done.tally(0, 9, 9))
    }

    @Test fun tallyIgnoresDaysAfterToday() {
        val t = track(prac(every = 3), listOf(e("p", 3), e("p", 12)), first = 0)
        assertEquals(Tally(1, 3), t.tally(0, 30, 9))
    }

    @Test fun tallyOfANotDoneEntryIsMissed() {
        val t = track(prac(), listOf(e("p", 1, value = 0), e("p", 2)), first = 0)
        assertEquals(Tally(1, 2), t.tally(1, 2, 10))
    }

    @Test fun tallyOnlyCountsActiveDays() {
        val t = track(prac(), periods = listOf(period("p", 3, 6)), first = 0)
        // Days 0, 1, 2 and 6, 7, 8, 9 are active. All are missed.
        assertEquals(Tally(0, 7), t.tally(0, 9, 10))
    }

    @Test fun tallyShare() {
        assertNull(Tally.ZERO.share)
        assertEquals(0.5, Tally(1, 2).share!!, 1e-12)
        assertEquals(Tally(3, 5), Tally(1, 2) + Tally(2, 3))
    }

    // ---- week ----

    @Test fun weekCountsAllDoneDaysAndNamesTheMostMissedPractice() {
        val a = prac("a", position = 5)
        val b = prac("b", position = 1)
        val entries = doneOn("a", 96L..99) + doneOn("b", 96L..99)
        val w = Analytics.week(listOf(track(a, entries, first = 0), track(b, entries, first = 0)), 100)
        // Days 94 to 99 count. Today is not done, so it is skipped.
        assertEquals(4, w.allDone)
        assertEquals(6, w.days)
        // Both missed 2 of 6. The tie goes to the lower position, which is b.
        assertEquals(MostMissed(b, 2, 6), w.mostMissed)
    }

    @Test fun weekCountsTodayWhenEveryDuePracticeIsDoneToday() {
        val p = prac()
        val w = Analytics.week(listOf(track(p, doneOn("p", 94L..100), first = 0)), 100)
        assertEquals(WeekSummary(7, 7, null), w)
    }

    @Test fun weekSkipsTodayWhenNotEveryDuePracticeIsDone() {
        val a = prac("a")
        val b = prac("b")
        val entries = doneOn("a", 94L..100) + doneOn("b", 94L..99)
        val w = Analytics.week(listOf(track(a, entries, first = 0), track(b, entries, first = 0)), 100)
        assertEquals(6, w.allDone)
        assertEquals(6, w.days)
        val none = Analytics.week(listOf(track(a, doneOn("a", 94L..99), first = 0)), 100)
        assertEquals(WeekSummary(6, 6, null), none)
    }

    @Test fun todayNeverAddsAMissToMostMissed() {
        // Misses on 98 and 99 are two. Today is not done and adds none.
        val p = prac()
        val w = Analytics.week(listOf(track(p, doneOn("p", 94L..97), first = 0)), 100)
        assertEquals(MostMissed(p, 2, 6), w.mostMissed)
        // Only one past miss, plus an open today: not named.
        val one = Analytics.week(listOf(track(p, doneOn("p", 94L..98), first = 0)), 100)
        assertNull(one.mostMissed)
    }

    @Test fun weekAllDoneNeedsEveryDuePracticeDone() {
        val a = prac("a")
        val b = prac("b")
        val entries = doneOn("a", 94L..100)
        val w = Analytics.week(listOf(track(a, entries), track(b, emptyList(), first = 0)), 100)
        assertEquals(0, w.allDone)
        assertEquals(6, w.days)
    }

    @Test fun weekDoesNotCountDaysWithNothingDue() {
        val p = prac(every = 3)
        val w = Analytics.week(listOf(track(p, emptyList(), first = 0)), 100)
        // Due on 96 and 99 in the window 94 to 100.
        assertEquals(2, w.days)
        assertEquals(0, w.allDone)
        assertEquals(MostMissed(p, 2, 2), w.mostMissed)
    }

    @Test fun weekWithNoActiveDaysIsEmpty() {
        val w = Analytics.week(listOf(track(prac(created = 100), first = null)), 100)
        assertEquals(WeekSummary(0, 0, null), w)
    }

    @Test fun weekCountsArchivedNowPracticesButNeverNamesThem() {
        val a = prac("a", position = 1)
        val c = prac("c", archived = true, position = 0)
        val tracks = listOf(
            track(a, doneOn("a", 94L..100), first = 0),
            track(c, emptyList(), periods = listOf(period("c", 98, null)), first = 0),
        )
        // c was active and missed on 94..97, so those days are not all done.
        assertEquals(WeekSummary(3, 7, null), Analytics.week(tracks, 100))
    }

    @Test fun weekDoesNotNameAPracticeWithOnlyOneMiss() {
        val w = Analytics.week(listOf(track(prac(), doneOn("p", 94L..98) + doneOn("p", 100L..100), first = 0)), 100)
        assertNull(w.mostMissed)
        assertEquals(6, w.allDone)
        assertEquals(7, w.days)
    }

    @Test fun weekNamesAPracticeWithExactlyTwoMisses() {
        val p = prac()
        val w = Analytics.week(listOf(track(p, doneOn("p", 94L..97) + doneOn("p", 100L..100), first = 0)), 100)
        assertEquals(MostMissed(p, 2, 7), w.mostMissed)
    }

    @Test fun weekNamesThePracticeWithTheMostMisses() {
        val a = prac("a", position = 0)
        val b = prac("b", position = 1)
        val tracks = listOf(
            track(a, doneOn("a", 94L..97), first = 0),
            track(b, doneOn("b", 94L..95), first = 0),
        )
        assertEquals(MostMissed(b, 4, 6), Analytics.week(tracks, 100).mostMissed)
    }

    // ---- week starts ----

    // 9 November 2026 is a Monday.
    private val monday = epoch(2026, 11, 9)

    @Test fun weekStartWithMondayFirst() {
        assertEquals(monday, Analytics.weekStart(monday, DayOfWeek.MONDAY))
        assertEquals(monday, Analytics.weekStart(monday + 2, DayOfWeek.MONDAY))
        assertEquals(monday, Analytics.weekStart(monday + 6, DayOfWeek.MONDAY))
        assertEquals(monday + 7, Analytics.weekStart(monday + 7, DayOfWeek.MONDAY))
    }

    @Test fun weekStartWithSundayFirst() {
        assertEquals(monday - 1, Analytics.weekStart(monday, DayOfWeek.SUNDAY))
        assertEquals(monday - 1, Analytics.weekStart(monday - 1, DayOfWeek.SUNDAY))
        assertEquals(monday - 1, Analytics.weekStart(monday + 5, DayOfWeek.SUNDAY))
        assertEquals(monday + 6, Analytics.weekStart(monday + 6, DayOfWeek.SUNDAY))
    }

    @Test fun weekStartsGiveTwelveWeeksOldestFirstWithTodayInTheLast() {
        for (first in listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY)) {
            val today = monday + 3
            val starts = Analytics.weekStarts(today, first, 12)
            assertEquals(12, starts.size)
            assertEquals(Analytics.weekStart(today, first), starts.last())
            assertTrue(starts.last() <= today && today < starts.last() + 7)
            assertEquals(starts.sorted(), starts)
            assertTrue(starts.zipWithNext().all { (a, b) -> b - a == 7L })
            assertEquals(first, LocalDate.ofEpochDay(starts.first()).dayOfWeek)
        }
    }

    @Test fun weekTalliesCoverSevenDaysEachAndStopAtToday() {
        val t = track(prac(), doneOn("p", 0L..20), first = 0)
        val tallies = Analytics.weekTallies(t, listOf(0L, 7L, 14L), 16)
        // The last week has days 14, 15 and today 16, all done.
        assertEquals(listOf(Tally(7, 7), Tally(7, 7), Tally(3, 3)), tallies)
    }

    // ---- level ----

    @Test fun levelBoundaries() {
        assertNull(Analytics.level(Tally(0, 0)))
        assertEquals(0, Analytics.level(Tally(0, 3)))
        assertEquals(1, Analytics.level(Tally(1, 3)))
        assertEquals(1, Analytics.level(Tally(1, 4)))
        assertEquals(2, Analytics.level(Tally(1, 2)))
        assertEquals(2, Analytics.level(Tally(2, 4)))
        assertEquals(2, Analytics.level(Tally(2, 3)))
        assertEquals(3, Analytics.level(Tally(3, 3)))
        assertEquals(3, Analytics.level(Tally(1, 1)))
    }

    // ---- trend ----

    private val starts12 = (0 until 12).map { 100L + 7 * it }
    private val starts5 = (0 until 5).map { 100L + 7 * it }
    private val farToday = 1000L

    @Test fun steadyAtExactlyEightyPercentCounts() {
        // 35 days, 28 done.
        val p = prac()
        val r = Analytics.trend(listOf(track(p, doneOn("p", 100L..127), first = 0)), starts5, farToday)
        assertEquals(1, r.steady)
        assertEquals(1, r.counted)
    }

    @Test fun belowEightyPercentIsNotSteady() {
        val p = prac()
        val r = Analytics.trend(listOf(track(p, doneOn("p", 100L..126), first = 0)), starts5, farToday)
        assertEquals(0, r.steady)
        assertEquals(1, r.counted)
    }

    @Test fun practicesWithNoDueDaysAreNotCounted() {
        val steady = prac("a", position = 0)
        val later = prac("q", start = 5000, position = 1)
        val r = Analytics.trend(
            listOf(track(steady, doneOn("a", 100L..134), first = 0), track(later, emptyList(), first = 0)),
            starts5,
            farToday,
        )
        assertEquals(1, r.steady)
        assertEquals(1, r.counted)
    }

    // Earlier part: days 100..155. Recent part: days 156..183. Created on 146 gives 10 earlier days.
    private fun twoPart(id: String, created: Long, earlierDone: LongRange, recentDone: LongRange, position: Int = 0, archived: Boolean = false) =
        track(prac(id, created = created, position = position, archived = archived), doneOn(id, earlierDone) + doneOn(id, recentDone), first = null)

    @Test fun aFallOfExactlyTwentyPointsCounts() {
        // Earlier 7 of 10 (0.7). Recent 14 of 28 (0.5).
        val t = twoPart("p", 146, 146L..152, 156L..169)
        val r = Analytics.trend(listOf(t), starts12, farToday)
        assertEquals(Change(t.practice, rose = false), r.change)
    }

    @Test fun aRiseOfExactlyTwentyPointsCounts() {
        // Earlier 3 of 10 (0.3). Recent 14 of 28 (0.5).
        val t = twoPart("p", 146, 146L..148, 156L..169)
        val r = Analytics.trend(listOf(t), starts12, farToday)
        assertEquals(Change(t.practice, rose = true), r.change)
    }

    @Test fun aChangeUnderTwentyPointsGivesNothing() {
        // Earlier 4 of 10 (0.4). Recent 14 of 28 (0.5).
        val t = twoPart("p", 146, 146L..149, 156L..169)
        assertNull(Analytics.trend(listOf(t), starts12, farToday).change)
    }

    @Test fun aFallIsNamedBeforeALargerRise() {
        val rising = twoPart("a", 146, LongRange.EMPTY, 156L..183, position = 0)
        val falling = twoPart("b", 146, 146L..152, 156L..169, position = 1)
        val r = Analytics.trend(listOf(rising, falling), starts12, farToday)
        assertEquals(Change(falling.practice, rose = false), r.change)
    }

    @Test fun theBiggestRiseIsNamedWhenNothingFell() {
        val small = twoPart("a", 146, 146L..148, 156L..169, position = 0)
        val big = twoPart("b", 146, LongRange.EMPTY, 156L..183, position = 1)
        val r = Analytics.trend(listOf(small, big), starts12, farToday)
        assertEquals(Change(big.practice, rose = true), r.change)
    }

    @Test fun theBiggestFallIsNamed() {
        val small = twoPart("a", 146, 146L..152, 156L..169, position = 0)
        val big = twoPart("b", 146, 146L..155, 156L..160, position = 1)
        val r = Analytics.trend(listOf(small, big), starts12, farToday)
        assertEquals(Change(big.practice, rose = false), r.change)
    }

    @Test fun aTieOfFallsGoesToTheLowerPosition() {
        val late = twoPart("late", 146, 146L..152, 156L..169, position = 3)
        val early = twoPart("early", 146, 146L..152, 156L..169, position = 1)
        val r = Analytics.trend(listOf(late, early), starts12, farToday)
        assertEquals(Change(early.practice, rose = false), r.change)
    }

    @Test fun fewerThanSevenDueDaysInTheEarlierPartGivesNoChange() {
        // Created on 150: 6 earlier days, all missed. Recent part all done.
        val six = twoPart("p", 150, LongRange.EMPTY, 156L..183)
        assertNull(Analytics.trend(listOf(six), starts12, farToday).change)
        // Created on 149: 7 earlier days.
        val seven = twoPart("p", 149, LongRange.EMPTY, 156L..183)
        assertEquals(Change(seven.practice, rose = true), Analytics.trend(listOf(seven), starts12, farToday).change)
    }

    @Test fun fewerThanSevenDueDaysInTheRecentPartGivesNoChange() {
        // Archived from 156 and back on 178 leaves 6 recent days. Back on 177 leaves 7.
        val done = doneOn("p", 178L..183)
        val six = track(prac(created = 0), done, periods = listOf(period("p", 156, 178)), first = null)
        assertNull(Analytics.trend(listOf(six), starts12, farToday).change)
        val done7 = doneOn("p", 177L..183)
        val seven = track(prac(created = 0), done7, periods = listOf(period("p", 156, 177)), first = null)
        assertEquals(Change(seven.practice, rose = true), Analytics.trend(listOf(seven), starts12, farToday).change)
    }

    @Test fun archivedPracticesAreLeftOutOfTheTrend() {
        val t = twoPart("p", 146, 146L..152, 156L..169, archived = true)
        assertEquals(TrendSummary(0, 0, null), Analytics.trend(listOf(t), starts12, farToday))
    }

    // ---- lastDays ----

    @Test fun lastDaysCountsTodayOnlyWhenDone() {
        val without = track(prac(), doneOn("p", 71L..99), first = 0)
        assertEquals(Tally(29, 29), Analytics.lastDays(without, 100))
        val with = track(prac(), doneOn("p", 71L..100), first = 0)
        assertEquals(Tally(30, 30), Analytics.lastDays(with, 100))
    }

    @Test fun lastDaysStartsThirtyDaysBack() {
        // Day 70 is outside the 30 days, so a miss there is not counted.
        val t = track(prac(), doneOn("p", 71L..100), first = 0)
        assertEquals(30, Analytics.lastDays(t, 100).due)
        val noneDone = track(prac(), first = 0)
        assertEquals(Tally(0, 29), Analytics.lastDays(noneDone, 100))
    }

    @Test fun lastDaysSkipsDaysBeforeTheStart() {
        val t = track(prac(created = 90), first = null)
        assertEquals(Tally(0, 10), Analytics.lastDays(t, 100))
    }

    // ---- weakestWeekday ----

    // 15 November 2026 is a Sunday.
    private val sunday = epoch(2026, 11, 15)

    // Done on every day except the first [miss] Mondays. Today (a Sunday) is done.
    private fun mondayTrack(from: Long, today: Long, miss: Int, missTwoDays: Boolean = false): Track {
        var mondays = 0
        val days = mutableListOf<Long>()
        for (d in from..today) {
            val wd = LocalDate.ofEpochDay(d).dayOfWeek
            if (wd == DayOfWeek.MONDAY) {
                mondays++
                if (mondays <= miss) continue
            }
            if (missTwoDays && wd == DayOfWeek.TUESDAY) continue
            days += d
        }
        return track(prac(created = from), doneOn("p", days), first = null)
    }

    @Test fun weakestWeekdayIsNullUnderTwentyEightActiveDays() {
        // 27 active days: Tuesday to Sunday, 4 weeks, minus the first Monday.
        val from = sunday - 26
        val t = mondayTrack(from, sunday, miss = 4)
        assertNull(Analytics.weakestWeekday(t, sunday - 83, sunday))
    }

    @Test fun weakestWeekdayIsFoundAtTwentyEightActiveDays() {
        val from = sunday - 27
        assertEquals(DayOfWeek.MONDAY, LocalDate.ofEpochDay(from).dayOfWeek)
        val t = mondayTrack(from, sunday, miss = 4)
        assertEquals(DayOfWeek.MONDAY, Analytics.weakestWeekday(t, sunday - 83, sunday))
    }

    @Test fun weakestWeekdayCountsOnlyActiveDays() {
        // The practice is created 28 days back. The 84-day stretch has only 28 active days.
        val t = mondayTrack(sunday - 27, sunday, miss = 4)
        assertEquals(DayOfWeek.MONDAY, Analytics.weakestWeekday(t, sunday - 83, sunday))
    }

    @Test fun weakestWeekdayIsNullOnATie() {
        val from = sunday - 83
        val t = mondayTrack(from, sunday, miss = 12, missTwoDays = true)
        assertNull(Analytics.weakestWeekday(t, from, sunday))
    }

    @Test fun weakestWeekdayIsNullWhenTheGapIsUnderFifteenPoints() {
        // 40 weeks. Monday 34 of 40 (0.85). Average 274 of 280 is 0.9786. Gap is 0.129.
        val from = sunday - 279
        val t = mondayTrack(from, sunday, miss = 6)
        assertNull(Analytics.weakestWeekday(t, from, sunday))
    }

    @Test fun weakestWeekdayIsFoundWhenTheGapIsExactlyFifteenPoints() {
        // Monday 33 of 40 (0.825). Average 273 of 280 (0.975). Gap is 0.15.
        val from = sunday - 279
        val t = mondayTrack(from, sunday, miss = 7)
        assertEquals(DayOfWeek.MONDAY, Analytics.weakestWeekday(t, from, sunday))
    }

    @Test fun weakestWeekdayIsFoundWhenTheGapIsAboveFifteenPoints() {
        val from = sunday - 279
        val t = mondayTrack(from, sunday, miss = 8)
        assertEquals(DayOfWeek.MONDAY, Analytics.weakestWeekday(t, from, sunday))
    }

    @Test fun weakestWeekdayIsNullWhenNothingWasDue() {
        val t = track(prac(every = 7, start = sunday + 100), first = null)
        assertNull(Analytics.weakestWeekday(t, sunday - 83, sunday))
    }

    // ---- weekly totals and focus ----

    @Test fun weeklyTotalsAddValuesAndNotDoneAddsZero() {
        val entries = listOf(
            e("p", 0, 10), e("p", 6, 5), e("p", 7, 0), e("p", 8, 3), e("p", 14, 99), e("p", 3, 0),
        )
        assertEquals(listOf(15L, 3L), Analytics.weeklyTotals(entries, listOf(0L, 7L)))
    }

    @Test fun weeklyTotalsDoNotOverflowInt() {
        val entries = listOf(e("p", 0, Int.MAX_VALUE), e("p", 1, Int.MAX_VALUE))
        assertEquals(listOf(Int.MAX_VALUE.toLong() * 2), Analytics.weeklyTotals(entries, listOf(0L)))
    }

    @Test fun weeklyFocusIsNullForAWeekWithoutScores() {
        val entries = listOf(e("p", 1, focus = null), e("p", 9, focus = 6))
        val r = Analytics.weeklyFocus(entries, listOf(0L, 7L, 14L))
        assertNull(r[0])
        assertEquals(6.0, r[1]!!, 1e-12)
        assertNull(r[2])
    }

    @Test fun weeklyFocusCountsEachScoredEntryOnceAndSkipsUnscored() {
        val entries = listOf(e("p", 0, focus = 4), e("p", 1, focus = 8), e("p", 2, value = 5, focus = null), e("q", 3, focus = 9))
        assertEquals(7.0, Analytics.weeklyFocus(entries, listOf(0L))[0]!!, 1e-12)
    }

    @Test fun focusAverageIncludesBothEndsAndIsNullWhenEmpty() {
        val entries = listOf(e("p", 5, focus = 2), e("p", 10, focus = 8), e("p", 11, focus = 10))
        assertEquals(5.0, Analytics.focusAverage(entries, 5, 10)!!, 1e-12)
        assertNull(Analytics.focusAverage(entries, 6, 9))
        assertNull(Analytics.focusAverage(emptyList(), 0, 10))
    }

    // ---- monthGrid ----

    private fun checkGrid(month: YearMonth, first: DayOfWeek, leading: Int, trailing: Int) {
        val grid = Analytics.monthGrid(month, first)
        assertEquals(0, grid.size % 7)
        assertEquals(leading, grid.takeWhile { it == null }.size)
        assertEquals(trailing, grid.reversed().takeWhile { it == null }.size)
        val days = grid.filterNotNull()
        assertEquals(month.lengthOfMonth(), days.size)
        assertEquals(month.atDay(1).toEpochDay(), days.first())
        assertEquals(month.atEndOfMonth().toEpochDay(), days.last())
        assertEquals(first, LocalDate.ofEpochDay(grid.first() ?: days.first() - leading).dayOfWeek)
    }

    @Test fun monthGridForAMonthThatStartsOnSunday() {
        // November 2026 starts on a Sunday and has 30 days.
        checkGrid(YearMonth.of(2026, 11), DayOfWeek.MONDAY, leading = 6, trailing = 6)
        checkGrid(YearMonth.of(2026, 11), DayOfWeek.SUNDAY, leading = 0, trailing = 5)
    }

    @Test fun monthGridForAMonthThatStartsOnTuesday() {
        // December 2026 starts on a Tuesday and has 31 days.
        checkGrid(YearMonth.of(2026, 12), DayOfWeek.MONDAY, leading = 1, trailing = 3)
        checkGrid(YearMonth.of(2026, 12), DayOfWeek.SUNDAY, leading = 2, trailing = 2)
    }

    @Test fun monthGridHasNoNullsWhenTheMonthFillsWholeWeeks() {
        // February 2027 starts on a Monday and has 28 days.
        val grid = Analytics.monthGrid(YearMonth.of(2027, 2), DayOfWeek.MONDAY)
        assertEquals(28, grid.size)
        assertTrue(grid.none { it == null })
    }

    @Test fun weekdaysStartOnTheFirstDay() {
        assertEquals(DayOfWeek.SUNDAY, Analytics.weekdays(DayOfWeek.SUNDAY).first())
        assertEquals(DayOfWeek.SATURDAY, Analytics.weekdays(DayOfWeek.SUNDAY).last())
        assertEquals(7, Analytics.weekdays(DayOfWeek.MONDAY).distinct().size)
    }

    // activeWeek: a week counts when the practice was active on any day of it, up to today.

    @Test
    fun activeWeekIsFalseBeforeThePracticeStarted() {
        val t = track(prac(created = 20))
        assertFalse(Analytics.activeWeek(t, 7, 30))
    }

    @Test
    fun activeWeekIsTrueWhenItStartsMidweek() {
        val t = track(prac(created = 20))
        assertTrue(Analytics.activeWeek(t, 14, 30))
    }

    @Test
    fun activeWeekIsFalseWhenArchivedAllWeek() {
        val t = track(prac(), periods = listOf(period("p", 10, 30)))
        assertFalse(Analytics.activeWeek(t, 14, 40))
        assertTrue(Analytics.activeWeek(t, 28, 40))
    }

    @Test
    fun activeWeekIsFalseForAWeekAfterToday() {
        assertFalse(Analytics.activeWeek(track(prac()), 21, 20))
    }
}
