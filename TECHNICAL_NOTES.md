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

## Limitations

- This does not permanently fix the firmware bug.
- It restarts Android apps/services, so open applications are closed.
- It has only been validated on the build listed above.
- A future AYN OTA may make this workaround unnecessary or change its behavior.

If your Wi-Fi problem has a different signature, do not assume this script is appropriate.
