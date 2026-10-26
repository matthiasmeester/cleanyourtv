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

/** Local silent video, without audio-focus requests or any media-session controls. */
final class CampfirePlayer implements TextureView.SurfaceTextureListener {
    final TextureView view;
    private final Context context;
    private MediaPlayer player;
    private Surface surface;
    private boolean enabled;
    private int videoWidth, videoHeight;

    CampfirePlayer(Context context, FrameLayout root) {
        this.context = context;
        view = new TextureView(context);
        view.setOpaque(false);
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
            next.setVolume(0f, 0f);
            next.setLooping(true);
            next.setOnVideoSizeChangedListener((mp, width, height) -> {
                videoWidth = width; videoHeight = height; fit();
            });
            next.setOnPreparedListener(mp -> { if (enabled && player == mp) mp.start(); });
            next.setOnErrorListener((mp, what, extra) -> { failed(); return true; });
            next.prepareAsync();
        } catch (Exception error) { failed(); }
    }

    private void failed() {
        stop();
        Toast.makeText(context, "Campfire video unavailable. Reinstall the profile media.", Toast.LENGTH_LONG).show();
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
