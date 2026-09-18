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

        String report = DiagnosticReport.buildFull(
                "0.3.0-beta17", 316, "13", 33, "AYN Thor",
                snapshot, "framework restart",
                "2026-09-18 21:00:00 | CHECK | LOCKUP_CONFIRMED");

        assertTrue(report.contains("App version: 0.3.0-beta17 (316)"));
        assertTrue(report.contains("Android: 13 / API 33"));
        assertTrue(report.contains("Model: AYN Thor"));
        assertTrue(report.contains("Diagnosis: LOCKUP_CONFIRMED"));
        assertTrue(report.contains("Scanner state: ScanningState"));
        assertTrue(report.contains("Low-level AP count: 3"));
        assertTrue(report.contains("Last recovery mode: framework restart"));
        assertTrue(report.contains("Recent local history"));
        assertFalse(report.contains("Thor-5G"));
        assertFalse(report.contains("aa:bb:cc:dd:ee:ff"));
        assertFalse(report.contains("192.168.1.14"));
    }

    @Test public void reportNormalizesSingleLineFields() {
        DiagnosticEngine.DeviceInfo info = new DiagnosticEngine.DeviceInfo(
                DiagnosticEngine.SUPPORTED_FIRMWARE + "\nignored", true, true);
        DiagnosticEngine.Snapshot snapshot = new DiagnosticEngine.Snapshot(
                info, false, 0, "dest=IdleState", 0);

        String report = DiagnosticReport.build("test\nversion", snapshot);

        assertFalse(report.contains("test\nversion"));
        assertTrue(report.contains("App version: test version"));
    }
}
