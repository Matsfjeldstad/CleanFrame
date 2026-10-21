# CleanFrame for Android TV: the shell

A Home app that runs the CleanFrame UI in `../app` full screen in a WebView.
Built and tested on a Xiaomi TV Stick 4K (Android TV 11).

## How it works

- `MainActivity` serves `../app` from APK assets at `https://cleanframe.local/`.
  The page is laid out at 1920 CSS px and zoomed to the screen (`width=1920` viewport,
  wide viewport and overview mode), so the 1920x1080 stage fits any density.
- The remote's Back key becomes Escape and OK becomes Enter. Pressing Home while
  CleanFrame is open returns to the root of Home.
- `Bridge` is `window.CleanFrameAndroid`, used by `app/device.js`:

  | Method | Does |
  | --- | --- |
  | `apps()` | Installed launchable apps, with icon and mark URLs |
  | `launch(pkg)` | Opens an app |
  | `appInfo(pkg)` | The system App info screen |
  | `settings()` | Android TV Settings |
  | `screensaver()` | Starts the system screensaver, or opens its settings |
  | `stockHome()` | Opens another installed Home app |
  | `network()` | `{type, level, online}` for the Wi-Fi indicator |
  | `is24Hour()` | The TV's 12/24-hour setting |

  Action methods return `""` on success or an error message the UI shows.
- Icons are served as `/icon/<package>.png` and marks as `/mark/<package>.png`.
  An app without a bundled SVG gets a white mark made from its adaptive icon's
  foreground, or from its logo separated from a flat background. If neither works,
  the UI shows the full-colour icon.

## Install

Needs JDK 17, the Android SDK and network debugging on the TV
(Developer options → USB debugging). Turn off any VPN on your computer.

```sh
TV_HOST=192.168.0.67 android/install.sh
```

This builds a debug APK, installs it, sets it as Home and opens it.
Installing while you're watching something doesn't interrupt it; opening it does.

## Debug

Attach Chrome DevTools to the live WebView:

```sh
adb forward tcp:9333 localabstract:webview_devtools_remote_$(adb shell pidof no.matsfjeldstad.cleanframe)
```

Then open `chrome://inspect`, or connect over CDP at `http://127.0.0.1:9333`.
`ShoreQA.where()` and `ShoreQA.state()` describe the current view and focus.
