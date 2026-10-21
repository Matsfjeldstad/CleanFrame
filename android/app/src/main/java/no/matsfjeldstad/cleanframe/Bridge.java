package no.matsfjeldstad.cleanframe;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The page's window.CleanFrameAndroid. Action methods return "" on success or an error message.
 * Icons are served to the page as /icon/<package>.png, and monochrome marks as /mark/<package>.png.
 */
final class Bridge {
    private static final String STOCK_LAUNCHER = "com.google.android.tvlauncher";
    private static final int ICON = 192;
    private static final int MARK = 240;

    private final Activity activity;
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    private static final class Entry {
        long updated;
        byte[] icon;
        byte[] mark;
        JSONObject metrics;
    }

    Bridge(Activity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public String apps() throws JSONException {
        PackageManager pm = activity.getPackageManager();
        JSONArray out = new JSONArray();
        Set<String> seen = new HashSet<>();
        seen.add(activity.getPackageName());
        for (String category : new String[] {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent probe = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo ri : pm.queryIntentActivities(probe, 0)) {
                String pkg = ri.activityInfo.packageName;
                if (!seen.add(pkg)) continue;
                Entry e = entry(pm, pkg, ri);
                JSONObject a = new JSONObject();
                a.put("appId", pkg);
                a.put("name", ri.loadLabel(pm).toString());
                if (e.icon != null) a.put("icon", "/icon/" + pkg + ".png");
                if (e.mark != null) {
                    a.put("mark", "/mark/" + pkg + ".png");
                    a.put("metrics", e.metrics);
                }
                out.put(a);
            }
        }
        return out.toString();
    }

    @JavascriptInterface
    public String launch(String pkg) {
        PackageManager pm = activity.getPackageManager();
        Intent intent = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (intent == null) intent = pm.getLaunchIntentForPackage(pkg);
        if (intent == null) return "This app is not installed.";
        return start(intent);
    }

    @JavascriptInterface
    public String settings() {
        return start(new Intent(Settings.ACTION_SETTINGS));
    }

    /** The system App info screen: force stop, storage, permissions, uninstall. */
    @JavascriptInterface
    public String appInfo(String pkg) {
        return start(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)));
    }

    @JavascriptInterface
    public String screensaver() {
        Intent dream = new Intent(Intent.ACTION_MAIN)
                .setComponent(new ComponentName("com.android.systemui", "com.android.systemui.Somnambulator"));
        return start(dream).isEmpty() ? "" : start(new Intent(Settings.ACTION_DREAM_SETTINGS));
    }

    /** Opens another Home app: the stock launcher if enabled, otherwise any other installed one. */
    @JavascriptInterface
    public String stockHome() {
        PackageManager pm = activity.getPackageManager();
        Intent probe = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ComponentName pick = null;
        for (ResolveInfo ri : pm.queryIntentActivities(probe, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(activity.getPackageName()) || ri.priority < 0) continue;
            ComponentName c = new ComponentName(pkg, ri.activityInfo.name);
            if (pick == null || pkg.equals(STOCK_LAUNCHER)) pick = c;
        }
        if (pick == null) return "No other Home app is installed.";
        return start(new Intent(Intent.ACTION_MAIN).setComponent(pick));
    }

    @JavascriptInterface
    public boolean is24Hour() {
        return DateFormat.is24HourFormat(activity);
    }

    /** PNG bytes for "icon/<pkg>.png" or "mark/<pkg>.png", built by apps(). */
    byte[] image(String path) {
        int slash = path.indexOf('/');
        if (!path.endsWith(".png") || slash < 0) return null;
        Entry e = cache.get(path.substring(slash + 1, path.length() - 4));
        if (e == null) return null;
        return path.startsWith("icon/") ? e.icon : e.mark;
    }

    private String start(Intent intent) {
        try {
            activity.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return "";
        } catch (ActivityNotFoundException | SecurityException err) {
            return err.getMessage() == null ? "The TV could not open this." : err.getMessage();
        }
    }

    private Entry entry(PackageManager pm, String pkg, ResolveInfo ri) {
        long updated;
        try {
            PackageInfo info = pm.getPackageInfo(pkg, 0);
            updated = info.lastUpdateTime;
        } catch (PackageManager.NameNotFoundException gone) {
            updated = 0;
        }
        Entry e = cache.get(pkg);
        if (e != null && e.updated == updated) return e;
        e = new Entry();
        e.updated = updated;
        Drawable d = ri.loadIcon(pm);
        e.icon = png(render(d));
        Bitmap mark = d instanceof AdaptiveIconDrawable ? mark(((AdaptiveIconDrawable) d).getForeground(), e) : null;
        if (mark == null) mark = glyph(d, e);
        if (mark != null) e.mark = png(mark);
        cache.put(pkg, e);
        return e;
    }

    private static Bitmap render(Drawable d) {
        Bitmap b = Bitmap.createBitmap(ICON, ICON, Bitmap.Config.ARGB_8888);
        d.setBounds(0, 0, ICON, ICON);
        d.draw(new Canvas(b));
        return b;
    }

    /**
     * Turns an adaptive icon's foreground layer into a white glyph like the bundled SVG marks.
     * Returns null when the layer is a solid plate rather than a logo.
     */
    private static Bitmap mark(Drawable fg, Entry e) {
        if (fg == null) return null;
        // Layers are 108dp with a 72dp visible centre; draw 1.5x so the centre fills the bitmap.
        int full = MARK * 3 / 2, off = (full - MARK) / 2;
        Bitmap b = Bitmap.createBitmap(MARK, MARK, Bitmap.Config.ARGB_8888);
        fg.setBounds(-off, -off, full - off, full - off);
        fg.draw(new Canvas(b));
        int[] px = new int[MARK * MARK];
        b.getPixels(px, 0, MARK, 0, 0, MARK, MARK);
        // Drop faint shadows, then keep only the shape.
        for (int i = 0; i < px.length; i++) px[i] = Math.max(0, Math.min(255, ((px[i] >>> 24) - 48) * 255 / 160));
        return finish(b, px, e);
    }

    /**
     * For a flat icon (a logo on a plain plate), keeps the pixels that differ from the plate's colour.
     * Returns null when the plate is not one even colour.
     */
    private static Bitmap glyph(Drawable d, Entry e) {
        Bitmap b = Bitmap.createBitmap(MARK, MARK, Bitmap.Config.ARGB_8888);
        d.setBounds(0, 0, MARK, MARK);
        d.draw(new Canvas(b));
        int[] px = new int[MARK * MARK];
        b.getPixels(px, 0, MARK, 0, 0, MARK, MARK);
        // Sample the plate on a ring near the edge, skipping corners a round or squircle mask cut away.
        int inset = MARK / 12, n = 0, total = 4 * (MARK - 2 * inset);
        long r = 0, g = 0, bl = 0;
        int[] ring = new int[total];
        for (int t = inset; t < MARK - inset; t++) {
            int[] at = {inset * MARK + t, (MARK - 1 - inset) * MARK + t, t * MARK + inset, t * MARK + MARK - 1 - inset};
            for (int i : at) {
                if ((px[i] >>> 24) < 250) continue;
                ring[n++] = px[i];
                r += (px[i] >> 16) & 255; g += (px[i] >> 8) & 255; bl += px[i] & 255;
            }
        }
        if (n < total / 2) return null;
        int pr = (int) (r / n), pg = (int) (g / n), pb = (int) (bl / n), off = 0;
        for (int i = 0; i < n; i++) if (distance(ring[i], pr, pg, pb) > 24) off++;
        if (off > n / 10) return null;
        for (int i = 0; i < px.length; i++) {
            int a = (px[i] >>> 24) < 128 ? 0 : Math.max(0, Math.min(255, (distance(px[i], pr, pg, pb) - 40) * 255 / 80));
            px[i] = a;
        }
        return finish(b, px, e);
    }

    private static int distance(int c, int r, int g, int b) {
        return Math.max(Math.abs(((c >> 16) & 255) - r), Math.max(Math.abs(((c >> 8) & 255) - g), Math.abs((c & 255) - b)));
    }

    /** px holds alphas; writes the white glyph into b and its metrics into e. */
    private static Bitmap finish(Bitmap b, int[] px, Entry e) {
        int left = MARK, top = MARK, right = -1, bottom = -1;
        double ink = 0;
        for (int i = 0; i < px.length; i++) {
            int a = px[i];
            px[i] = (a << 24) | 0xFFFFFF;
            ink += a / 255.0;
            if (a > 40) {
                int x = i % MARK, y = i / MARK;
                left = Math.min(left, x); top = Math.min(top, y);
                right = Math.max(right, x); bottom = Math.max(bottom, y);
            }
        }
        if (right < left) return null;
        int w = right - left + 1, h = bottom - top + 1;
        double fill = ink / (w * h);
        if (fill > 0.8 || (w * h > MARK * MARK * 0.8 && fill > 0.6)) return null;
        b.setPixels(px, 0, MARK, 0, 0, MARK, MARK);
        try {
            e.metrics = new JSONObject().put("n", MARK).put("x", left).put("y", top).put("w", w).put("h", h).put("fill", fill);
        } catch (JSONException impossible) {
            return null;
        }
        return b;
    }

    private static byte[] png(Bitmap b) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.PNG, 100, out);
        return out.toByteArray();
    }
}
