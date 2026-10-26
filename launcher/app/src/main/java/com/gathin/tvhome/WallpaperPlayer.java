package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Displays and rotates a theme's local wallpaper images while Home is visible. */
final class WallpaperPlayer {
    private final FrameLayout root;
    private final ImageView image;
    private final int backgroundColor;
    private final List<File> files;
    private final WallpaperRemote remote;
    private final boolean online;
    private final long interval;
    private final String order;
    private final SharedPreferences preferences;
    private final ExecutorService decoder = Executors.newSingleThreadExecutor(task -> new Thread(() -> {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
        task.run();
    }, "WallpaperIO"));
    private final Random random = new Random();
    private final int width;
    /** config wallpaper_true_color: ARGB_8888 instead of RGB_565. */
    private final Bitmap.Config bitmapConfig;
    private final int height;
    /** wallpaper_dim as an ARGB black, painted into each picture once instead of a full-screen shade view. */
    private final int dimColor;
    /** The picture on screen, released once the next one has been drawn. */
    private Bitmap current;
    private boolean active;
    private boolean closed;
    private boolean loading;
    private long retryAfter;
    private final Runnable retry = this::update;
    private String displayed = "";
    /** wallpaper_captions: the shown picture's name from wallpapers/captions.txt; null when off or there are none. */
    private final TextView caption;
    private final Map<String, String> names;
    private final boolean briefCaption;
    private final Runnable hideLocation = this::hideLocation;
    /** Photo attribution shown only in More, keyed by the wallpaper's local filename. */
    private final Map<String, String> sources = new HashMap<>();
    /** wallpaper_moods: the collection's groups by time of day; empty rotates through all of it. */
    private final Map<String, Set<String>> moods;
    /** config.xml latitude and longitude for the sun's times, and today's {sunrise, sunset} once worked out. */
    private final String latitude, longitude;
    private LocalDate sunDay;
    private int[] sun;

    WallpaperPlayer(Context context, FrameLayout root, Config config) {
        this.root = root;
        backgroundColor = config.backgroundColor;
        remote = new WallpaperRemote(Config.dir(context));
        online = config.wallpaperEnabled && remote.enabled()
                && context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE)
                .getString(Config.WALLPAPER_CHOICE_KEY, null) == null;
        files = new ArrayList<>(online ? remote.files() : config.wallpapers);
        interval = config.wallpaperRotationMinutes * 60_000L;
        bitmapConfig = config.wallpaperTrueColor ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565;
        // The dim is painted into the picture (decode), so no full-screen shade layer is drawn on every frame.
        dimColor = config.wallpaperDim > 0 ? (config.wallpaperDim * 255 / 100) << 24 : 0;
        order = config.wallpaperOrder;
        preferences = context.getSharedPreferences("wallpaper_rotation", Context.MODE_PRIVATE);
        moods = config.wallpaperMoods;
        latitude = moods.isEmpty() ? "" : context.getString(R.string.latitude);
        longitude = moods.isEmpty() ? "" : context.getString(R.string.longitude);
        // Playback state belongs to one collection. Changing the collection starts from its first
        // picture instead of resuming a filename that belonged to the previous one.
        File catalogue = new File(new File(Config.dir(context), "wallpapers"), "catalog.tsv");
        String collection = online ? "online:" + catalogue.length() + ":" + catalogue.lastModified() : collectionKey(files);
        if (!collection.equals(preferences.getString("collection", ""))) {
            preferences.edit().putString("collection", collection)
                    .remove("image").remove("changed").apply();
        }
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        width = metrics.widthPixels;
        height = metrics.heightPixels;
        image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        for (String entry : context.getResources().getStringArray(R.array.wallpaper_sources)) {
            int split = entry.indexOf('=');
            if (split <= 0) continue;
            String file = entry.substring(0, split).trim();
            String credit = entry.substring(split + 1).trim();
            if (!file.isEmpty() && !credit.isEmpty()) sources.put(file, credit);
        }
        briefCaption = context.getResources().getBoolean(R.bool.brief_wallpaper_location);
        names = briefCaption ? new HashMap<>() : config.wallpaperCaptions && config.wallpaperEnabled && !files.isEmpty()
                ? WallpaperCaptions.read(online ? new File(Config.dir(context), "wallpapers")
                        : files.get(0).getParentFile()) : Collections.emptyMap();
        if (briefCaption) {
            for (String entry : context.getResources().getStringArray(R.array.wallpaper_locations)) {
                int split = entry.indexOf('=');
                if (split > 0) names.put(entry.substring(0, split), entry.substring(split + 1));
            }
        }
        if (!files.isEmpty()) {
            File folder = online ? new File(Config.dir(context), "wallpapers") : files.get(0).getParentFile();
            sources.putAll(WallpaperCaptions.readCredits(folder));
            if (briefCaption) names.putAll(WallpaperCaptions.read(folder));
        }
        caption = names.isEmpty() ? null : makeCaption(context, root, briefCaption);
        if (config.wallpaperEnabled && !files.isEmpty()) {
            root.addView(image, 0, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }

    void start() {
        active = true;
        image.setVisibility(View.VISIBLE);
        if (briefCaption) showCaption(false);
        update();
    }

    void stop() {
        active = false;
        image.setVisibility(View.INVISIBLE);
        root.removeCallbacks(retry);
        root.removeCallbacks(hideLocation);
        if (briefCaption && caption != null) { caption.animate().cancel(); caption.setAlpha(0f); }
    }

    String sourceCredit() {
        String credit = sources.get(displayed);
        return credit == null ? "" : credit;
    }

    void showLocationBriefly() {
        if (briefCaption && active) showCaption(false);
    }

    /** Drops the wallpaper texture when Home is hidden and Android asks the process to free memory. */
    void releaseForMemoryPressure() {
        if (current == null) return;
        image.setImageDrawable(null);
        // The renderer may still have a queued frame that references this hardware bitmap. Drop our references and
        // let Android reclaim its native buffer after rendering releases it instead of recycling it mid-frame.
        current = null;
        displayed = "";
        root.setBackgroundColor(backgroundColor);
    }

    /** The pictures the rotation picks from now: wallpaper_moods' group for the time of day, or the whole collection. */
    private List<File> playable() {
        if (moods.isEmpty()) return files;
        LocalDateTime now = LocalDateTime.now();
        if (!now.toLocalDate().equals(sunDay)) {
            sunDay = now.toLocalDate();
            sun = WallpaperMood.sun(sunDay, latitude, longitude, ZoneId.systemDefault());
        }
        return WallpaperMood.select(files, moods, WallpaperMood.timeOfDay(now.getHour() * 60 + now.getMinute(), sun[0], sun[1]));
    }

    void close() {
        root.removeCallbacks(hideLocation);
        root.removeCallbacks(retry);
        active = false;
        closed = true;
        remote.close();
        decoder.shutdownNow();
    }

    void update() {
        if (!active || closed || loading || files.isEmpty() || System.currentTimeMillis() < retryAfter) return;
        String previous = preferences.getString("image", "");
        // A picture outside the time of day's group (the group changed) is replaced at once, not after the interval.
        List<File> playable = playable();
        File selected = WallpaperRotation.find(playable, previous);
        long changed = preferences.getLong("changed", 0);
        if (selected == null || WallpaperRotation.due(changed, System.currentTimeMillis(), interval)) {
            selected = WallpaperRotation.pick(playable, previous, order, random);
        }
        if (selected == null || selected.getName().equals(displayed)) return;
        File next = selected;
        String pinned = displayed;
        loading = true;
        decoder.execute(() -> {
            // On a cold start, show a cached photo of this mood first. Neither disk decoding nor
            // hashing/network I/O blocks the first UI frame. Never fetch the whole catalogue.
            File actual = null;
            if (online && pinned.isEmpty()) {
                List<File> cached = remote.cachedFiles();
                cached.removeIf(file -> WallpaperRotation.find(playable, file.getName()) == null);
                actual = WallpaperRotation.find(cached, previous);
                if (actual == null) actual = WallpaperRotation.pick(cached, "", order, random);
                if (actual != null) actual = remote.resolve(actual, pinned);
            }
            if (actual == null) actual = online ? remote.resolve(next, pinned) : next;
            Bitmap bitmap = actual == null ? null : decode(actual);
            String nextName = actual == null ? next.getName() : actual.getName();
            root.post(() -> {
                loading = false;
                if (closed || !active) {
                    if (bitmap != null) bitmap.recycle();
                    return;
                }
                if (bitmap == null) {
                    if (online) {
                        // Keep the current picture on network failure; try later, never spin or
                        // replace a working background with a blank/network-error screen.
                        retryAfter = System.currentTimeMillis() + 60_000L;
                        root.postDelayed(retry, 60_100L);
                    } else {
                        files.remove(next);
                        Log.w("TvHome.Wallpaper", "Skipping unreadable image: " + next.getName());
                        update();
                    }
                    return;
                }
                boolean preserveFrame = nextName.startsWith("culture-");
                image.setScaleType(preserveFrame ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER_CROP);
                image.setBackgroundColor(preserveFrame ? android.graphics.Color.BLACK : android.graphics.Color.TRANSPARENT);
                show(bitmap);
                displayed = nextName;
                showCaption(true);
                if (!displayed.equals(previous) || changed == 0) {
                    preferences.edit().putString("image", displayed)
                            .putLong("changed", System.currentTimeMillis()).apply();
                }
            });
        });
    }

    /**
     * Small light text under the top-right buttons, above everything else and never focusable. It sits where the
     * top bar ends in every layout, so it does not cover the clock, the stats line or the tiles.
     */
    private static TextView makeCaption(Context context, FrameLayout root, boolean brief) {
        float dp = context.getResources().getDisplayMetrics().density;
        TextView text = new TextView(context);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, brief ? 11 : 15);
        text.setTextColor(0xD9FFFFFF);
        text.setShadowLayer(6 * dp, 0, dp, 0xCC000000);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setMaxWidth(Math.round(520 * dp));
        text.setFocusable(false);
        text.setAlpha(0f);
        try {
            text.setTypeface(UiFont.get(context, R.string.font_regular));
        } catch (Exception ignored) {
            // the system font is fine
        }
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, (brief ? Gravity.BOTTOM : Gravity.TOP) | Gravity.END);
        params.topMargin = brief ? 0 : Math.round(110 * dp);
        params.bottomMargin = brief ? Math.round(22 * dp) : 0;
        params.setMarginEnd(Math.round((brief ? 28 : 62) * dp));
        root.addView(text, params);
        return text;
    }

    /** The displayed picture's name, faded across a change; a picture without a name hides the caption. */
    private void showCaption(boolean fade) {
        if (caption == null) return;
        if (briefCaption) {
            root.removeCallbacks(hideLocation);
            caption.animate().cancel();
            String location = names.get(displayed);
            caption.setText(location == null ? "" : location);
            caption.setAlpha(location == null ? 0f : 1f);
            root.postDelayed(hideLocation, 6_000L);
            return;
        }
        String name = names.get(displayed);
        caption.animate().cancel();
        if (name == null) {
            caption.setAlpha(0f);
            return;
        }
        caption.setContentDescription(name);
        if (!fade || caption.getAlpha() == 0f) {
            caption.setText(name);
            caption.setAlpha(1f);
            return;
        }
        caption.animate().alpha(0f).setDuration(250).withEndAction(() -> {
            caption.setText(name);
            caption.animate().alpha(1f).setDuration(350).start();
        }).start();
    }

    private void hideLocation() {
        if (caption != null) caption.animate().alpha(0f).setDuration(400).start();
    }

    /** Names plus sizes: enough to notice a different collection without hashing every image. */
    private static String collectionKey(List<File> images) {
        StringBuilder key = new StringBuilder();
        for (File file : images) key.append(file.getName()).append(':').append(file.length()).append(';');
        return key.toString();
    }

    /** Puts a picture on screen and releases the previous one after a frame has drawn the new one. */
    private void show(Bitmap bitmap) {
        Bitmap old = current;
        current = bitmap;
        // The wallpaper covers the whole root; avoid drawing the fallback colour under it.
        root.setBackground(null);
        image.setImageBitmap(bitmap);
        if (old != null && old != bitmap) image.postDelayed(old::recycle, 500);
    }

    private Bitmap decode(File file) {
        return WallpaperDecode.decode(file, width, height, bitmapConfig, dimColor,
                file.getName().startsWith("culture-"));
    }
}
