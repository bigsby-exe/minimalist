package dev.ecorp.steps;

import org.junit.Test;
import java.time.Instant;
import static org.junit.Assert.*;

public class TaskDatesTest {
    @Test public void expirationUsesAnInstantAcrossTheAutumnClockChange() {
        // London midnight after the long DST day is 00:00Z, not 24 hours after the previous reset.
        assertTrue(TaskDates.active("2026-10-26T00:00:00Z", Instant.parse("2026-10-25T23:59:59Z")));
        assertFalse(TaskDates.active("2026-10-26T00:00:00Z", Instant.parse("2026-10-26T00:00:00Z")));
        assertFalse(TaskDates.active("", Instant.now()));
    }

    @Test public void dueDateUsesProfileTimezoneRatherThanPhoneTimezone() {
        Instant now = Instant.parse("2026-10-19T23:30:00Z");
        assertEquals("❗ Today", TaskDates.label("2026-10-20", "Europe/London", now));
        assertEquals("Tomorrow", TaskDates.label("2026-10-20", "America/New_York", now));
        assertEquals("‼️ Yesterday", TaskDates.label("2026-10-19", "Europe/London", now));
        assertEquals("In 7 days", TaskDates.label("2026-10-27", "Europe/London", now));
        assertEquals("‼️ 7 days overdue", TaskDates.label("2026-10-13", "Europe/London", now));
        assertEquals("", TaskDates.label("null", "Europe/London", now));
    }
}
