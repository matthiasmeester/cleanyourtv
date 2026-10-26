package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** A passive, local-only view of currently playing media; never sends playback commands. */
final class NowPlayingBadge {
    private final Context context;
    private final MediaSessionManager sessions;
    private final ComponentName access;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<MediaController> controllers = new ArrayList<>();
    private final LinearLayout bubble;
    private final TextView title, artist;
    private boolean listening;
    private final MediaSessionManager.OnActiveSessionsChangedListener changed = this::bind;
    private final MediaController.Callback playback = new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(PlaybackState state) { render(); }
        @Override public void onMetadataChanged(MediaMetadata metadata) { render(); }
        @Override public void onSessionDestroyed() { refresh(); }
    };

    NowPlayingBadge(Context context, FrameLayout root) {
        this.context = context;
        sessions = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        access = new ComponentName(context, NowPlayingAccess.class);
        bubble = new LinearLayout(context);
        bubble.setOrientation(LinearLayout.HORIZONTAL);
        bubble.setGravity(Gravity.CENTER_VERTICAL);
        bubble.setPadding(dp(8), dp(7), dp(10), dp(7));
        bubble.setFocusable(false);
        GradientDrawable glass = new GradientDrawable();
        glass.setColor(0x8020262D);
        glass.setCornerRadius(dp(13));
        glass.setStroke(dp(1), 0x33FFFFFF);
        bubble.setBackground(glass);
        LinearLayout lines = new LinearLayout(context);
        lines.setOrientation(LinearLayout.VERTICAL);
        title = line(11, 0xF2FFFFFF);
        artist = line(9, 0xB3FFFFFF);
        lines.addView(title);
        lines.addView(artist);
        title.setMaxWidth(dp(155));
        artist.setMaxWidth(dp(155));
        bubble.addView(lines, new LinearLayout.LayoutParams(-2, -2));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.LEFT);
        params.bottomMargin = dp(22);
        params.leftMargin = dp(28);
        bubble.setVisibility(View.GONE);
        root.addView(bubble, params);
    }

    private int dp(float value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }

    private TextView line(int size, int color) {
        TextView text = new TextView(context);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setTypeface(UiFont.get(context, R.string.font_regular));
        return text;
    }

    void start() {
        stop();
        if (sessions == null) return;
        try {
            sessions.addOnActiveSessionsChangedListener(changed, access, handler);
            listening = true;
            refresh();
        } catch (SecurityException unavailable) { stop(); }
    }

    void stop() {
        if (listening && sessions != null) sessions.removeOnActiveSessionsChangedListener(changed);
        listening = false;
        for (MediaController controller : controllers) controller.unregisterCallback(playback);
        controllers.clear();
        bubble.setVisibility(View.GONE);
    }

    private void refresh() {
        if (!listening) return;
        try { bind(sessions.getActiveSessions(access)); }
        catch (SecurityException revoked) { stop(); }
    }

    private void bind(List<MediaController> active) {
        if (!listening) return;
        for (MediaController controller : controllers) controller.unregisterCallback(playback);
        controllers.clear();
        if (active != null) controllers.addAll(active);
        for (MediaController controller : controllers) controller.registerCallback(playback, handler);
        render();
    }

    private void render() {
        for (MediaController controller : controllers) {
            PlaybackState state = controller.getPlaybackState();
            if (state == null || state.getState() != PlaybackState.STATE_PLAYING) continue;
            MediaMetadata metadata = controller.getMetadata();
            CharSequence track = metadata == null ? null : metadata.getDescription().getTitle();
            CharSequence performer = metadata == null ? null : metadata.getText(MediaMetadata.METADATA_KEY_ARTIST);
            if (TextUtils.isEmpty(performer) && metadata != null) performer = metadata.getDescription().getSubtitle();
            title.setText(TextUtils.isEmpty(track) ? "Now playing" : track);
            artist.setText(TextUtils.isEmpty(performer) ? appName(controller.getPackageName()) : performer);
            bubble.setContentDescription("Now playing: " + title.getText() + ", " + artist.getText());
            bubble.setVisibility(View.VISIBLE);
            return;
        }
        bubble.setVisibility(View.GONE);
    }

    private CharSequence appName(String name) {
        try { return context.getPackageManager().getApplicationLabel(context.getPackageManager().getApplicationInfo(name, 0)); }
        catch (android.content.pm.PackageManager.NameNotFoundException missing) { return "Now playing"; }
    }
}
