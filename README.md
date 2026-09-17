# Social Detox

Fork of [SocialBlocker](https://github.com/funkypitt/SocialBlocker) with the
password gate removed. Same DNS-based blocking for YouTube, Instagram and
TikTok with the same scheduling — but no parental-control password to start,
stop or open the app.

The trade-off is intentional: this version is meant to be used as a
**self-imposed digital detox tool**, not as a parental control. You can lift
it any time you want — the friction is the discoverability and the conscious
moment of toggling it, not a secret password.

If you need an actual lock (e.g. for a child's device), use the original
SocialBlocker repository.

## What's different

- `PasswordActivity` removed.
- `MainActivity` is now the launcher activity (Android opens straight to the
  blocking controls).
- VPN notification's tap-action goes to `MainActivity` (previously
  `PasswordActivity`).
- Application id changed to `com.parentcontrol.socialblocker.detox` so this
  fork coexists with the original on the same device.
- Display name: **Social Detox**.

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
an app update.

## Install

Available from `funkypitt`'s personal F-Droid repository — see the
installation instructions at <https://gallaz.ch/eink>.

## Crédits / Credits

Basé sur / Based on [SocialBlocker](https://github.com/funkypitt/SocialBlocker) by Pierre Gallaz, GPL-3.0-only.

© 2026 Pierre Gallaz. Développé avec [Claude Code](https://claude.com/claude-code) (Anthropic).
Licence GPL-3.0-only, voir `LICENSE`.

© 2026 Pierre Gallaz. Developed with [Claude Code](https://claude.com/claude-code) (Anthropic).
GPL-3.0-only licence, see `LICENSE`.
