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

## Daily budget (1.8.0)

An optional number of minutes a day in the apps of the chosen sites. `SitePackages`
maps each site to its Android apps (official and the usual third-party clients;
the manifest `<queries>` must name every one of them, a unit test checks it,
since Android 11 hides undeclared packages from `getApplicationInfo` and from
the usage events). `Budget` (plain Java, `BudgetTest`) sums the day's stretches
of those apps from the usage events — an activity's RESUMED to its PAUSED or
STOPPED, closed early by another package in front, the screen going off or a
reboot, the union when two activities of one app overlap, clipped to the local
calendar day — and decides: without a budget the hours decide as before (none =
all day); with one, the sites are blocked once it is spent and, when hours are
set, outside them too; with a budget and no hours the budget alone decides.
`BudgetMonitor` reads the events (`PACKAGE_USAGE_STATS`, given by the user in
the usage-access settings), stores the count in the preferences (day key +
milliseconds) where the service's per-lookup decision reads it, and redraws the
widget. While the VPN runs it measures again when the minutes left could be
spent (at most every two minutes, only while the screen is on), at screen on
and unlock, and at midnight (a main-thread `Handler`, no exact alarm, so no
alarm permission); the app measures when it opens and the boot/update receiver
after a start. At midnight the stored count belongs to the old day, so the
sites open again by themselves. Browser use of a site is not counted (the DNS
block still applies to it once the budget is spent).

## Name lookups (1.7.2)

Only DNS reaches the app. A lookup for a blocked site is answered on the phone;
every other one is forwarded unchanged. `UpstreamOrder` decides to whom: the
DNS servers of the current network first, for every lookup. Only when none of
them answers within 2.5 s each is the lookup sent to Quad9 (9.9.9.9,
149.112.112.112, 2620:fe::fe, 2620:fe::9), which is what keeps the phone
working on a network whose own DNS is silent. So that such a network does not
cost a round of timeouts on every lookup, Quad9 is then asked first for one
minute; after that one lookup tries the network's servers again, and an answer
from them, or a change of network, puts them back in front at once. Quad9 is
never kept as the preferred server. Unit tests: `./gradlew testDebugUnitTest`.

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
