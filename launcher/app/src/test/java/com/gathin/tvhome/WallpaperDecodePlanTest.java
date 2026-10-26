package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class WallpaperDecodePlanTest {
    @Test public void threeByTwoFourKPhotoCropsBeforeAllocation() {
        WallpaperDecodePlan p = WallpaperDecodePlan.create(3840, 2560, 3840, 2160, false);
        assertArrayEquals(new int[]{0, 200, 3840, 2160, 1}, values(p));
    }

    @Test public void fourKScreenKeepsEveryPixelOfUhdPhoto() {
        assertArrayEquals(new int[]{0, 0, 3840, 2160, 1},
                values(WallpaperDecodePlan.create(3840, 2160, 3840, 2160, false)));
    }

    @Test public void fullHdUsesHalfResolutionUhdDecode() {
        assertArrayEquals(new int[]{0, 200, 3840, 2160, 2},
                values(WallpaperDecodePlan.create(3840, 2560, 1920, 1080, false)));
    }

    @Test public void eightKSourceNeverAllocatesEightKBitmap() {
        WallpaperDecodePlan p = WallpaperDecodePlan.create(7680, 4320, 3840, 2160, false);
        assertEquals(2, p.sample);
        assertTrue(WallpaperDecodePlan.sampledPixels(p.width, p.height, p.sample) <= WallpaperSize.MAX_PIXELS);
    }

    @Test public void panoramaCropsSidesSymmetrically() {
        assertArrayEquals(new int[]{1280, 0, 3840, 2160, 1},
                values(WallpaperDecodePlan.create(6400, 2160, 3840, 2160, false)));
    }

    @Test public void containPreservesPortraitAndBoundsDecode() {
        WallpaperDecodePlan p = WallpaperDecodePlan.create(3000, 5000, 3840, 2160, true);
        assertArrayEquals(new int[]{0, 0, 3000, 5000, 2}, values(p));
        assertArrayEquals(new int[]{1296, 2160}, WallpaperDecodePlan.fit(1500, 2500, 3840, 2160));
    }

    @Test public void containCannotDecodeAbovePixelBudgetEvenOnHugeScreen() {
        WallpaperDecodePlan p = WallpaperDecodePlan.create(3840, 2560, 7680, 4320, true);
        assertEquals(2, p.sample);
        assertEquals(0, p.left);
        assertEquals(0, p.top);
        assertEquals(2560, p.height);
    }

    @Test public void smallPhotoIsNeverUpscaled() {
        WallpaperDecodePlan p = WallpaperDecodePlan.create(1280, 720, 3840, 2160, false);
        assertEquals(1, p.sample);
        assertArrayEquals(new int[]{1280, 720}, WallpaperDecodePlan.fit(1280, 720, 3840, 2160));
    }

    @Test public void oddDimensionsStayInsideSourceAndUnderBudget() {
        WallpaperDecodePlan p = WallpaperDecodePlan.create(6001, 4001, 3840, 2160, false);
        assertTrue(p.left >= 0 && p.top >= 0);
        assertTrue(p.left + p.width <= 6001 && p.top + p.height <= 4001);
        assertTrue(WallpaperDecodePlan.sampledPixels(p.width, p.height, p.sample) <= WallpaperSize.MAX_PIXELS);
    }

    @Test public void extremeAndInvalidHeadersAreRejectedBeforeAllocation() {
        assertNull(WallpaperDecodePlan.create(10000, 10000, 1920, 1080, false));
        assertNull(WallpaperDecodePlan.create(Integer.MAX_VALUE, Integer.MAX_VALUE, 1920, 1080, false));
        assertNull(WallpaperDecodePlan.create(0, 2000, 1920, 1080, false));
        assertNull(WallpaperDecodePlan.create(3000, 2000, 0, 1080, false));
        assertNull(WallpaperDecodePlan.create(3000, 2000, 1920, -1, false));
    }

    private static int[] values(WallpaperDecodePlan p) {
        assertNotNull(p);
        return new int[]{p.left, p.top, p.width, p.height, p.sample};
    }
}
