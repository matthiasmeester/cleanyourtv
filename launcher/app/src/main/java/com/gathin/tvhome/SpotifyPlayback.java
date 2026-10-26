package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.view.KeyEvent;
import android.widget.EditText;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/** Starts a configured Spotify playlist, or resumes Spotify's existing queue using its own receiver. */
final class SpotifyPlayback {
    private static final String PACKAGE = "com.spotify.tv.android";
    private static final String RECEIVER = PACKAGE + ".util.RemoteControlReceiver";
    private static final String PLAYLIST_KEY = "spotify_playlist_uri";
    private static final String PREFERENCES = "spotify_music";
    private static final String PLAYLIST_PREFERENCE = "playlist_uri";
    private static final String START_PLAYBACK = "android.intent.extra.START_PLAYBACK";
    private static final String URI_PREFIX = "spotify:playlist:";
    private static final String LIKED_SONGS = "spotify:collection:tracks";
    private static final String URL_PREFIX = "https://open.spotify.com/playlist/";
    private static final Pattern PLAYLIST_ID = Pattern.compile("[A-Za-z0-9]{22}");
    private static final int MAX_THEME_BYTES = 128 * 1024;

    private SpotifyPlayback() { }

    static boolean isAvailable(Context context) {
        try {
            return launchIntent(context) != null;
        } catch (SecurityException unavailable) {
            return false;
        }
    }

    static void play(Context context) {
        try {
            Intent launch = launchIntent(context);
            if (launch == null) {
                showUnavailable(context);
                return;
            }
            String playlist = currentPlaylistUri(context);
            if (!playlist.isEmpty()) {
                // Verified against Spotify TV 1.83.3: its deep-link handler reads this Boolean and
                // forwards the URI to its own player with playback requested.
                context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(playlist))
                        .setPackage(PACKAGE)
                        .putExtra(START_PLAYBACK, true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            }
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Context app = context.getApplicationContext();
            new Handler(Looper.getMainLooper()).postDelayed(() -> resume(app), 800);
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            showUnavailable(context);
        }
    }

    /** Keeps a personal Daily Mix link on this TV, outside the portable theme files. */
    static void configure(Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        EditText input = new EditText(activity);
        input.setHint("Paste a Daily Mix or playlist link");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        input.setTypeface(UiFont.get(activity, R.string.font_regular));
        input.setText(currentPlaylistUri(activity));
        int padding = Math.round(24 * activity.getResources().getDisplayMetrics().density);
        input.setPadding(padding, padding / 3, padding, padding / 3);
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Spotify playlist")
                .setMessage("Leave empty to resume Spotify's current queue.")
                .setView(input)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Save", null)
                .create();
        dialog.setOnShowListener(shown -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
                String entered = input.getText().toString();
                String playlist = normalizePlaylistUri(entered);
                if (!entered.trim().isEmpty() && playlist.isEmpty()) {
                    Toast.makeText(activity, "Enter a Spotify playlist link or leave it empty.",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                // Keep an explicit empty override: clearing the personal link must resume music,
                // even if the installed theme happens to include a default playlist.
                activity.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
                        .putString(PLAYLIST_PREFERENCE, playlist).apply();
                dialog.dismiss();
            });
            input.requestFocus();
        });
        dialog.show();
    }

    /** Only Spotify receives these keys; MEDIA_PLAY cannot pause music that is already playing. */
    private static void resume(Context context) {
        if (!isAvailable(context)) {
            showUnavailable(context);
            return;
        }
        try {
            long now = SystemClock.uptimeMillis();
            ComponentName receiver = new ComponentName(PACKAGE, RECEIVER);
            context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_BUTTON).setComponent(receiver)
                    .putExtra(Intent.EXTRA_KEY_EVENT,
                            new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY, 0)));
            context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_BUTTON).setComponent(receiver)
                    .putExtra(Intent.EXTRA_KEY_EVENT,
                            new KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY, 0)));
        } catch (SecurityException unavailable) {
            showUnavailable(context);
        }
    }

    private static Intent launchIntent(Context context) {
        PackageManager packages = context.getPackageManager();
        Intent launch = packages.getLeanbackLaunchIntentForPackage(PACKAGE);
        return launch != null ? launch : packages.getLaunchIntentForPackage(PACKAGE);
    }

    /** Returns a canonical playlist URI; query parameters on shared HTTPS links are discarded. */
    static String normalizePlaylistUri(String value) {
        if (value == null) return "";
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) return "";
        }
        String link = value.trim();
        if (LIKED_SONGS.equals(link)
                || "https://open.spotify.com/collection/tracks".equals(link)) return LIKED_SONGS;
        String id;
        if (link.startsWith(URI_PREFIX)) {
            id = link.substring(URI_PREFIX.length());
        } else if (link.startsWith(URL_PREFIX)) {
            int query = link.indexOf('?');
            if (query >= 0) link = link.substring(0, query);
            id = link.substring(URL_PREFIX.length());
        } else {
            return "";
        }
        return PLAYLIST_ID.matcher(id).matches() ? URI_PREFIX + id : "";
    }

    static String currentPlaylistUri(Context context) {
        try {
            SharedPreferences preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
            if (preferences.contains(PLAYLIST_PREFERENCE)) {
                return normalizePlaylistUri(preferences.getString(PLAYLIST_PREFERENCE, ""));
            }
        } catch (ClassCastException | SecurityException unreadable) {
            // An unreadable preference leaves the bounded local theme as the fallback.
        }
        return playlistUri(context);
    }

    private static String playlistUri(Context context) {
        try {
            File dir = Config.dir(context);
            File file = new File(dir != null ? dir : context.getFilesDir(), Config.FILE_NAME);
            long length = file.length();
            if (!file.isFile() || length <= 0 || length >= MAX_THEME_BYTES) return LIKED_SONGS;
            byte[] bytes = new byte[(int) length];
            int count = 0;
            try (InputStream in = new FileInputStream(file)) {
                int read;
                while (count < bytes.length && (read = in.read(bytes, count, bytes.length - count)) > 0) {
                    count += read;
                }
                if (in.read() != -1) return ""; // A file changed during the read; keep the bounded fallback.
            }
            String json = new String(bytes, 0, count, StandardCharsets.UTF_8);
            if (json.startsWith("\uFEFF")) json = json.substring(1);
            return normalizePlaylistUri(new JSONObject(json).optString(PLAYLIST_KEY, LIKED_SONGS));
        } catch (Exception unreadable) {
            return "";
        }
    }

    private static void showUnavailable(Context context) {
        Toast.makeText(context, context.getString(R.string.cant_open, "Spotify"), Toast.LENGTH_SHORT).show();
    }
}
