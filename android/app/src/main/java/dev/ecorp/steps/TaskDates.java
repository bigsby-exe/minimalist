package dev.ecorp.steps;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

final class TaskDates {
    static boolean active(String expiresAt, Instant now) {
        try { return Instant.parse(expiresAt).isAfter(now); }
        catch (RuntimeException invalid) { return false; }
    }

    static String label(String date, String zone, Instant now) {
        if (date == null || date.isEmpty() || date.equals("null")) return "";
        LocalDate due = LocalDate.parse(date);
        LocalDate today = now.atZone(ZoneId.of(zone)).toLocalDate();
        long days = ChronoUnit.DAYS.between(today, due);
        String label;
        if (days == 0) label = "Today";
        else if (days == 1) label = "Tomorrow";
        else if (days == -1) label = "Yesterday";
        else if (days > 1 && days <= 7) label = "In " + days + " days";
        else if (days < -1 && days >= -7) label = -days + " days overdue";
        else label = due.format(DateTimeFormatter.ofPattern(due.getYear() == today.getYear() ? "d MMM" : "d MMM yyyy", Locale.getDefault()));
        return (days < 0 ? "‼️ " : days == 0 ? "❗ " : "") + label;
    }
}
