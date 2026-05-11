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

Nothing else: same DNS VPN service, same scheduler, same UI.

## Install

Available from `funkypitt`'s personal F-Droid repository — see the
installation instructions at <https://gallaz.ch/eink>.
