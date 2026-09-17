package dev.joelmomo.thorwifirecovery;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DiagnosticEngineTest {
    private static final String FW = DiagnosticEngine.SUPPORTED_FIRMWARE;

    @Test public void supportedFirmwareRequiresExactValidatedBuild() {
        assertTrue(DiagnosticEngine.isSupportedFirmware(FW));
        assertFalse(DiagnosticEngine.isSupportedFirmware("Thor_V1.0.0.377_other"));
        assertFalse(DiagnosticEngine.isSupportedFirmware("Thor_V1.0.0.378_test"));
    }

    @Test public void parseProbeReadsSanitizedDeviceState() {
        DiagnosticEngine.DeviceInfo info = DiagnosticEngine.parseProbe(
                "__FIRMWARE__\n" + FW + "\n__WIFI_ENABLED__\n1\n__WLAN_PRESENT__\n1\n");
        assertEquals(FW, info.firmware);
        assertTrue(info.wifiEnabled);
        assertTrue(info.wlanPresent);
    }

    @Test public void idleScannerIsReady() {
        assertEquals(DiagnosticEngine.Diagnosis.READY,
                snapshot(true, true, true, 0, "dest=IdleState", "").diagnosis());
    }

    @Test public void scanningWithCarrierIsConfirmedLockup() {
        assertEquals(DiagnosticEngine.Diagnosis.LOCKUP_CONFIRMED,
                snapshot(true, true, true, 124, "dest=ScanningState", "").diagnosis());
    }

    @Test public void scanningWithVisibleRadioIsConfirmedLockup() {
        String ap = "aa:bb:cc:dd:ee:ff 2412 -55 [ESS] test";
        assertEquals(DiagnosticEngine.Diagnosis.LOCKUP_CONFIRMED,
                snapshot(true, true, false, 0, "dest=ScanningState", ap).diagnosis());
    }

    @Test public void scannerTimeoutWithoutIndependentRadioEvidenceIsNotRecoverable() {
        assertEquals(DiagnosticEngine.Diagnosis.SCANNER_TIMEOUT,
                snapshot(true, true, false, 124, "", "").diagnosis());
    }

    @Test public void nonzeroScannerErrorIsReportedSeparately() {
        assertEquals(DiagnosticEngine.Diagnosis.SCANNER_ERROR,
                snapshot(true, true, false, 1, "", "").diagnosis());
    }

    @Test public void scanningWithoutCarrierOrApsIsUnconfirmed() {
        assertEquals(DiagnosticEngine.Diagnosis.SCANNER_UNCONFIRMED,
                snapshot(true, true, false, 0, "dest=ScanningState", "").diagnosis());
    }

    @Test public void unknownScannerStateFailsClosed() {
        assertEquals(DiagnosticEngine.Diagnosis.SCANNER_UNKNOWN,
                snapshot(true, true, false, 0, "", "").diagnosis());
    }

    @Test public void wifiDisabledOverridesScannerState() {
        assertEquals(DiagnosticEngine.Diagnosis.WIFI_DISABLED,
                snapshot(false, true, true, 0, "dest=ScanningState", "").diagnosis());
    }

    @Test public void missingWlanInterfaceIsReported() {
        assertEquals(DiagnosticEngine.Diagnosis.WLAN_MISSING,
                snapshot(true, false, false, 0, "", "").diagnosis());
    }

    @Test public void unsupportedFirmwareFailsClosed() {
        String raw = diagnosticRaw("Thor_V1.0.0.378_test", true, true, true,
                0, "dest=ScanningState", "aa:bb:cc:dd:ee:ff");
        assertEquals(DiagnosticEngine.Diagnosis.UNSUPPORTED_FIRMWARE,
                DiagnosticEngine.parseDiagnosis(raw).diagnosis());
    }

    @Test public void bssidCounterCountsAccessPoints() {
        String results = "aa:bb:cc:dd:ee:ff first\n11:22:33:44:55:66 second\n";
        assertEquals(2, DiagnosticEngine.countAccessPoints(results));
    }

    private static DiagnosticEngine.Snapshot snapshot(boolean wifiEnabled, boolean wlanPresent,
                                                        boolean carrierUp, int scannerRc,
                                                        String scannerState, String radioResults) {
        return DiagnosticEngine.parseDiagnosis(diagnosticRaw(FW, wifiEnabled, wlanPresent,
                carrierUp, scannerRc, scannerState, radioResults));
    }

    private static String diagnosticRaw(String firmware, boolean wifiEnabled,
                                        boolean wlanPresent, boolean carrierUp,
                                        int scannerRc, String scannerState,
                                        String radioResults) {
        return "__FIRMWARE__\n" + firmware
                + "\n__WIFI_ENABLED__\n" + (wifiEnabled ? "1" : "0")
                + "\n__WLAN_PRESENT__\n" + (wlanPresent ? "1" : "0")
                + "\n__CARRIER__\n" + (carrierUp ? "1" : "0")
                + "\n__SCANNER_RC__\n" + scannerRc
                + "\n__SCANNER_STATE__\n" + scannerState
                + "\n__RADIO_RESULTS__\n" + radioResults;
    }
}
