package dev.joelmomo.thorwifirecovery;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DiagnosticReportTest {
    @Test public void reportContainsUsefulStateWithoutNetworkIdentifiers() {
        DiagnosticEngine.DeviceInfo info = new DiagnosticEngine.DeviceInfo(
                DiagnosticEngine.SUPPORTED_FIRMWARE, true, true);
        DiagnosticEngine.Snapshot snapshot = new DiagnosticEngine.Snapshot(
                info, true, 0, "dest=ScanningState", 3);

        String report = DiagnosticReport.build("0.3.0-beta2", snapshot);

        assertTrue(report.contains("Diagnosis: LOCKUP_CONFIRMED"));
        assertTrue(report.contains("Scanner state: ScanningState"));
        assertTrue(report.contains("Low-level AP count: 3"));
        assertFalse(report.contains("Thor-5G"));
        assertFalse(report.contains("aa:bb:cc:dd:ee:ff"));
        assertFalse(report.contains("192.168.1.14"));
    }
}
