# CleanFrame for Android TV

A WebView shell that runs the CleanFrame UI in `../app` as an Android TV Home app.
Built for a Xiaomi TV Stick 4K (Android 11), but nothing in it is specific to that stick.

- `MainActivity` serves `../app` from APK assets at `https://cleanframe.local/` and maps
  the remote's Back to Escape and OK to Enter.
- `Bridge` is `window.CleanFrameAndroid`, used by `app/device.js`: list and launch apps,
  open Settings, start the screensaver, open another launcher, read the 12/24-hour setting.
- Apps without a bundled SVG mark get one made from their icon: the adaptive icon's
  foreground layer, or the logo separated from a flat plate. Others fall back to the icon.

## Install

Needs JDK 17, the Android SDK, and network debugging on the TV
(Developer options → USB debugging). Turn off any VPN on the Mac.

```sh
TV_HOST=192.168.0.67 android/install.sh
```

Debug: `adb forward tcp:9333 localabstract:webview_devtools_remote_$(adb shell pidof no.matsfjeldstad.cleanframe)`,
then open `chrome://inspect` or connect over CDP at `http://127.0.0.1:9333`.
