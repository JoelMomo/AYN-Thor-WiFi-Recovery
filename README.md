# AYN Thor Wi-Fi Recovery

[![Android CI](https://github.com/JoelMomo/AYN-Thor-WiFi-Recovery/actions/workflows/android-ci.yml/badge.svg)](https://github.com/JoelMomo/AYN-Thor-WiFi-Recovery/actions/workflows/android-ci.yml)

Temporary workaround for an intermittent Wi-Fi scanning lockup observed on the stock AYN Thor Android firmware.

## Tested configuration

- Device: AYN Thor
- Android: 13
- Firmware: `Thor_V1.0.0.377_20260206_165408_user`
- Symptom: Wi-Fi is enabled, but Android keeps scanning and shows no access points.

This is **not an official AYN fix** and it does not modify the firmware.

## Android app (beta)

The repository also contains an Android app in `app/`. Current beta: `0.3.0-beta11`.

- UI language follows Android's system language; English and Spanish are included.
- It uses AYN's built-in `PServerBinder` service for a fixed, narrow set of diagnostic/recovery commands.
- The Binder invocation pattern is adapted from `parthi1994/ayn-thor-wifi-recovery` under MIT; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
- The diagnostic does **not** start an Android framework scan. It reads the `WifiSingleScanStateMachine` state with a bounded `dumpsys wifiscanner` query.
- If the scanner is stuck in `ScanningState` while the Wi-Fi link is active, **Recover Wi-Fi** is enabled.
- If the link is down, a low-level `wpa_cli` scan is used as secondary evidence that the radio can still see APs.
- When the framework is stuck but the low-level radio path still responds, recovery restarts Android's runtime/framework.
- When there is no carrier and no low-level AP evidence, recovery first restarts the `.377` vendor Wi-Fi HAL and then restarts Android's runtime/framework.
- If the light recovery is still stuck after delayed verification and a retry, the app automatically escalates once to the deeper HAL + framework recovery.
- `IdleState` or an unconfirmed signature keeps recovery disabled.
- The exact validated `.377` build is checked before recovery can be enabled; unknown firmware fails closed.
- After recovery, the app stores a pending-verification flag and checks the scanner automatically when Android returns.
- The diagnosis engine is covered by unit tests for healthy, confirmed, unsupported, timeout, error and ambiguous states.
- After a full check, **Copy diagnostic report** copies a sanitized local report with firmware, diagnosis, scanner state, carrier state and AP count only.
- The UI shows the installed app version and detected firmware, links to the source project, and uses an adaptive launcher icon with Android 13 monochrome support.
- The app has no Internet permission and does not store Wi-Fi passwords. Diagnostic logs contain only state booleans/counts, not SSIDs or BSSIDs.

This state-based check avoids using `cmd wifi start-scan` as a diagnostic action, because testing showed that initiating another framework scan can itself leave the scanner in `ScanningState` on the affected firmware.

### Signed APK

The current beta is `v0.3.0-beta11`. Download the signed APK from the GitHub Releases page.

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

## Installation

### Android app

1. Download the signed APK from the latest GitHub prerelease.
2. Install and open **Thor Wi-Fi Recovery**.
3. Tap **Check scanner** when Android shows the affected symptom.
4. **Recover Wi-Fi** is enabled only when the validated lockup is confirmed.
5. Use **Copy diagnostic report** if you want to share a sanitized result for troubleshooting.

### Manual script fallback

1. Download `Thor_WiFi_Recovery.sh`.
2. Copy it to the Thor, for example to `Download`.
3. Save any open game or application before running it.

## Manual script usage

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

## Development and CI

GitHub Actions runs on every push to `main` and on pull requests. CI uses JDK 17 and runs:

- unit tests;
- Android lint;
- debug APK compilation;
- a built-APK check that rejects `android.permission.INTERNET`;
- a source check that rejects obvious logging of SSIDs, BSSIDs, `WifiInfo` or passwords;
- upload of the debug APK and lint report as workflow artifacts.

Public release APKs remain signed locally with the dedicated release key; the private signing key is not stored in GitHub.

## What I observed

During the failure, Android's `WifiSingleScanStateMachine` entered `ScanningState` and did not return scan results. After restarting the Android framework, scan results resumed and the device reconnected normally.

See [TECHNICAL_NOTES.md](TECHNICAL_NOTES.md) for the diagnostic evidence and limitations.

## Español

Este repositorio contiene un parche temporal para el fallo intermitente de escaneo Wi-Fi observado en una AYN Thor con firmware `.377`.

Uso recomendado:

1. Descarga la APK firmada de la última prerelease.
2. Abre **Thor Wi-Fi Recovery** y pulsa **Comprobar escáner** cuando Android no muestre redes.
3. **Recuperar Wi-Fi** solo se habilita si la app confirma la firma de fallo validada en `.377`.
4. Tras la recuperación, la app comprueba automáticamente si el escáner volvió a `IdleState`.
5. **Copiar informe de diagnóstico** genera un informe local sanitizado para compartir resultados sin nombres de red, direcciones hardware, IP ni contraseñas.

`Thor_WiFi_Recovery.sh` se mantiene como alternativa manual mediante **Run script as Root**.

No borra ROMs, emuladores, redes guardadas ni la configuración del frontend. Es un workaround experimental, no una actualización oficial de AYN.

## License

MIT. See [LICENSE](LICENSE).
