# Technical notes

## Scope

This workaround was validated on one AYN Thor running stock Android 13 build:

`Thor_V1.0.0.377_20260206_165408_user`

It should be treated as an experimental workaround until reproduced by more users.

## Failure signature

Observed while the Wi-Fi UI showed no networks:

```text
Wifi is enabled
Wifi scanning is always available
Wifi is not connected
```

A framework scan returned no results, while earlier direct low-level testing showed the radio could still see nearby access points.

The Android scanner state machine showed:

```text
IdleState -> ScanningState
```

and did not receive the normal scan-completion transition back to `IdleState`.

## Recovery result

After `setprop ctl.restart zygote`, Android restarted its framework/runtime path. On the tested unit, subsequent scans returned dozens of APs and Wi-Fi reconnected normally.

## Interpretation

The evidence points to an intermittent lockup in the Android Wi-Fi scanning/framework path on the tested firmware, rather than a dead radio or antenna. This repository does not claim a confirmed vendor root cause.

The failure was also reproduced with Wi-Fi 7 enabled and recovered without changing router settings, so the script is intentionally router-independent.

## Additional validation

A later physical test refined the app diagnostic. While Wi-Fi remained associated, `WifiSingleScanStateMachine` was observed with its latest transition ending in `dest=ScanningState`; `dumpsys wifiscanner` also timed out instead of completing normally. The app detected this state together with an active `wlan0` carrier and enabled recovery.

After triggering **Recover Wi-Fi** from the app, `setprop ctl.restart zygote` restarted the Android framework. The Thor re-associated automatically. A subsequent read-only app check reported the scanner in `IdleState` and disabled the recovery button.

The same recovery cycle also produced a successful scanner completion with 26 results and a normal `ScanningState -> IdleState` transition before the intermittent bug was reproduced again by a later framework scan. This supports the workaround behavior but does not establish a permanent firmware fix.

## App diagnostic rule

The current beta does not trigger an Android framework scan merely to diagnose the problem.

- Latest `WifiSingleScanStateMachine` transition ends in `IdleState`: scanner is responsive; recovery stays disabled.
- Latest transition ends in `ScanningState` and `wlan0` carrier is active: validated lockup signature; recovery is enabled.
- `ScanningState` with no carrier: the app performs a low-level `wpa_cli` scan; if the radio still sees APs, recovery may be enabled.
- Missing/ambiguous state with no independent evidence that the radio path is alive: recovery stays disabled.

The scanner query is bounded with `timeout` so a hung `wifiscanner` service cannot block the app indefinitely.

## Android app end-to-end validation

`v0.2.0-alpha4` was validated on the same `.377` Thor with the scanner stuck in `ScanningState` while the Wi-Fi link remained active. The app enabled recovery, executed the zygote/framework restart, and after Android returned the scanner reported `IdleState`, Wi-Fi was associated again, and the recovery button was disabled.

The public release APK was then installed on the device and re-checked. The installed APK SHA-256 matched the published build exactly, and its signing certificate SHA-256 was:

`0d901b01a4230283554200ce674999a89bfe16c00388d95d288e4e2ba5933b59`

The release manifest declares no app permissions.


## Beta1 validation

`v0.3.0-beta1` adds exact firmware gating, explicit scanner error states, unit-tested diagnosis logic, and post-recovery verification.

On the validated `.377` Thor, a framework scan was deliberately used to reproduce a persistent `ScanningState` while `wlan0` carrier remained active. The beta classified this as `LOCKUP_CONFIRMED` and enabled recovery.

Recovery was then triggered from the app itself. The app persisted a pending-verification flag before executing `setprop ctl.restart zygote`. After Android returned, reopening the app automatically ran the read-only diagnostic, observed `IdleState`, displayed **Recovery verified**, and kept the recovery button disabled.

A separate boot-time reproduction with `ScanningState`, no carrier, and no low-level AP evidence was deliberately classified as unconfirmed and recovery remained disabled. Wi-Fi-off behavior was also validated physically.

The diagnosis engine currently has 13 local unit tests covering the supported build, healthy state, confirmed lockup paths, timeout/error handling, unsupported firmware, Wi-Fi disabled, missing `wlan0`, and ambiguous states.

## Beta2 hardening and UX

`v0.3.0-beta2` adds a sanitized diagnostic-report path, CI checks and UI/launcher polish without widening the recovery scope.

The copied diagnostic report contains the app version, firmware, classification, Wi-Fi enabled state, `wlan0` presence, carrier state, scanner exit code/state and low-level AP count. It does not include network names, hardware addresses, IP addresses or Wi-Fi passwords, and nothing is uploaded automatically.

The app now exposes the installed version and detected firmware in the UI, provides an external GitHub project link, and uses an adaptive launcher icon with an Android 13 monochrome resource. Opening the project delegates to the installed browser and does not require the app itself to request Internet permission.

Local unit coverage is 14 tests: 13 diagnosis tests plus a report-privacy test. GitHub Actions repeats unit tests, lint and a debug build on pushes and pull requests, checks the built APK for `android.permission.INTERNET`, and rejects obvious sensitive Wi-Fi logging patterns in app source.

## Beta11 adaptive recovery

Testing on the validated `.377` unit showed that restarting Android's framework with
`setprop ctl.restart zygote` can recover the scanner, but it is not the only layer
that can remain stale. The vendor Wi-Fi HAL on this build is managed by the init
service `vendor.wifi_hal_legacy`.

Direct device testing found:

- restarting the vendor Wi-Fi HAL without restarting Android's framework was not
  sufficient; the existing framework-side scanner could remain stale;
- restarting the vendor Wi-Fi HAL and then restarting zygote restored association,
  framework scan results and `WifiSingleScanStateMachine` to `IdleState`;
- a normal zygote-only recovery remains sufficient when the link or low-level scan
  evidence shows that the radio path is still alive.

`v0.3.0-beta11` therefore uses adaptive recovery:

- framework-only recovery when carrier or low-level AP evidence is present;
- vendor Wi-Fi HAL + framework recovery when there is no carrier and no low-level
  AP evidence;
- an 8-second settling delay plus a second verification 5 seconds later;
- one automatic escalation from framework-only to HAL + framework recovery if the
  scanner is still locked after both checks.

This is still a recovery workaround for the stock firmware defect, not a permanent
firmware modification.

## Beta15 localization, UI and validation pass

`v0.3.0-beta15` is a UI/documentation hardening pass; it does not widen the recovery signature or add new recovery commands.

The physical Thor was used to validate the status-card animation. A screen recording of the previous beta showed that pressing **Check Wi-Fi** restarted the liquid transition while the diagnostic was still running. Beta15 separates the temporary **Checking…** presentation from the status fill: the ambient wave continues without being reset, and the liquid fill changes only when a real diagnostic result arrives. A second device recording was used to confirm the regression was removed.

The app now ships twelve UI languages: English, Spanish, Catalan, Galician, Basque, Portuguese, German, French, Italian, Russian, Japanese and Simplified Chinese. All localized files are checked against the default translatable resource set, and CI now rejects missing or extra localized resources. The generic `values-es` resource is intentionally retained so Android Spanish variants such as `es-419` resolve to Spanish rather than English.

A compact **+ apps** link was also added to the header. It delegates to the system browser and opens the developer's GitHub repository list, providing a stable path to CarePad and future projects without adding `android.permission.INTERNET` to this app.

This pass also removed unused UI helpers, fields, strings and imports identified during lint/manual review. Unit tests, Android lint and signed release assembly are rerun after these changes before publishing.

During the final physical screenshot pass, the fault was reproduced again after a reboot: the framework exposed no scan results and the single-scan state machine remained in `ScanningState`. The app classified it as a probable lockup and correctly exposed recovery. In this particular reproduction, the recovery attempt did not clear the scanner and the app reported **Scanner still stuck** rather than claiming success. A second attempt also remained stuck. This reinforces that the project is a recovery workaround, not a guaranteed firmware fix; the recovery logic itself was not broadened in beta15.

## Beta16 Unicode and deep-recovery hardening

`v0.3.0-beta16` fixes two independent issues found during physical-device validation.

Seven localized resource files had been accidentally stored with reversible UTF-8 mojibake. This affected characters such as `ó`, middle dots, Cyrillic, Japanese and Chinese text. The resources were repaired as UTF-8, and CI now rejects the same reversible mojibake pattern in future localized files. Nunito itself was not the cause: its bundled cmap covers every Latin and Cyrillic code point used by the app. Japanese and Simplified Chinese continue to use Android's system CJK fallback, which was visually checked on the physical Thor. All twelve languages were re-captured in both dark and light themes after the repair.

Recovery was also hardened after beta15 reproduced a lockup that survived the earlier HAL-only escalation. The deep path now disables Wi-Fi, restarts `vendor.wifi_hal_legacy`, `wificond` and `wpa_supplicant`, re-enables Wi-Fi, waits for the stack to settle, and finally restarts Android's framework. The light path remains framework-only, and a persistent recoverable lock can escalate once from light to deep recovery.

A lower PCI/driver reset was investigated on the physical Thor. Unbinding and rebinding the Qualcomm `cnss_pci` device did reinitialize the Wi-Fi firmware and produced fresh scan results, but experimental sequences could also leave Android's Wi-Fi services stopped or Wi-Fi disabled during handoff. That path is therefore intentionally **not shipped** in beta16; the app keeps the less invasive userspace Wi-Fi-stack reset as its deepest automatic recovery.

The physical Thor was used throughout this pass. The scanner was repeatedly driven into `ScanningState`, recovery availability was confirmed in the UI, and the device was returned to a healthy `IdleState` with scan results and a live carrier before final validation. This remains a firmware workaround rather than a guaranteed fix.

## Limitations

- This does not permanently fix the firmware bug.
- It restarts Android apps/services, so open applications are closed.
- It has only been validated on the build listed above.
- A future AYN OTA may make this workaround unnecessary or change its behavior.

If your Wi-Fi problem has a different signature, do not assume this script is appropriate.
