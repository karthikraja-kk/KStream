package com.kstream.tv.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DurationFormatTest {

    @Test fun parses_plain_integer_minutes() {
        assertEquals(165, DurationFormat.parseMinutes("165"))
        assertEquals(45, DurationFormat.parseMinutes("45"))
    }

    @Test fun parses_int_with_min_suffix() {
        assertEquals(148, DurationFormat.parseMinutes("148 min"))
        assertEquals(148, DurationFormat.parseMinutes("148 mins"))
        assertEquals(148, DurationFormat.parseMinutes("148 minutes"))
        assertEquals(148, DurationFormat.parseMinutes("148 minute"))
    }

    @Test fun parses_hour_only_forms() {
        assertEquals(120, DurationFormat.parseMinutes("2h"))
        assertEquals(120, DurationFormat.parseMinutes("2hr"))
        assertEquals(120, DurationFormat.parseMinutes("2 hr"))
        assertEquals(120, DurationFormat.parseMinutes("2 hour"))
        assertEquals(120, DurationFormat.parseMinutes("2 hours"))
        assertEquals(60, DurationFormat.parseMinutes("1H"))
    }

    @Test fun parses_hour_and_min_combined() {
        assertEquals(135, DurationFormat.parseMinutes("2h 15m"))
        assertEquals(135, DurationFormat.parseMinutes("2hr 15min"))
        assertEquals(135, DurationFormat.parseMinutes("2 hours 15 minutes"))
        assertEquals(135, DurationFormat.parseMinutes("2h15m"))
        assertEquals(135, DurationFormat.parseMinutes("2hr15min"))
    }

    @Test fun parses_colon_formats() {
        assertEquals(150, DurationFormat.parseMinutes("2:30"))
        assertEquals(150, DurationFormat.parseMinutes("02:30"))
        assertEquals(133, DurationFormat.parseMinutes("02:13:12"))
        assertEquals(90, DurationFormat.parseMinutes("1:30:00"))
        assertEquals(12, DurationFormat.parseMinutes("00:12:00"))
    }

    @Test fun parses_colon_with_whitespace() {
        assertEquals(133, DurationFormat.parseMinutes("02 : 13 : 12"))
        assertEquals(150, DurationFormat.parseMinutes("02 :30"))
    }

    @Test fun parses_colon_with_trailing_unit_suffix() {
        // Source data sometimes appends a redundant unit token after an
        // H:MM:SS string ("02:58:07 min"). The colon parser tolerates it
        // rather than falling through to the loose token scanner which
        // would only pick up the trailing "07 min" → 7 mins.
        assertEquals(178, DurationFormat.parseMinutes("02:58:07 min"))
        assertEquals(178, DurationFormat.parseMinutes("02:58:07 mins"))
        assertEquals(178, DurationFormat.parseMinutes("02:58:07 minutes"))
        assertEquals(150, DurationFormat.parseMinutes("2:30 hr"))
        assertEquals(150, DurationFormat.parseMinutes("2:30 hours"))
        assertEquals("2 hrs 58 mins", DurationFormat.format("02:58:07 min"))
    }

    @Test fun parses_iso_8601() {
        assertEquals(135, DurationFormat.parseMinutes("PT2H15M"))
        assertEquals(60, DurationFormat.parseMinutes("PT1H"))
        assertEquals(45, DurationFormat.parseMinutes("PT45M"))
        assertEquals(135, DurationFormat.parseMinutes("pt2h15m"))
    }

    @Test fun parses_decimal_hours() {
        assertEquals(150, DurationFormat.parseMinutes("2.5 hours"))
        assertEquals(150, DurationFormat.parseMinutes("2.5h"))
    }

    @Test fun normalises_non_ascii_spaces() {
        assertEquals(133, DurationFormat.parseMinutes("2\u00A0hr 13\u00A0min"))
    }

    @Test fun returns_null_for_unrecognized() {
        assertNull(DurationFormat.parseMinutes("unknown"))
        assertNull(DurationFormat.parseMinutes(""))
        assertNull(DurationFormat.parseMinutes("   "))
    }

    @Test fun formats_hours_and_minutes() {
        assertEquals("2 hrs 13 mins", DurationFormat.format("02:13:12"))
        assertEquals("2 hrs 28 mins", DurationFormat.format("148"))
        assertEquals("2 hrs 15 mins", DurationFormat.format("2h15m"))
        assertEquals("1 hr 5 mins", DurationFormat.format("65"))
    }

    @Test fun formats_hours_only_when_minutes_zero() {
        assertEquals("1 hr", DurationFormat.format("60"))
        assertEquals("2 hrs", DurationFormat.format("PT2H"))
        assertEquals("2 hrs", DurationFormat.format("02:00:00"))
    }

    @Test fun formats_minutes_only_when_hours_zero() {
        assertEquals("45 mins", DurationFormat.format("45"))
        assertEquals("45 mins", DurationFormat.format("PT45M"))
        assertEquals("12 mins", DurationFormat.format("00:12:00"))
    }

    @Test fun returns_raw_for_unrecognized_or_blank() {
        assertEquals("", DurationFormat.format(""))
        assertEquals("unknown", DurationFormat.format("unknown"))
        assertEquals("garbage value", DurationFormat.format("garbage value"))
    }
}
