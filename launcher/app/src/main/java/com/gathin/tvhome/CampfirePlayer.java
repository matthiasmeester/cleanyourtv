package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;
import java.io.File;

/** Local video with its own volume, without audio-focus requests or media-session controls. */
final class CampfirePlayer implements TextureView.SurfaceTextureListener {
    final TextureView view;
    private final Context context;
    private MediaPlayer player;
    private MediaPlayer audioPlayer;
    private boolean separateAudio;
    private Surface surface;
    private boolean enabled;
    private int videoWidth, videoHeight;
    private int volume;

    CampfirePlayer(Context context, FrameLayout root) {
        this.context = context;
        volume = Math.max(0, Math.min(100, context.getSharedPreferences("ambient_mode", Context.MODE_PRIVATE).getInt("volume", 0)));
        view = new TextureView(context);
        // The video is opaque and center-cropped to cover the screen. Avoid blending
        // every decoded frame with the wallpaper retained underneath it.
        view.setOpaque(true);
        view.setFocusable(false);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        view.setSurfaceTextureListener(this);
        view.setVisibility(View.GONE);
        root.addView(view, 1, new FrameLayout.LayoutParams(-1, -1));
    }

    boolean available() {
        File dir = Config.dir(context);
        return dir != null && new File(dir, "campfire.mp4").isFile();
    }

    void start() {
        enabled = true;
        view.setKeepScreenOn(true);
        view.setVisibility(View.VISIBLE);
        if (view.isAvailable()) prepare(view.getSurfaceTexture());
    }

    void stop() {
        enabled = false;
        view.setKeepScreenOn(false);
        release();
        view.setVisibility(View.GONE);
    }

    private void release() {
        if (audioPlayer != null) { audioPlayer.release(); audioPlayer = null; }
        if (player != null) { player.release(); player = null; }
        if (surface != null) { surface.release(); surface = null; }
    }

    private void prepare(SurfaceTexture texture) {
        if (!enabled || player != null || texture == null) return;
        try {
            MediaPlayer next = new MediaPlayer();
            player = next;
            surface = new Surface(texture);
            next.setSurface(surface);
            next.setDataSource(new File(Config.dir(context), "campfire.mp4").getAbsolutePath());
            File audio = new File(Config.dir(context), "campfire-audio.mp3");
            separateAudio = audio.isFile();
            next.setVolume(separateAudio ? 0f : volume / 100f, separateAudio ? 0f : volume / 100f);
            next.setLooping(true);
            next.setOnVideoSizeChangedListener((mp, width, height) -> {
                videoWidth = width; videoHeight = height; fit();
            });
            next.setOnPreparedListener(mp -> {
                if (enabled && player == mp) {
                    mp.start();
                    if (separateAudio) prepareAudio(audio);
                }
            });
            next.setOnErrorListener((mp, what, extra) -> { failed(); return true; });
            next.prepareAsync();
        } catch (Exception error) { failed(); }
    }

    /** Independent loop: a long sound recording must not restart with the short video. */
    private void prepareAudio(File file) {
        try {
            MediaPlayer next = new MediaPlayer();
            audioPlayer = next;
            next.setDataSource(file.getAbsolutePath());
            next.setVolume(volume / 100f, volume / 100f);
            next.setLooping(true);
            next.setOnPreparedListener(mp -> { if (enabled && audioPlayer == mp) mp.start(); });
            next.setOnErrorListener((mp, what, extra) -> { audioFailed(); return true; });
            next.prepareAsync();
        } catch (Exception error) { audioFailed(); }
    }

    private void audioFailed() {
        if (audioPlayer != null) { audioPlayer.release(); audioPlayer = null; }
        Toast.makeText(context, "Campfire audio unavailable. Reinstall the audio file.", Toast.LENGTH_LONG).show();
    }

    private void failed() {
        stop();
        Toast.makeText(context, "Campfire video unavailable. Reinstall the profile media.", Toast.LENGTH_LONG).show();
    }

    int volume() { return volume; }

    void setVolume(int value) {
        volume = Math.max(0, Math.min(100, value));
        context.getSharedPreferences("ambient_mode", Context.MODE_PRIVATE).edit().putInt("volume", volume).apply();
        if (player != null && !separateAudio) player.setVolume(volume / 100f, volume / 100f);
        if (audioPlayer != null) audioPlayer.setVolume(volume / 100f, volume / 100f);
    }

    String sourceCredit() {
        File dir = Config.dir(context);
        File credit = dir == null ? null : new File(dir, "campfire-custom.txt");
        if (credit != null && credit.isFile()) {
            if (credit.length() > 0 && credit.length() <= 4096) {
                try {
                    String text = new String(java.nio.file.Files.readAllBytes(credit.toPath()),
                            java.nio.charset.StandardCharsets.UTF_8).trim();
                    if (!text.isEmpty()) return text;
                } catch (java.io.IOException ignored) { }
            }
            return "Personal campfire video";
        }
        return "Kaminfeuer — Larvis — CC BY-SA 4.0 (silent H.264 conversion)\nhttps://commons.wikimedia.org/wiki/File:Kaminfeuer.webm";
    }

    private void fit() {
        if (videoWidth <= 0 || videoHeight <= 0 || view.getWidth() == 0 || view.getHeight() == 0) return;
        float scale = Math.max((float) view.getWidth() / videoWidth, (float) view.getHeight() / videoHeight);
        Matrix matrix = new Matrix();
        matrix.setScale(videoWidth * scale / view.getWidth(), videoHeight * scale / view.getHeight(),
                view.getWidth() / 2f, view.getHeight() / 2f);
        view.setTransform(matrix);
    }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) { prepare(texture); }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) { fit(); }
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) { release(); return true; }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture texture) { }
}
