package no.matsfjeldstad.cleanframe;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Home activity: a full-screen WebView running the CleanFrame UI from assets.
 * The remote's Back and OK keys are translated to the Escape and Enter keys the UI listens for.
 */
public class MainActivity extends Activity {
    static final String HOST = "cleanframe.local";
    private static final String ORIGIN = "https://" + HOST + "/";
    private static final Map<String, String> MIME = new HashMap<>();
    static {
        MIME.put("html", "text/html"); MIME.put("js", "text/javascript"); MIME.put("css", "text/css");
        MIME.put("svg", "image/svg+xml"); MIME.put("png", "image/png"); MIME.put("jpg", "image/jpeg");
        MIME.put("webp", "image/webp"); MIME.put("json", "application/json");
        MIME.put("woff2", "font/woff2"); MIME.put("ttf", "font/ttf");
    }

    private WebView web;
    private Bridge bridge;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        WebView.setWebContentsDebuggingEnabled((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0);
        bridge = new Bridge(this);
        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setTextZoom(100);
        // The UI is a fixed 1920x1080 stage; lay it out at 1920 CSS px and zoom out to the screen.
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        web.addJavascriptInterface(bridge, "CleanFrameAndroid");
        web.setWebViewClient(new Client());
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        setContentView(web);
        web.requestFocus();
        web.loadUrl(ORIGIN + "index.html");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home pressed while CleanFrame is showing (or returning to it): close any panel.
        if (web != null && intent != null && intent.hasCategory(Intent.CATEGORY_HOME)) {
            web.evaluateJavascript("window.ShoreQA && ShoreQA.scene && ShoreQA.scene('home')", null);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        web.requestFocus();
    }

    @Override
    protected void onPause() {
        web.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_BACK:
                return web.dispatchKeyEvent(remap(e, KeyEvent.KEYCODE_ESCAPE));
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                return web.dispatchKeyEvent(remap(e, KeyEvent.KEYCODE_ENTER));
            default:
                return super.dispatchKeyEvent(e);
        }
    }

    private static KeyEvent remap(KeyEvent e, int code) {
        return new KeyEvent(e.getDownTime(), e.getEventTime(), e.getAction(), code, e.getRepeatCount(),
                e.getMetaState(), e.getDeviceId(), e.getScanCode(), e.getFlags(), e.getSource());
    }

    private final class Client extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return !HOST.equals(request.getUrl().getHost());
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri url = request.getUrl();
            if (!HOST.equals(url.getHost())) return notFound();
            String path = url.getPath() == null ? "" : url.getPath().replaceFirst("^/+", "");
            if (path.startsWith("icon/") || path.startsWith("mark/")) {
                byte[] png = bridge.image(path);
                return png == null ? notFound() : response("image/png", new ByteArrayInputStream(png));
            }
            if (path.isEmpty() || path.contains("..")) return notFound();
            try {
                return response(mime(path), getAssets().open(path));
            } catch (IOException missing) {
                return notFound();
            }
        }
    }

    private static String mime(String path) {
        String ext = path.substring(path.lastIndexOf('.') + 1).toLowerCase();
        String type = MIME.get(ext);
        return type == null ? "application/octet-stream" : type;
    }

    private static WebResourceResponse response(String mime, InputStream body) {
        WebResourceResponse r = new WebResourceResponse(mime, mime.startsWith("text/") ? "utf-8" : null, body);
        Map<String, String> headers = new HashMap<>();
        headers.put("Access-Control-Allow-Origin", ORIGIN.substring(0, ORIGIN.length() - 1));
        r.setResponseHeaders(headers);
        return r;
    }

    private static WebResourceResponse notFound() {
        return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", null, new ByteArrayInputStream(new byte[0]));
    }
}
