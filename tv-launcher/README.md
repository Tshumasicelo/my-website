# ASPECTS TV

A lightweight custom home screen (launcher) for Android TV, themed to match
[ASPECTS](https://tshumasicelo.github.io/my-website/).

Built for a **SINOTEC SWTV-20AE** — Skyworth 4K "T32" chassis, Android TV OS 11
(API 30), kernel 4.19, **1 GB RAM** — so every decision here favours low memory and
fast cold start over visual gimmicks.

---

## What it does

| | |
|---|---|
| 🕐 **Header** | Big clock + date, plus live chips for network & IP, RAM, free storage, CPU temp and uptime |
| ⭐ **Favourites** | Pin the apps you actually use to the top row |
| 📺 **TV Apps** | Every proper leanback app (Netflix, YouTube, Daily Play…) with its real 16:9 banner |
| 📱 **All Apps** | **Sideloaded phone apps that the stock Android TV launcher refuses to show** |
| ⚙️ **System** | One-press tiles into Settings, Wi-Fi, Display, Apps, Storage, Date & time and Developer options |
| 🎨 **Themes** | Six accent colours; the whole UI repaints instantly |

## Controls

| Button | Action |
|---|---|
| **D-pad** | Move between cards and rows |
| **OK** | Launch |
| **OK (hold)** | Open / Pin / App info / Uninstall |
| **Back** | Jump back to the top of the home screen |

---

## Installing on the TV

### Option A — Downloader app (no cable, easiest)

You already have **Downloader** installed. Open it and enter:

```
https://github.com/Tshumasicelo/my-website/releases/download/tv-latest/aspects-tv.apk
```

Then **Install**. CI republishes that same tag on every build, so the URL
never changes.

> If Android blocks it, allow **Downloader** under
> *Settings → Apps → Special app access → Install unknown apps*.

### Option B — ADB over the network

```bash
adb connect <TV-IP>:5555
adb install -r aspects-tv.apk
```

(The TV's IP is shown in the ASPECTS TV header once it is running, and under
*Settings → Network* before that. Enable **USB/network debugging** in Developer options first.)

### Making it your home screen

Open **ASPECTS TV → System → Home app**, then pick ASPECTS TV.
On Android TV 11 you may instead find it under
*Settings → Device Preferences → Home screen*.

To go back to the stock launcher, choose it in that same picker — or just
uninstall ASPECTS TV and the TV falls back automatically. **You cannot lock
yourself out.**

---

## Building it yourself

Every push to `tv-launcher/**` builds the APK in GitHub Actions and republishes
the `tv-latest` release. To build locally you need the Android SDK (platform 34):

```bash
cd tv-launcher
./gradlew assembleRelease
# app/build/outputs/apk/release/app-release.apk
```

### Optional: a stable signing key

Without secrets the release APK is signed with the local debug key, which works
fine for sideloading but **changes between CI runs** — so an update may ask you
to uninstall first. To get a permanent signature, generate a key:

```bash
keytool -genkeypair -v -keystore signing.jks -storetype PKCS12 \
  -alias aspectstv -keyalg RSA -keysize 2048 -validity 10950 \
  -dname "CN=ASPECTS TV, O=ASPECTS, C=ZA"
base64 -w0 signing.jks   # copy this
```

Then add four **repository secrets** (Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | the base64 blob printed above |
| `KEYSTORE_PASSWORD` | your store password |
| `KEY_ALIAS` | `aspectstv` |
| `KEY_PASSWORD` | your key password |

Keep `signing.jks` off the repo — this one is public.

---

## Notes for a 1 GB box

- **No Jetpack Compose, no image library.** Plain views + `RecyclerView`, two
  AndroidX dependencies total. The APK is around a megabyte.
- **Icons live in a bounded `LruCache`**, never on the model objects, and are
  decoded on a background thread — drawables are a launcher's biggest memory cost.
- **The header refreshes every 10 s, not every second**, to keep a passively
  cooled SoC idle.
- **`itemAnimator` is disabled** and rows use fixed-size cards, so scrolling
  never triggers a re-layout pass.

### The Android 11 gotcha this app had to solve

Since API 30, `queryIntentActivities()` returns **nothing** unless your manifest
declares which apps you care about. A launcher written for Android 10 shows an
empty screen on this TV. The fix is the `<queries>` block in
`AndroidManifest.xml`, which declares the two `MAIN` launcher intents — the
scoped alternative to the blanket `QUERY_ALL_PACKAGES` permission.
