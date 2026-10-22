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

## Section quotas and parent PIN — family build (1.11.0)

Two Gradle flavours since 1.11.0: `publique` (the F-Droid app, unchanged in substance) and
`famille` (version code 1000 + public, `-famille` suffix, same id and key), for a child's
phone. The `famille` manifest alone declares `sections.SectionGuardService`, an
`AccessibilityService` (`res/xml/section_guard.xml`: window state, window content, view
selected; `flagReportViewIds`, `canRetrieveWindowContent`, 300 ms notification timeout), the
`<queries>` for WhatsApp and CapCut, and the FileProvider of the exports. `BuildConfig.FAMILLE`
gates the *sections* row and the PIN in the UI.

`sections/` holds the logic, pure and tested (`SectionTimerTest`, `RulesTest`, `QuotaTest`,
`PinTest`, 33 tests):

- `Rules` parses the JSON (bundled `assets/section_rules.json`, or `files/section_rules.json`
  imported by the parent; `RulesRepo` chooses and reloads). A section is in front when one
  `any` selector holds and no `none` does; selectors AND their keys on one node (id, class,
  text, desc per language, selected, clickable) plus `activity` on the window; `*` globs, case
  ignored. `escape` names the nodes to tap to leave. Format and update procedure: RULES.md.
- `SectionTimer` is the stopwatch: `observe(sectionOrNull, now)` on every look. Entry counts
  from the first observation; leaving (or switching) is confirmed only once the new state has
  held for 1 s (anti-flicker) and then counts up to the moment the section was first seen
  gone; `screenOff` closes at once; a stretch across midnight is split by day; `onCommit`
  reports each closed stretch for persistence; `seed` restores the day's totals at start.
- `Quota` (week / weekend minutes, 0 = closed, warning in the last five minutes), `ActionGuard`
  (three actions per ten seconds, then thirty seconds of message only), `Pin` (salted SHA-256).

`SectionGuardService` restricts its `packageNames` at run time to the rules' packages (so a
rules file can add an app), throttles content-changed events to 400 ms, walks the window
(≤ 400 nodes, depth ≤ 30) into a `Snapshot`, asks the rules, feeds the timer. Because a
restricted service hears nothing when the child leaves for another app, it also looks once a
second while a section runs; that tick is also what spends a quota on a still screen. The day's
total is written every 30 s and at each close (`SectionPrefs`, SharedPreferences `sections`,
keys by day so another day's figure is never read). Quota spent: toast, escape tap (nearest
clickable ancestor) or GLOBAL_ACTION_BACK, under the guard. Service off: `onUnbind` notifies
the parent; a force-stop gives no `onUnbind`, so `checkOff` runs at app open, after a boot and
from the budget's clock. Discovery mode (parent, ten minutes) writes one text file per screen
under `files/discovery/` (texts cut at 40 characters, 220 lines, 60 files kept, identical bursts
skipped); the parent exports them as `readers-detox-discovery.txt`, saved then shared.

The PIN: `MainActivity` shows `PinScreen` instead of everything while a PIN is set and the
activity was away more than a minute (the VPN consent and the file pickers stop it for
seconds, not a relock). The widget's off tap opens the app instead of stopping.

Seen on the emulator (API 36, Settings app as the watched package through an imported rules
file): quota 0 → pushed out of *Network & internet* in under half a second; quota 1 minute over
two stretches → left at 61 s, total stored; discovery dumps readable; PIN set, asked at the
next open, wrong PIN refused; force-stop → notification *section quotas not kept*. Not seen:
WhatsApp and CapCut themselves (the bundled rules are drafts to confirm with discovery), the
midnight rollover on a device, Family Link.

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
