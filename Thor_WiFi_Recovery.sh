#!/system/bin/sh
# AYN Thor Wi-Fi Recovery
# Temporary workaround for the intermittent Android Wi-Fi scan lockup.
# Tested on stock Android 13 build Thor_V1.0.0.377_20260206_165408_user.

echo "AYN Thor Wi-Fi framework recovery: $(date)" > /sdcard/Download/ayn_thor_wifi_recovery.log
setprop ctl.restart zygote
