# Contributing to Thor Wi-Fi Recovery

Thanks for considering a contribution.

## Before opening an issue

- Search existing issues first.
- Use the bug-report form for reproducible problems.
- Use the feature-request form for new ideas.
- Keep reports focused on one problem.
- Remove passwords, account details, private logs and other sensitive information.

## Pull requests

Small, focused pull requests are easier to review.

1. Fork the repository and create a branch for one change.
2. Keep unrelated formatting or refactoring out of the same pull request.
3. Test the behavior you changed.
4. Update documentation when the user-facing behavior changes.
5. Explain the problem, the change and how it was tested in the pull request.

### Local validation

The Android project uses **JDK 17**. Before opening a pull request, run the same core checks as CI:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug --no-daemon
```

On Windows:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --no-daemon
```

Do not commit APKs, signing files, `signing.properties`, local Android SDK settings or device captures.

If you add or change a translatable string, update every locale declared in `app/src/main/res/xml/locales_config.xml`. CI requires exact resource parity and rejects probable UTF-8 mojibake.

### Recovery-path changes

The recovery path is deliberately conservative. Changes to firmware gating, diagnosis rules, root commands, recovery escalation or post-recovery verification should include concrete validation evidence from the supported AYN Thor firmware before they are proposed for release.

Do not broaden the validated firmware signature or add a lower-level hardware reset solely to make another device/build pass.

There is no guaranteed response time. These projects are maintained in spare time.

## Project links

- Apps and tools: https://joelmomo.github.io/
- Support development: https://joelmomo.github.io/#support
