package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Rect;
import java.io.File;
import java.io.IOException;

/** Worker-thread decoder: crop before allocation, retaining 4K detail without a full oversized bitmap. */
final class WallpaperDecode {
    private WallpaperDecode() { }

    static Bitmap decode(File file, int screenW, int screenH, Bitmap.Config config, int dimColor) {
        return decode(file, screenW, screenH, config, dimColor, false);
    }

    /** contain preserves the entire photograph; the caller must display it with FIT_CENTER. */
    static Bitmap decode(File file, int screenW, int screenH, Bitmap.Config config, int dimColor, boolean contain) {
        if (file == null) return null;
        BitmapRegionDecoder decoder = null;
        Bitmap decoded = null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), bounds);
            WallpaperDecodePlan plan = WallpaperDecodePlan.create(bounds.outWidth, bounds.outHeight,
                    screenW, screenH, contain);
            if (plan == null) return null;
            decoder = BitmapRegionDecoder.newInstance(file.getPath(), false);
            if (decoder == null) return null;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = plan.sample;
            options.inScaled = false; // RegionDecoder ignores density scaling; resize the bounded result below.
            options.inPreferredConfig = config == null || config == Bitmap.Config.HARDWARE
                    ? Bitmap.Config.ARGB_8888 : config;
            options.inMutable = dimColor != 0;
            decoded = decoder.decodeRegion(new Rect(plan.left, plan.top,
                    plan.left + plan.width, plan.top + plan.height), options);
            if (decoded == null || !WallpaperSize.accepted(decoded.getWidth(), decoded.getHeight())) return null;
            int[] target = WallpaperDecodePlan.fit(decoded.getWidth(), decoded.getHeight(), screenW, screenH);
            if (target[0] != decoded.getWidth() || target[1] != decoded.getHeight()) {
                Bitmap scaled = Bitmap.createScaledBitmap(decoded, target[0], target[1], true);
                if (scaled != decoded) { decoded.recycle(); decoded = scaled; }
            }
            if (dimColor != 0) {
                // Scaling may return an immutable bitmap even though the decoder requested a mutable one.
                if (!decoded.isMutable()) {
                    Bitmap mutable = decoded.copy(Bitmap.Config.ARGB_8888, true);
                    if (mutable == null) return null;
                    decoded.recycle();
                    decoded = mutable;
                }
                new Canvas(decoded).drawColor(dimColor);
            }
            // Release software pixels after a successful GPU copy. A failed GPU allocation may still use
            // the already bounded software bitmap, rather than dropping an otherwise valid photograph.
            Bitmap result = decoded;
            try {
                Bitmap hardware = decoded.copy(Bitmap.Config.HARDWARE, false);
                if (hardware != null) { decoded.recycle(); result = hardware; }
            } catch (RuntimeException | OutOfMemoryError unavailable) { /* retain software result */ }
            decoded = null; // ownership transfers to the caller
            return result;
        } catch (IOException | RuntimeException | OutOfMemoryError unavailable) {
            return null;
        } finally {
            if (decoded != null) decoded.recycle();
            if (decoder != null) decoder.recycle();
        }
    }
}
