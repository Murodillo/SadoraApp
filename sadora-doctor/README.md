# Sadora Doctor

The app doctors use: Kotlin Multiplatform with Compose Multiplatform, Android and iOS.
Doctors sign in with the same phone-and-code accounts as the client app, apply to be
verified, and once approved answer the questions waiting for a doctor and write posts
that carry their name and check mark.

* [shared](./shared/src) — all of the app: `data` (the API over one `ApiCaller`, the
  session, the controllers), `design` and `ui/components` (the client app's design
  system and motion, ported), `i18n` (Uzbek, Russian, English), `nav`, and the screens.
* [androidApp](./androidApp) and [iosApp](./iosApp) — the entry points.
* The wire format is `sadora-backend/contract`, included as a composite build.

### Running against a local backend

- Android emulator: `./gradlew :androidApp:assembleDebug` talks to `10.0.2.2:8080`.
- Android phone over USB: `adb reverse tcp:8080 tcp:8080`, then build with
  `-Psadora.devHost=localhost`. Over Wi-Fi, use the Mac's `.local` name instead; a whole
  `https://` URL points the build at a tunnel.
- iOS simulator: the Debug build talks to `127.0.0.1:8080`. For a phone, set
  `SADORA_DEV_HOST` in `iosApp/Configuration/Config.xcconfig` (or on the xcodebuild line).

A development server that returns `devCode` has the code filled in on the sign-in page.

### Tests

- `./gradlew :shared:testAndroidHostTest` — the common tests on the JVM.
- `./gradlew :shared:compileKotlinIosSimulatorArm64` — the iOS compile.
