# Reader's Detox — notes

Reference material moved out of the README.

## What's different

- `PasswordActivity` removed.
- `MainActivity` is now the launcher activity (Android opens straight to the
  blocking controls).
- VPN notification's tap-action goes to `MainActivity` (previously
  `PasswordActivity`).
- Application id changed to `com.parentcontrol.socialblocker.detox` so this
  fork coexists with the original on the same device.
- Display name: **Reader's Detox** (was **Social Detox** up to 1.5.1).

Same DNS VPN service and scheduler as the original.

## Reader's look (1.6.0)

Since 1.6.0 the interface follows the Reader's apps (Reader's Notes, Tasks,
Calendar…): black and white only, text rows, a ⋯ menu, prompts above the
keyboard, light/dark, text size and font in the settings. The UI is Kotlin +
Jetpack Compose (`ui/Theme.kt` and `ui/Common.kt` are the Reader's kit); the VPN
service, preferences, schedule check and boot receiver are the original Java.
The allowed hours are a list of ranges instead of a typed `6-8,18-22` string,
the maths gate is a screen of the app, and the app speaks English, French,
German, Spanish, Portuguese and Russian. The blocker also restarts itself after
an app update, and the allowed hours are locked while blocking, like the
sites and the maths switch. Display name since 1.6.0: Reader's Detox (the
application id is unchanged, so it updates the old Social Detox install).

## Widget (1.7.0)

A one-line home-screen widget: the state in words, and a switch. Off → on
starts blocking at once when the VPN consent is already given, otherwise it
opens the app on the consent dialog. On → off takes exactly the app's path
(`Blocker.stopOrChallenge`): straight away, or the maths challenge when the
gate is on. The switch is an invisible activity that decides at the moment of
the tap. The widget is redrawn by every change of state (`Blocker`, the
service's `onRevoke`, the boot/update receiver, leaving the app) and once an
hour while its text depends on the allowed hours. Turning the VPN off in the
system settings now counts as stopping.
