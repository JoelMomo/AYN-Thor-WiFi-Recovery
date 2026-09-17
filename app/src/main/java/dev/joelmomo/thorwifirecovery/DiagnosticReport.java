package dev.joelmomo.thorwifirecovery;

final class DiagnosticReport {
    private DiagnosticReport() {}

    static String build(String appVersion, DiagnosticEngine.Snapshot snapshot) {
        StringBuilder out = new StringBuilder();
        out.append("AYN Thor Wi-Fi Recovery diagnostic\n");
        out.append("App version: ").append(clean(appVersion)).append('\n');
        out.append("Firmware: ").append(clean(snapshot.device.firmware)).append('\n');
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
        out.append("Privacy: network names, hardware addresses, IP addresses and passwords are not included.\n");
        out.append("Generated locally; nothing is uploaded automatically.");
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
}
