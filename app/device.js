/* Android TV controls through the WebView shell's CleanFrameAndroid bridge. The browser preview has no bridge. */
(function () {
  'use strict';
  /* Packages drawn with a bundled SVG mark in assets/icons/. */
  var BRANDS = {
    'com.google.android.youtube.tv': 'youtube', 'com.spotify.tv.android': 'spotify', 'com.wbd.stream': 'hbomax',
    'com.formulaone.production': 'f1', 'com.nordvpn.android': 'nordvpn', 'com.android.vending': 'googleplay',
    'com.stremio.one': 'stremio', 'com.netflix.ninja': 'netflix', 'com.disney.disneyplus': 'disneyplus',
    'com.amazon.amazonvideo.livingroom': 'primevideo', 'com.apple.atve.androidtv.appletv': 'appletv'
  };
  function api() { return window.CleanFrameAndroid; }
  function unavailable(done) {
    done({ message: 'Open CleanFrame on the TV to use this control.', detail: 'The web preview cannot control the TV.' });
  }
  /* Bridge calls are synchronous; defer the callback so callers see the same async shape as before. */
  function call(method, arg, done) {
    if (!api()) { unavailable(done); return; }
    setTimeout(function () {
      var err;
      try { err = arg === undefined ? api()[method]() : api()[method](arg); }
      catch (e) { err = e && e.message || 'The bridge did not respond.'; }
      done(err ? { message: 'The TV could not open this screen.', detail: String(err) } : null);
    }, 0);
  }
  function launchApp(id, done) { call('launch', id, done); }
  function art(done) { call('screensaver', undefined, done); }
  function home(done) { call('stockHome', undefined, done); }
  function settings(done) { call('settings', undefined, done); }
  function appInfo(id, done) { call('appInfo', id, done); }
  function apps(done) {
    if (!api()) { unavailable(done); return; }
    setTimeout(function () {
      var list;
      try { list = JSON.parse(api().apps()); }
      catch (e) { done({ message: 'Could not read installed apps.', detail: e && e.message || '' }); return; }
      done(null, list.map(function (a) {
        return { appId: a.appId, name: a.name, brand: BRANDS[a.appId] || '', icon: a.icon || '', markSrc: a.mark || '', metrics: a.metrics || null };
      }));
    }, 0);
  }
  function clock24() {
    try { return !!(api() && api().is24Hour()); } catch (e) { return false; }
  }
  window.FrameDevice = { native: !!api(), launchApp: launchApp, art: art, home: home, settings: settings, appInfo: appInfo, apps: apps, clock24: clock24 };
}());
