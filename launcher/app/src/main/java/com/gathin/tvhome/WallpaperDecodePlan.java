package com.gathin.tvhome;

/** Pure decode geometry: bound allocations before asking Android to decode any pixels. */
final class WallpaperDecodePlan {
    static final long MAX_SOURCE_PIXELS = 40_000_000L;
    final int left, top, width, height, sample;

    private WallpaperDecodePlan(int left, int top, int width, int height, int sample) {
        this.left = left;
        this.top = top;
        this.width = width;
        this.height = height;
        this.sample = sample;
    }

    static WallpaperDecodePlan create(int width, int height, int screenW, int screenH, boolean contain) {
        if (width <= 0 || height <= 0 || screenW <= 0 || screenH <= 0
                || (long) width * height > MAX_SOURCE_PIXELS) return null;
        int regionW = width, regionH = height;
        if (!contain) {
            if ((long) width * screenH > (long) height * screenW)
                regionW = Math.max(1, (int) ((long) height * screenW / screenH));
            else regionH = Math.max(1, (int) ((long) width * screenH / screenW));
        }
        int[] target = fit(regionW, regionH, screenW, screenH);
        int sample = 1;
        while (regionW / (sample * 2L) >= target[0] && regionH / (sample * 2L) >= target[1]) sample *= 2;
        // Round up conservatively: codecs can round odd region dimensions upwards.
        while (sampledPixels(regionW, regionH, sample) > WallpaperSize.MAX_PIXELS) sample *= 2;
        return new WallpaperDecodePlan((width - regionW) / 2, (height - regionH) / 2,
                regionW, regionH, sample);
    }

    static long sampledPixels(int width, int height, int sample) {
        return ((width + (long) sample - 1) / sample) * ((height + (long) sample - 1) / sample);
    }

    /** Fit inside the screen without ever enlarging the decoded image. */
    static int[] fit(int width, int height, int screenW, int screenH) {
        double factor = Math.min(1d, Math.min(screenW / (double) width, screenH / (double) height));
        return new int[]{Math.max(1, (int) Math.floor(width * factor)),
                Math.max(1, (int) Math.floor(height * factor))};
    }
}
