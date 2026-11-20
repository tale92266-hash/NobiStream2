# NobiStream 2

NobiStream 2 is a fresh Jetpack Compose rebuild of NobiStream (v1.0.0, code 1,
package `com.nobi.nobistream2` — installs alongside the old NobiStream, which
is untouched). Sign in with your own Telegram account, paste a group/channel
link, and watch all its videos in one list — YouTube-style streaming,
buffering and cache, resume, Next/Previous, quality filter, Smart Filter
(each episode once, best quality copy).

## What's new vs NobiStream (old)

- **Full Compose UI** — Material 3 Expressive, graphite-dark YouTube-like
  theme, reusable component library first (AppTopBar, PrimaryButton,
  VideoCard, GroupCard, QualityChip, Empty/Error/Skeleton states).
- **Bottom nav**: Home / Watch Later / Downloads.
- **Watch Later** (Room) and **Downloads** (real TDLib downloads, offline
  playback, determinate progress, Pause / Retry).
- **Picture-in-Picture**, **Sleep Timer** (15/30/60 min), **Mini-Player**
  above the bottom nav, **Player Queue** sheet, **Group Details** screen,
  **Sort sheet** (Newest / Oldest / Title A–Z / Largest / Highest Quality),
  **Filter sheet**, list/grid toggle, search with recent searches.
- **Snackbars with Undo**, not Toasts. Loading = shimmer skeletons,
  proper empty/error states with Retry on every screen.

## Engine (reused, proven)

TDLib 1.8.64 (MTProto, own login), Media3/ExoPlayer 1.8.0 streaming from
TDLib downloads with seek support, Room (resume positions, saved groups,
Watch Later, downloads), QualityParser + SmartFilter, silent auto-resume
(one `setMediaItem(item, savedPosition)` + `prepare()`, never
prepare-at-0-then-seek), stall retry with backoff (spinner + background
retry, error screen only after 10 consecutive failures).

## Stack (pinned)

| Component | Version |
|---|---|
| Gradle | 8.11.1 |
| Android Gradle Plugin (AGP) | 8.9.2 |
| Kotlin | 2.2.21 |
| compileSdk / targetSdk | 35 |
| minSdk | 26 |
| Media3 (ExoPlayer) | 1.8.0 |
| Room | 2.8.5 |
| TDLib | 1.8.64 via `io.github.tdlib-android:core/ktx:0.1.1` |
| Compose BOM | 2025.09.00 (Material 3) |
| Navigation Compose | 2.9.4 |
| Coil | 2.7.0 |

## Credentials (never in source)

Telegram `api_id` / `api_hash` are injected ONLY at build time from GitHub
Actions secrets `NOBISTREAM2_API_ID` / `NOBISTREAM2_API_HASH` as BuildConfig
fields, and the user can override them in the app's Settings screen
(EncryptedSharedPreferences, on the phone only, hash masked).

## Signing (fixed key, install-over updates)

Fixed keystore `~/workspace/NobiStream2-signing/nobistream2.p12`
(alias `nobistream2`, SHA-256 `BF:33:9E:C2:92:5A:27:8D:DB:A1:C6:F1:7D:CE:3A:A8:B8:79:A5:5E:E2:D6:85:75:AD:98:80:DE:62:A5:0F:15`,
till 2054-04-07) — never committed; password in
`nobistream2.p12.pass.txt` next to it. CI secrets (names only):

| Secret | Value |
|---|---|
| `NOBISTREAM2_KEYSTORE_B64` | Base64 of `nobistream2.p12` |
| `NOBISTREAM2_KEYSTORE_PASSWORD` | Keystore password |
| `NOBISTREAM2_KEY_ALIAS` | `nobistream2` |
| `NOBISTREAM2_KEY_PASSWORD` | Key password (same as keystore password) |

## How to build

**GitHub Actions:** push to `main` — the workflow builds a complete debug
APK (no R8) and uploads it as artifact `nobistream2-v1-debug-apk`
(kept 30 days).

**On your phone (ACS):** copy the project folder to your phone, open it in
ACS (Android Code Studio), press Run. `gradle/wrapper/gradle-wrapper.jar`
is included (ACS import hangs without it).

## Spec

`~/workspace/NobiStream2-spec.md` (Appendix A, A1–A21) is the master spec.
UI reference repos: `~/workspace/Android-UI-Reference.md`.
