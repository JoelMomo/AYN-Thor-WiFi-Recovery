package dev.joelmomo.thorwifirecovery;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DiagnosticEngine {
    static final String SUPPORTED_FIRMWARE = "Thor_V1.0.0.377_20260206_165408_user";

    enum Diagnosis {
        READY,
        LOCKUP_CONFIRMED,
        UNSUPPORTED_FIRMWARE,
        WIFI_DISABLED,
        WLAN_MISSING,
        SCANNER_TIMEOUT,
        SCANNER_ERROR,
        LOCKUP_PROBABLE,
        SCANNER_UNKNOWN
    }

    static final class DeviceInfo {
        final String firmware;
        final boolean wifiEnabled;
        final boolean wlanPresent;

        DeviceInfo(String firmware, boolean wifiEnabled, boolean wlanPresent) {
            this.firmware = firmware;
            this.wifiEnabled = wifiEnabled;
            this.wlanPresent = wlanPresent;
        }
    }

    static final class Snapshot {
        final DeviceInfo device;
        final boolean carrierUp;
        final int scannerRc;
        final String scannerStateLine;
        final int radioAps;

        Snapshot(DeviceInfo device, boolean carrierUp, int scannerRc,
                 String scannerStateLine, int radioAps) {
            this.device = device;
            this.carrierUp = carrierUp;
            this.scannerRc = scannerRc;
            this.scannerStateLine = scannerStateLine == null ? "" : scannerStateLine;
            this.radioAps = radioAps;
        }

        boolean scannerIdle() {
            return scannerStateLine.contains("dest=IdleState");
        }

        boolean scannerScanning() {
            return scannerStateLine.contains("dest=ScanningState");
        }

        Diagnosis diagnosis() {
            if (!isSupportedFirmware(device.firmware)) return Diagnosis.UNSUPPORTED_FIRMWARE;
            if (!device.wifiEnabled) return Diagnosis.WIFI_DISABLED;
            if (!device.wlanPresent) return Diagnosis.WLAN_MISSING;
            if (scannerIdle()) return Diagnosis.READY;
            if (scannerScanning() && (carrierUp || radioAps > 0)) {
                return Diagnosis.LOCKUP_CONFIRMED;
            }
            if (scannerRc == 124) return Diagnosis.SCANNER_TIMEOUT;
            if (scannerRc != 0) return Diagnosis.SCANNER_ERROR;
            if (scannerScanning()) return Diagnosis.LOCKUP_PROBABLE;
            return Diagnosis.SCANNER_UNKNOWN;
        }
    }

    private DiagnosticEngine() {}

    static boolean isSupportedFirmware(String firmware) {
        return SUPPORTED_FIRMWARE.equals(firmware == null ? "" : firmware.trim());
    }

    static DeviceInfo parseProbe(String raw) {
        return new DeviceInfo(
                section(raw, "__FIRMWARE__", "__WIFI_ENABLED__").trim(),
                "1".equals(section(raw, "__WIFI_ENABLED__", "__WLAN_PRESENT__").trim()),
                "1".equals(section(raw, "__WLAN_PRESENT__", null).trim()));
    }

    static Snapshot parseDiagnosis(String raw) {
        DeviceInfo device = new DeviceInfo(
                section(raw, "__FIRMWARE__", "__WIFI_ENABLED__").trim(),
                "1".equals(section(raw, "__WIFI_ENABLED__", "__WLAN_PRESENT__").trim()),
                "1".equals(section(raw, "__WLAN_PRESENT__", "__CARRIER__").trim()));
        boolean carrierUp = "1".equals(section(raw, "__CARRIER__", "__SCANNER_RC__").trim());
        int scannerRc = parseInt(section(raw, "__SCANNER_RC__", "__SCANNER_STATE__").trim(), -1);
        String scannerState = section(raw, "__SCANNER_STATE__", "__RADIO_RESULTS__").trim();
        int radioAps = countAccessPoints(section(raw, "__RADIO_RESULTS__", null));
        return new Snapshot(device, carrierUp, scannerRc, scannerState, radioAps);
    }

    static String section(String output, String startMarker, String endMarker) {
        if (output == null) return "";
        int start = output.indexOf(startMarker);
        if (start < 0) return "";
        start += startMarker.length();
        int end = endMarker == null ? output.length() : output.indexOf(endMarker, start);
        if (end < 0) end = output.length();
        return output.substring(start, end);
    }

    static int countAccessPoints(String output) {
        Matcher matcher = Pattern.compile("(?i)(?:[0-9a-f]{2}:){5}[0-9a-f]{2}")
                .matcher(output == null ? "" : output);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
