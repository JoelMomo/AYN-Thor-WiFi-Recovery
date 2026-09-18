package dev.joelmomo.thorwifirecovery;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class HistoryStore {
    private static final String PREFS = "diagnostic_history";
    private static final String KEY_ENTRIES = "entries";
    private static final int MAX_ENTRIES = 10;

    static final String KIND_CHECK = "CHECK";
    static final String KIND_RECOVERY = "RECOVERY";

    static final class Entry {
        final long timestampMs;
        final String kind;
        final String result;
        final int recoveryStage;
        final long durationMs;

        Entry(long timestampMs, String kind, String result, int recoveryStage, long durationMs) {
            this.timestampMs = timestampMs;
            this.kind = safeToken(kind);
            this.result = safeToken(result);
            this.recoveryStage = recoveryStage;
            this.durationMs = Math.max(0L, durationMs);
        }

        String encode() {
            return timestampMs + "|" + kind + "|" + result + "|" + recoveryStage + "|" + durationMs;
        }

        static Entry decode(String encoded) {
            if (encoded == null || encoded.trim().isEmpty()) return null;
            String[] parts = encoded.split("\\|", -1);
            if (parts.length != 5) return null;
            try {
                return new Entry(Long.parseLong(parts[0]), parts[1], parts[2],
                        Integer.parseInt(parts[3]), Long.parseLong(parts[4]));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        private static String safeToken(String value) {
            if (value == null || value.trim().isEmpty()) return "UNKNOWN";
            return value.replace("|", "_").replace("\n", " ").replace("\r", " ").trim();
        }
    }

    private HistoryStore() {}

    static void recordCheck(Context context, DiagnosticEngine.Diagnosis diagnosis) {
        add(context, new Entry(System.currentTimeMillis(), KIND_CHECK,
                diagnosis == null ? "UNKNOWN" : diagnosis.name(), 0, 0L));
    }

    static void recordRecovery(Context context, String result, int recoveryStage, long durationMs) {
        add(context, new Entry(System.currentTimeMillis(), KIND_RECOVERY,
                result, recoveryStage, durationMs));
    }

    static List<Entry> read(Context context) {
        String raw = prefs(context).getString(KEY_ENTRIES, "");
        if (raw == null || raw.trim().isEmpty()) return Collections.emptyList();
        ArrayList<Entry> entries = new ArrayList<>();
        for (String line : raw.split("\n")) {
            Entry entry = Entry.decode(line);
            if (entry != null) entries.add(entry);
        }
        return entries;
    }

    static void clear(Context context) {
        prefs(context).edit().remove(KEY_ENTRIES).apply();
    }

    static String portableSummary(Context context) {
        List<Entry> entries = read(context);
        if (entries.isEmpty()) return "none";
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        StringBuilder out = new StringBuilder();
        for (Entry entry : entries) {
            out.append(format.format(new Date(entry.timestampMs)))
                    .append(" | ").append(entry.kind)
                    .append(" | ").append(entry.result);
            if (KIND_RECOVERY.equals(entry.kind)) {
                out.append(" | ").append(recoveryMethod(entry.recoveryStage))
                        .append(" | ").append(entry.durationMs).append(" ms");
            }
            out.append('\n');
        }
        return out.toString().trim();
    }

    static String recoveryMethod(int stage) {
        if (stage == 2) return "Wi-Fi stack + framework";
        if (stage == 1) return "framework restart";
        return "unknown";
    }

    private static void add(Context context, Entry entry) {
        ArrayList<Entry> entries = new ArrayList<>(read(context));
        entries.add(0, entry);
        while (entries.size() > MAX_ENTRIES) entries.remove(entries.size() - 1);
        StringBuilder raw = new StringBuilder();
        for (Entry item : entries) {
            if (raw.length() > 0) raw.append('\n');
            raw.append(item.encode());
        }
        prefs(context).edit().putString(KEY_ENTRIES, raw.toString()).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
