# Maintenance and firmware validation

Thor Wi-Fi Recovery is maintained as a narrow recovery tool for a specific AYN Thor Wi-Fi scanner lockup. The project deliberately prefers a known-good release over speculative compatibility.

## Known-good baseline

- Device: **AYN Thor**
- Android: **13**
- Validated firmware: `Thor_V1.0.0.377_20260206_165408_user`
- Stable line: **0.3.x**
- Recovery remains **fail-closed** on firmware that has not been physically validated.

The previous signed release must remain available while any new firmware, regression or recovery change is being investigated.

## Real-world issue intake

For a new report, collect only the minimum information needed:

1. app version;
2. exact AYN Thor firmware/build string;
3. Android version;
4. whether Wi-Fi is enabled and whether the network list is empty;
5. the app classification/result;
6. the app's sanitized **Full report**, when available;
7. clear reproduction steps.

Do not request or publish Wi-Fi passwords, SSIDs, BSSIDs, IP addresses, device serial numbers, account data or signing material.

Classify the report before changing code:

- **Known lockup on validated firmware** — compare it with the existing scanner-state signature and recovery result.
- **New firmware** — treat it as unsupported until the firmware-validation gate below passes.
- **App regression** — reproduce the UI/logic failure independently from the Wi-Fi firmware path where possible.
- **Different Wi-Fi problem** — authentication, DHCP, router/Internet or ordinary connectivity failures are outside this tool's recovery scope.

## New firmware gate

A new AYN firmware is not added to the supported signature because it appears similar or because the app launches successfully.

Use this sequence:

1. Record the exact firmware/build string and create a checkpoint branch.
2. Keep the production firmware gate unchanged.
3. Install the latest signed stable APK and collect read-only baseline evidence.
4. Confirm whether the scanner state, carrier state and low-level radio evidence still match the known failure model.
5. If code changes are needed, make the smallest reversible change and add/update automated tests first.
6. Use an emulator for UI, lifecycle, persistence and pure diagnosis logic that does not depend on Thor hardware.
7. Use the physical Thor only for firmware/scanner/root/recovery behavior.
8. On the physical device, validate:
   - healthy `IdleState` remains classified as healthy;
   - the real lockup can be detected without triggering an unnecessary framework scan;
   - recovery is unavailable for ambiguous/unsupported states;
   - the intended recovery path executes;
   - post-recovery verification returns to a healthy scanner state when recovery succeeds;
   - Wi-Fi remains usable and can associate again;
   - a failed recovery is reported as failed/unverified rather than success.
9. Re-run unit tests, lint, translation parity, the no-`INTERNET` APK check and sensitive-log guards.
10. Only after the physical evidence passes, extend the exact firmware allowlist and prepare a signed maintenance release.

If the new firmware behaves differently, do not widen the diagnostic or recovery signature merely to make it pass. Keep the existing stable release intact and investigate the new case separately.

## App regression workflow

For an app bug that does not depend on Thor-specific hardware:

1. reproduce it on the emulator when possible;
2. create a focused test or deterministic probe;
3. checkpoint before changing direction;
4. make one small reversible change;
5. run the relevant local tests plus full CI;
6. use the Thor only if the final behavior depends on scanner state, firmware gating, root commands, Wi-Fi recovery or device-specific rendering.

Changes to recovery commands, firmware gating, escalation or post-recovery verification require physical Thor validation before release.

## Release gates

Before a stable or maintenance tag is published:

- `main` is clean and CI is green;
- versionName/versionCode are intentional and unique;
- all supported locales have exact resource parity;
- lint and unit tests pass;
- the release APK is signed with the established certificate;
- the built APK has no `android.permission.INTERNET`;
- diagnostic/history privacy checks pass;
- a manual signed snapshot workflow succeeds;
- the snapshot checksum is valid;
- if recovery/firmware behavior changed, the physical Thor gate above has passed;
- README, screenshots/GIFs and technical notes match visible behavior.

Publishing a tag/release is a separate explicit authorization step.

## Versioning

- **0.3.x**: compatible maintenance, regression fixes, documentation/tooling updates and explicitly validated firmware additions that do not change the product scope.
- A broader recovery model or materially different supported behavior should move to a new minor version rather than silently widening 0.3.x.

Every Android build increments `versionCode`, including maintenance releases.

## Rollback

Never remove the last known-good signed release merely because a newer candidate exists.

If a maintenance candidate fails a release gate:

1. do not publish its tag;
2. keep the current stable release available;
3. revert or fix on a branch;
4. rerun the affected gate;
5. repeat the signed snapshot check before reconsidering publication.

If a published maintenance release later proves faulty, preserve the evidence, stop recommending that release, fix forward with a new version, and keep the prior known-good APK/checksum available for comparison.

## Maintenance state

Once the stable release is published, normal project work should be event-driven:

**firmware update or real report → classify → reproduce → checkpoint → test → minimal fix → QA → signed maintenance release**

No recovery-path change should be made solely for speculative compatibility.
