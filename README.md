# AYN Thor Wi-Fi Recovery

Temporary workaround for an intermittent Wi-Fi scanning lockup observed on the stock AYN Thor Android firmware.

## Tested configuration

- Device: AYN Thor
- Android: 13
- Firmware: `Thor_V1.0.0.377_20260206_165408_user`
- Symptom: Wi-Fi is enabled, but Android keeps scanning and shows no access points.

This is **not an official AYN fix** and it does not modify the firmware.

## Android app (alpha)

The repository also contains an Android app in `app/`.

- UI language follows Android's system language; English and Spanish are included.
- It uses AYN's built-in `PServerBinder` service for a fixed, narrow set of diagnostic/recovery commands.
- The Binder invocation pattern is adapted from `parthi1994/ayn-thor-wifi-recovery` under MIT; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
- The diagnostic does **not** start an Android framework scan. It reads the `WifiSingleScanStateMachine` state with a bounded `dumpsys wifiscanner` query.
- If the scanner is stuck in `ScanningState` while the Wi-Fi link is active, **Recover Wi-Fi** is enabled.
- If the link is down, a low-level `wpa_cli` scan is used only as secondary evidence that the radio can still see APs.
- `IdleState` or an unconfirmed signature keeps recovery disabled.
- The app has no Internet permission and does not store Wi-Fi passwords. Diagnostic logs contain only state booleans/counts, not SSIDs or BSSIDs.

This state-based check avoids using `cmd wifi start-scan` as a diagnostic action, because testing showed that initiating another framework scan can itself leave the scanner in `ScanningState` on the affected firmware.

### Signed APK

The first public signed APK is `v0.2.0-alpha4`. Download it from the GitHub Releases page.

- Release certificate SHA-256: `0d901b01a4230283554200ce674999a89bfe16c00388d95d288e4e2ba5933b59`
- The app requests no Internet permission and does not store Wi-Fi credentials.
- If you installed an earlier debug test APK, uninstall it once before installing the signed release because the signing key is different.

## When to use it

Use this only when the Thor is affected by the same failure pattern:

- Wi-Fi is enabled but the network list is empty.
- Toggling Wi-Fi or airplane mode does not recover scanning.
- A reboot may or may not recover it.
- If ADB is available, `adb shell cmd wifi list-scan-results` may return `No scan results`.

Do not use it for ordinary password, authentication, DHCP or "connected without Internet" problems.

## Script installation

1. Download `Thor_WiFi_Recovery.sh`.
2. Copy it to the Thor, for example to `Download`.
3. Save any open game or application before running it.

## Script usage

1. Open the AYN/Thor settings app.
2. Open **Run script as Root**.
3. Select `Thor_WiFi_Recovery.sh`.
4. Android's UI/framework will restart for a few seconds.
5. Wait for the launcher to return, then open Wi-Fi settings again.

Running the script closes currently open apps. Save your progress first.

## What it does

The script runs:

```sh
setprop ctl.restart zygote
```

On the tested `.377` build this restarts the Android runtime/framework path, including the Wi-Fi scanning service, without rebooting the whole console. It does **not** erase saved Wi-Fi networks, ROMs, emulator data or frontend configuration.

A small timestamp log is written to:

`/sdcard/Download/ayn_thor_wifi_recovery.log`

## What we observed

During the failure, Android's `WifiSingleScanStateMachine` entered `ScanningState` and did not return scan results. After restarting the Android framework, scan results resumed and the device reconnected normally.

See [TECHNICAL_NOTES.md](TECHNICAL_NOTES.md) for the diagnostic evidence and limitations.

## Español

Este repositorio contiene un parche temporal para el fallo intermitente de escaneo Wi-Fi observado en una AYN Thor con firmware `.377`.

Uso:

1. Copia `Thor_WiFi_Recovery.sh` a la Thor.
2. Guarda cualquier partida o trabajo abierto.
3. Abre los ajustes de AYN/Thor y entra en **Run script as Root**.
4. Ejecuta el script.
5. La interfaz de Android se reiniciará durante unos segundos.
6. Cuando vuelva el launcher, comprueba de nuevo la lista de redes Wi-Fi.

No borra ROMs, emuladores, redes guardadas ni la configuración del frontend. Es un workaround experimental, no una actualización oficial de AYN.

## License

MIT. See [LICENSE](LICENSE).
