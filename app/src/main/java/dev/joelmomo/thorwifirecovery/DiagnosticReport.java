package dev.joelmomo.thorwifirecovery;

final class DiagnosticReport {
    private DiagnosticReport() {}

    static String build(String appVersion, DiagnosticEngine.Snapshot snapshot) {
        return buildFull(appVersion, 0, "unknown", 0, "unknown",
                snapshot, "not run", "none");
    }

    static String buildFull(String appVersion, int versionCode,
                            String androidVersion, int sdkInt, String model,
                            DiagnosticEngine.Snapshot snapshot,
                            String recoveryMode, String recentHistory) {
        StringBuilder out = new StringBuilder();
        out.append("AYN Thor Wi-Fi Recovery technical report\n");
        out.append("App version: ").append(clean(appVersion));
        if (versionCode > 0) out.append(" (").append(versionCode).append(')');
        out.append('\n');
        out.append("Android: ").append(clean(androidVersion));
        if (sdkInt > 0) out.append(" / API ").append(sdkInt);
        out.append('\n');
        out.append("Model: ").append(clean(model)).append('\n');
        out.append("Firmware: ").append(clean(snapshot.device.firmware)).append('\n');
        out.append("Validated firmware: ").append(DiagnosticEngine.SUPPORTED_FIRMWARE).append('\n');
        out.append("Supported firmware: ")
                .append(DiagnosticEngine.isSupportedFirmware(snapshot.device.firmware) ? "yes" : "no")
                .append('\n');
        out.append("Diagnosis: ").append(snapshot.diagnosis().name()).append('\n');
        out.append("Wi-Fi enabled: ").append(snapshot.device.wifiEnabled).append('\n');
        out.append("wlan0 present: ").append(snapshot.device.wlanPresent).append('\n');
        out.append("Carrier: ").append(snapshot.carrierUp ? "up" : "down").append('\n');
        out.append("Scanner exit code: ").append(snapshot.scannerRc).append('\n');
        out.append("Scanner state: ").append(scannerState(snapshot)).append('\n');
        out.append("Low-level AP count: ").append(snapshot.radioAps).append('\n');
        out.append("Last recovery mode: ").append(clean(recoveryMode)).append('\n');
        out.append("\nRecent local history (newest first):\n")
                .append(cleanMultiline(recentHistory)).append('\n');
        out.append("\nPrivacy: network names, hardware addresses, IP addresses, serial numbers ")
                .append("and passwords are not included.\n");
        out.append("Generated locally. The app has no Internet permission and uploads nothing automatically.");
        return out.toString();
    }

    private static String scannerState(DiagnosticEngine.Snapshot snapshot) {
        if (snapshot.scannerIdle()) return "IdleState";
        if (snapshot.scannerScanning()) return "ScanningState";
        return "unknown";
    }

    private static String clean(String value) {
        if (value == null || value.trim().isEmpty()) return "unknown";
        return value.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static String cleanMultiline(String value) {
        if (value == null || value.trim().isEmpty()) return "none";
        return value.replace("\r", "").trim();
    }
}
