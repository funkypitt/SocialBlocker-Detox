# Device checklist — section quotas (family build)

Build: `./gradlew assembleFamilleRelease` → `gallaz-sign` → install over the public app (same
id, higher version code). Unit tests first: `./gradlew testFamilleDebugUnitTest` (63 tests).
Watch the service with `adb logcat -s DetoxSections`.

Tick each line on the child's phone (or a phone with WhatsApp and CapCut signed in).

## Setup

- [ ] `adb shell pm list packages | grep -E "whatsapp|lemon"` shows `com.whatsapp` and
      `com.lemon.lvoverseas` (if CapCut has another id, add it to the rules file: RULES.md).
- [ ] App opens; *⋯ › sections with a quota* exists; the two apps and their sections are listed.
- [ ] *set a parent PIN*: twice the digits; the row now reads *change the PIN*, *remove the PIN*.
- [ ] Leave the app, come back after more than a minute: the PIN screen. A wrong PIN says so and
      waits two seconds; five wrong ones wait thirty.
- [ ] Right PIN: the usual home. The widget's switch, while blocking is on, opens the app on the
      PIN instead of stopping.
- [ ] *accessibility service: off* → tap → Android's accessibility settings → *Reader's Detox —
      sections* → allow. Back in the app the row reads *running* within two seconds.
- [ ] Allow the app to run in the background (settings) and, with Family Link, lock the
      accessibility settings and the uninstalling of apps.

## Discovery (first time, and after each WhatsApp or CapCut update)

- [ ] *discovery: off* → tap → *on until HH:MM*.
- [ ] Walk the screens listed in RULES.md (*Screens to capture*). Come back: *N screens
      captured*, N > 0.
- [ ] *export the captures…* → file saved where chosen → *share this file…* → the share sheet.
- [ ] *delete the captures* → *no capture yet*. Discovery stops by itself after ten minutes.
- [ ] Read the captures; confirm or correct the markers in the rules file; *import a rules
      file…* → *rules imported: version N*; the rules row says *imported*. An invalid file is
      refused with a message and the rules in use stay. *back to the built-in rules* works.

## Counting

- [ ] WhatsApp, *Updates and channels* on, 2 minutes (week and weekend). Open WhatsApp on
      *Chats* for a minute: the app's *minutes today* stays at 0.
- [ ] Open *Updates*, stay 30 s, go to *Chats* 30 s, back to *Updates* 30 s: about 1 minute today
      (the figure is refreshed when the stretch closes or every 30 s).
- [ ] Screen off while in *Updates* for a minute: nothing more is counted.
- [ ] A chat opened from *Updates* (tap a contact's status, reply): the time in the chat is not
      counted; the time back in *Updates* is.
- [ ] Switch to another app with *Updates* in front: the count stops within about two seconds.
- [ ] CapCut: same four checks with *Templates / For you* versus the editor.

## Quota

- [ ] Quota 2 minutes; stay in *Updates*: at 2 minutes the toast *Updates and channels: quota
      reached for today* and WhatsApp is back on *Chats* (or one step back).
- [ ] Tap *Updates* again: pushed back at once, the toast again. Tap it five times quickly: at
      most three pushes in ten seconds, then only the toast for thirty seconds (no fight, no
      loop); after that the push works again.
- [ ] A channel opened from a notification with the quota spent: pushed out too.
- [ ] Quota 0 minutes: the section is closed all day, pushed out at the first look.
- [ ] Quota 7 minutes, warning on: at 2 minutes left the toast *… 2 minutes left today*, once.
      Warning off: no toast.
- [ ] Weekend figure differs from the week figure and applies on Saturday/Sunday.
- [ ] Chats, calls, the CapCut editor stay open whatever the quota.

## Midnight and persistence

- [ ] Phone clock near 23:59 with a stretch open across midnight: *minutes today* goes back to
      the minutes since midnight; the quota is available again.
- [ ] Reboot: the service comes back by itself; the day's minutes are kept.
- [ ] Force-stop the app (settings › apps): the service is off — the app notifies *section
      quotas not kept* when next opened, after a boot, or within two minutes while the VPN runs,
      and the row shows *off since HH:MM*. Re-enable: the row reads *running*.
- [ ] Switch the service off in the accessibility settings: the notification arrives at once.

## Privacy

- [ ] Discovery off: `adb shell run-as com.parentcontrol.socialblocker.detox ls files/discovery`
      is empty after *delete the captures*; `logcat -s DetoxSections` shows section keys and
      counts only, never a text from the screen.
- [ ] Discovery on: the capture files hold view ids, classes and labels cut at 40 characters.

## Public build unchanged

- [ ] `assemblePubliqueRelease`: no *sections* row, no PIN, no accessibility service declared
      (`aapt dump xmltree … AndroidManifest.xml | grep -i accessib` finds nothing).
