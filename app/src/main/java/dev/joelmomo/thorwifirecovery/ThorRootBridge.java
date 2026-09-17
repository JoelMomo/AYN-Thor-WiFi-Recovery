package dev.joelmomo.thorwifirecovery;

import java.nio.charset.StandardCharsets;

/**
 * Minimal fixed-command bridge to AYN's built-in PServerBinder root service.
 * Binder invocation pattern adapted from parthi1994/ayn-thor-wifi-recovery (MIT);
 * see THIRD_PARTY_NOTICES.md.
 */
final class ThorRootBridge {
    private ThorRootBridge() {}

    static String probe() throws Exception {
        String firmware = execute("getprop ro.build.display.id");
        String wifiEnabled = execute(
                "cmd wifi status | head -n 1 | grep -q 'Wifi is enabled' && echo 1 || echo 0");
        String wlanPresent = execute(
                "if [ -d /sys/class/net/wlan0 ]; then echo 1; else echo 0; fi");
        return "__FIRMWARE__\n" + firmware
                + "\n__WIFI_ENABLED__\n" + wifiEnabled
                + "\n__WLAN_PRESENT__\n" + wlanPresent;
    }

    static String diagnoseScanner() throws Exception {
        String base = probe();
        DiagnosticEngine.DeviceInfo device = DiagnosticEngine.parseProbe(base);
        String carrier = "0";
        String scannerRc = "-1";
        String scannerState = "";
        String radioResults = "";

        if (device.wlanPresent) {
            carrier = execute("cat /sys/class/net/wlan0/carrier 2>/dev/null").trim();
        }

        if (DiagnosticEngine.isSupportedFirmware(device.firmware)
                && device.wifiEnabled && device.wlanPresent) {
            scannerState = execute(
                    "timeout 5 dumpsys wifiscanner "
                    + "| grep -E 'dest=|IdleState|ScanningState' | tail -n 1").trim();
            if (scannerState.isEmpty()) {
                scannerRc = execute(
                        "timeout 5 dumpsys wifiscanner >/dev/null 2>&1; echo $?").trim();
            } else {
                scannerRc = "0";
            }

            boolean scannerScanning = scannerState.contains("ScanningState");
            boolean carrierUp = "1".equals(carrier);
            if (scannerScanning) {
                if (!carrierUp) {
                    execute("/vendor/bin/wpa_cli -i wlan0 scan");
                    Thread.sleep(4000);
                    radioResults = execute("/vendor/bin/wpa_cli -i wlan0 scan_results");
                } else {
                    Thread.sleep(2000);
                }
                String followUpState = execute(
                        "timeout 5 dumpsys wifiscanner "
                        + "| grep -E 'dest=|IdleState|ScanningState' | tail -n 1").trim();
                if (followUpState.isEmpty()) {
                    scannerState = "";
                    scannerRc = execute(
                            "timeout 5 dumpsys wifiscanner >/dev/null 2>&1; echo $?").trim();
                } else {
                    scannerState = followUpState;
                    scannerRc = "0";
                }
            }
        }

        return base
                + "\n__CARRIER__\n" + carrier
                + "\n__SCANNER_RC__\n" + scannerRc
                + "\n__SCANNER_STATE__\n" + scannerState
                + "\n__RADIO_RESULTS__\n" + radioResults;
    }

    static void recover() throws Exception {
        execute("setprop ctl.restart zygote");
    }

    private static String execute(String command) throws Exception {
        Class<?> parcelClass = Class.forName("android.os.Parcel");
        Object request = parcelClass.getMethod("obtain").invoke(null);
        Object reply = parcelClass.getMethod("obtain").invoke(null);
        try {
            Object binder = Class.forName("android.os.ServiceManager")
                    .getMethod("getService", String.class).invoke(null, "PServerBinder");
            if (binder == null) throw new IllegalStateException("PServerBinder not found");
            parcelClass.getMethod("writeStringArray", String[].class)
                    .invoke(request, (Object) new String[]{command, "0"});
            boolean handled = (Boolean) Class.forName("android.os.IBinder")
                    .getMethod("transact", int.class, parcelClass, parcelClass, int.class)
                    .invoke(binder, 0, request, reply, 0);
            if (!handled) throw new IllegalStateException("AYN transaction rejected");
            byte[] bytes = (byte[]) parcelClass.getMethod("createByteArray").invoke(reply);
            return bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
        } finally {
            parcelClass.getMethod("recycle").invoke(request);
            parcelClass.getMethod("recycle").invoke(reply);
        }
    }
}
