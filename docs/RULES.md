# Section rules (family build)

The family build of Reader's Detox keeps a quota of minutes a day on **sections** of an app —
WhatsApp's *Updates* tab and channels, CapCut's *Templates* tab and its *For you* feed — while
the rest of the app (chats, calls, the video editor) stays open. Which screens count as a section
is not written in the code: it is read from a JSON rules file, so that an update of WhatsApp or
CapCut that renames its views is answered by a new file, not a new build.

* Bundled file: `app/src/main/assets/section_rules.json` (the fallback, always present).
* Imported file: `files/section_rules.json` in the app's private storage, written by
  *sections › rules › import a rules file…* in the app. It replaces the bundled one as long as
  it parses; *back to the built-in rules* deletes it.
* The file in use can be exported from the same menu (`readers-detox-section-rules.json`).

## Format

```json
{
  "version": 2,
  "packages": {
    "com.whatsapp": {
      "name": "WhatsApp",
      "sections": {
        "updates": {
          "name": {"en": "Updates and channels", "fr": "Actus et chaînes"},
          "any":    [ ...selectors... ],
          "none":   [ ...selectors... ],
          "escape": [ ...selectors... ]
        }
      }
    }
  }
}
```

* `version` — an integer you bump at each change; the app shows it (*rules v2 · imported*).
* `packages` — one entry per Android package. The accessibility service listens to **these
  packages only**; a package absent from the file is never looked at.
* `sections` — one entry per section, keyed by a short id (`updates`, `templates`). The quota
  settings are stored under `package/id`, so renaming an id resets its settings.
* `name` — the label shown to the parent, per language (`en` is the fallback).
* `any` — the section is in front when **at least one** of these selectors holds.
* `none` — … **and none** of these holds (to exclude a sub-screen that shares the markers).
* `escape` — nodes to tap to leave the section once the quota is spent, tried in order
  (the *Chats* tab, the editor tab). If none is found, the service presses Back.
* Any key starting with `_` is a comment and is ignored (`"_": "…"`, `"_comment"`, `"_status"`).

### Selectors

A selector is an object whose keys are **all** required of the **same node** (AND):

| key         | matches                                                    | example                                  |
|-------------|------------------------------------------------------------|------------------------------------------|
| `id`        | the view id (`package:id/name`)                            | `"id": "com.whatsapp:id/updates_tab"`    |
| `class`     | the view class                                             | `"class": "android.widget.TextView"`     |
| `text`      | the node's text                                            | `"text": {"en": ["Updates"], "fr": ["Actus"], "pl": ["Aktualizacje"]}` |
| `desc`      | the node's content description                             | `"desc": {"en": "Chats", "fr": "Discussions"}` |
| `selected`  | the node's selected state (tabs)                           | `"selected": true`                       |
| `clickable` | the node's clickable state                                 | `"clickable": true`                      |
| `activity`  | the class name of the activity in front (window, not node) | `"activity": "*NewsletterActivity"`      |

* Patterns match the **whole** value, case ignored; `*` stands for any run of characters.
  `"id": "com.whatsapp:id/*updates*"` matches any id containing `updates`.
* `text` and `desc` take a string, a list, or an object of lists per language. Every entry of
  every language may match: the rules do not know the phone's language. Keep `en`, `fr` and
  `pl` (or whatever the phone may be set to) for every label you rely on.
* A selector with only `activity` holds for the whole window. A selector with node keys and
  `activity` requires both.
* The service compares view ids, classes, texts and descriptions **in memory, for the length of
  one look**, and keeps none of it. Only structure is used: a label equal to "Updates", never the
  content of a message.

### Which markers to prefer

1. **A tab's label, selected** (`desc` or `text` + `"selected": true`): survives most updates.
2. **An activity name** for full screens (a channel, a template page).
3. **A view id**: precise, but ids change often — this is what the discovery mode is for.

## Updating the rules when WhatsApp or CapCut change

Signs: the section is no longer counted (the day's minutes stay at 0 while the child is in it),
or the quota does not kick in, or the escape tap no longer lands on the right tab.

1. In the app (parent PIN): *sections › discovery: off — tap for ten minutes*. From now on each
   screen of a watched app is written as a text file — the tree of nodes with depth, class,
   `#id`, a short text, a (description), `[selected]`, `[clickable]` — and the activity in front.
   **Do this with the parent's own account, or on screens without private conversations**: in
   discovery mode the short texts of the screen go into the file.
2. Open WhatsApp (or CapCut) and walk through the screens listed below, two or three seconds on
   each. A screenshot of each helps to map lines to what you saw.
3. Back in the app: *N screens captured — export the captures…* saves
   `readers-detox-discovery.txt` where you choose, then offers to share it (kDrive, Telegram…).
   *Delete the captures* afterwards.
4. In the captures, find for each section the node that is only there in the section (a tab
   with `[selected]`, an activity name, a stable id) and the node to tap to leave (`[clickable]`).
   Edit the JSON, bump `version`, import it (*rules › import a rules file…*). A broken file is
   refused and the rules in use stay. The service reloads the rules at once.
5. Check with the device checklist (`docs/TEST-CHECKLIST.md`): minutes counted in the section,
   not outside; escape lands on the right tab.

### Screens to capture

WhatsApp (`com.whatsapp`; `adb shell pm list packages | grep whatsapp` confirms the package):

* home, **Chats** tab in front — the reference that must *not* match;
* home, **Updates / Actus** tab, just after tapping it;
* the Updates tab scrolled to the channels list; the *Explore / Explorer* channels screen;
* one **channel** opened;
* one **status** opened;
* **Calls** and **Communities** tabs — must not match;
* one **chat** opened — must not match.

CapCut (`com.lemon.lvoverseas`; confirm with `adb shell pm list packages | grep lemon`):

* home / **Edit** tab — the reference that must not match;
* the **Templates** tab; its **For you** feed; a template's page; search inside templates;
* the **editor** with a project open — must not match;
* the profile / inbox tabs — must not match.

With a cable instead: `adb logcat -s DetoxSections` shows the rules reloads and each quota
action; the capture files are also under the app's `files/discovery/` (debug builds:
`adb shell run-as com.parentcontrol.socialblocker.detox ls files/discovery`).
