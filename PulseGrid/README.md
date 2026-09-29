# PulseGrid (Android)

In-game overlay: tap an edge handle and two angled panels slide in from the left and right
while the middle of the screen stays clear. Home button (top-left) closes it.

## Get an installable APK from your browser (no PC needed)
This project includes a GitHub Actions workflow that builds the APK on Google's own
build servers — you just upload the folder and download the result.

1. Go to **github.com**, make a free account if you don't have one.
2. Click **+ > New repository**, name it `PulseGrid`, keep it Public, click **Create repository**.
3. On the new repo's page click **uploading an existing file**, then drag in *everything inside*
   this `PulseGrid` folder (including the hidden `.github` folder — on desktop, unzip first,
   select all files with Ctrl/Cmd+A, and drag them in together). Commit the upload.
4. Click the **Actions** tab. A run called "Build PulseGrid APK" starts automatically — wait
   about 3–5 minutes for the green check.
5. Open that run, scroll to **Artifacts**, download **PulseGrid-debug-apk** — it's a zip
   containing `app-debug.apk`.
6. On your phone, open that apk file (via a file manager or after AirDrop/USB/Drive transfer).
   Android will ask to allow installs from this source once — allow it, then install.

## Build & install
1. Install **Android Studio** (Koala or newer).
2. `File > Open` and choose this `PulseGrid` folder. Let Gradle sync (it downloads Gradle 8.7 itself).
3. On your phone: enable **Developer options > USB debugging**, plug in, press **Run**.
   (Or `Build > Build APK(s)` and copy `app/build/outputs/apk/debug/app-debug.apk` to the phone.)
4. Open **PulseGrid**, tap *Open settings* for **Display over other apps**, allow it. Optionally allow
   **Usage access** (only needed for the Recent Apps list).
5. Tap **Start overlay**, open a game, tap the small arrow tab on the left or right edge.

## What really works (no root)
| Feature | Status |
|---|---|
| Side panels over games, home/close, edge handles | Works |
| Crosshair (4 styles, colour, size, opacity) | Works, touch-through, drawn at screen centre |
| Filters | Colour *tint* overlay (warm, cool, night, amber). Not true saturation/greyscale |
| Recent apps (tap to launch) | Works after granting Usage access |
| Volume slider | Works |
| Wi-Fi / Bluetooth | Shows real on/off state; tapping opens the system panel (Android 10+ blocks apps from toggling them) |
| Battery temp, battery %, thermal status | Works |
| Green / amber / red performance colours | Driven by battery temperature + system thermal status. Tap the status pill to preview each state |
| CPU / GPU clock gauges | Reads sysfs. Many phones block this for normal apps, then it shows **N/A** |

## What cannot be done without root or system privileges
Forcing CPU/GPU performance modes, fan control, refresh-rate switching, per-game FPS counters,
touch-sensitivity tuning, and system-wide saturation/greyscale filters. Those need a rooted
device or a manufacturer-signed app (which is how RedMagic does it).

## Notes
- Some ROMs (Xiaomi, Oppo, Vivo, Samsung) also need "Autostart" / "Battery: no restrictions" for the service to survive.
- Android 12+ limits how see-through overlay windows can be; the tint alphas here stay under that limit.
- Project not compiled in the authoring environment: if Gradle reports an error, send it over and it's a quick fix.
