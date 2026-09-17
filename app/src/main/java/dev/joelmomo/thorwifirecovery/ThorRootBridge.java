package dev.joelmomo.thorwifirecovery;

import java.nio.charset.StandardCharsets;

/** Minimal fixed-command bridge to AYN's built-in PServerBinder root service. Binder invocation pattern adapted from parthi1994/ayn-thor-wifi-recovery (MIT); see THIRD_PARTY_NOTICES.md. */
final class ThorRootBridge {
    private ThorRootBridge() {}

    static String probe() throws Exception {
        return execute("cmd wifi status");
    }

    static String diagnoseScanner() throws Exception {
        String carrier = execute("cat /sys/class/net/wlan0/carrier 2>/dev/null");
        String scannerState = execute("timeout 5 dumpsys wifiscanner | sed -n '/WifiSingleScanStateMachine:/,/^$/p' | grep 'dest=' | tail -n 1");
        String radioResults = "";
        if (scannerState.contains("dest=ScanningState") && !"1".equals(carrier.trim())) {
            execute("/vendor/bin/wpa_cli -i wlan0 scan");
            Thread.sleep(4000);
            radioResults = execute("/vendor/bin/wpa_cli -i wlan0 scan_results");
        }
        return "__CARRIER__\n" + carrier
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

