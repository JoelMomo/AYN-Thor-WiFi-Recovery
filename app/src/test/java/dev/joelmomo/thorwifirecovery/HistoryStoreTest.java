package dev.joelmomo.thorwifirecovery;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class HistoryStoreTest {
    @Test public void entryRoundTripsWithoutNetworkData() {
        HistoryStore.Entry source = new HistoryStore.Entry(
                123456789L, HistoryStore.KIND_RECOVERY, "RECOVERED", 2, 14500L);

        HistoryStore.Entry decoded = HistoryStore.Entry.decode(source.encode());

        assertNotNull(decoded);
        assertEquals(123456789L, decoded.timestampMs);
        assertEquals(HistoryStore.KIND_RECOVERY, decoded.kind);
        assertEquals("RECOVERED", decoded.result);
        assertEquals(2, decoded.recoveryStage);
        assertEquals(14500L, decoded.durationMs);
    }

    @Test public void recoveryMethodsAreStableForReports() {
        assertEquals("framework restart", HistoryStore.recoveryMethod(1));
        assertEquals("Wi-Fi stack + framework", HistoryStore.recoveryMethod(2));
        assertEquals("unknown", HistoryStore.recoveryMethod(0));
    }
}
