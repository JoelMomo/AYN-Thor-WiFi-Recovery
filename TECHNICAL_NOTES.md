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

## Additional failure signature observed

A later test produced a different state: both `cmd wifi list-scan-results` and a root `wpa_cli -i wlan0 scan_results` returned zero APs. Restarting `wpa_supplicant`, `wificond`, and `vendor.wifi_hal_legacy` individually did not restore scan results.

The Android app therefore treats this as a different failure signature and does not enable the validated zygote recovery path for it.

## App diagnostic rule

- Android APs > 0: healthy scan path.
- Android APs = 0 and low-level radio APs > 0: validated framework-only lockup; recovery may be offered.
- Android APs = 0 and low-level radio APs = 0: different/undetermined failure; recovery remains disabled.

## Limitations

- This does not permanently fix the firmware bug.
- It restarts Android apps/services, so open applications are closed.
- It has only been validated on the build listed above.
- A future AYN OTA may make this workaround unnecessary or change its behavior.

If your Wi-Fi problem has a different signature, do not assume this script is appropriate.
