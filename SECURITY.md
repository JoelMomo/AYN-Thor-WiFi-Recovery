# Security policy

## Supported version

Security fixes are applied to the current maintained line. Use the latest signed GitHub release when reporting or reproducing a security issue; fixes are developed on `main` before the next signed release.

## Reporting a vulnerability

Please do **not** open a public issue for a security vulnerability or include secrets, Wi-Fi credentials, private network identifiers, signing material, device serial numbers, or other sensitive data in a public report.

Use GitHub's private vulnerability reporting for this repository:

**Security → Report a vulnerability**

https://github.com/JoelMomo/AYN-Thor-WiFi-Recovery/security/advisories/new

Include the minimum information needed to reproduce the issue, such as the affected app version, Thor firmware version, Android version, reproduction steps, and impact.

For ordinary bugs that do not involve a security or privacy issue, use the public bug-report form instead.

## Security model

Thor Wi-Fi Recovery is intentionally narrow in scope:

- it does not request `android.permission.INTERNET`;
- diagnostic reports exclude SSIDs, BSSIDs, IP addresses, device serial numbers and passwords;
- local history stores only anonymous check/recovery metadata;
- recovery is gated to the validated AYN Thor firmware build;
- release APKs are signed and published with a SHA-256 checksum.

GitHub Actions also rejects unexpected Internet permission and obvious sensitive Wi-Fi logging patterns during CI.
