package com.gathin.tvhome;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.Map;
import static org.junit.Assert.*;

public class WallpaperCacheTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    private File sized(File parent, String name, long size, long modified) throws Exception {
        File file = new File(parent, name);
        try (RandomAccessFile stream = new RandomAccessFile(file, "rw")) { stream.setLength(size); }
        assertTrue(file.setLastModified(modified));
        return file;
    }

    @Test public void urlPolicyRejectsCredentialsPortsAndLookalikeHosts() {
        assertTrue(WallpaperCache.safeUrl("https://upload.wikimedia.org/wikipedia/commons/a/a1/test.jpg"));
        assertTrue(WallpaperCache.safeUrl("https://thumb.wikimedia.org/wikipedia/commons/thumb/a/a1/a.jpg/2560px-a.jpg"));
        for (String url : Arrays.asList("http://upload.wikimedia.org/wikipedia/a.jpg",
                "https://upload.wikimedia.org.evil.test/wikipedia/a.jpg",
                "https://user:secret@upload.wikimedia.org/wikipedia/a.jpg",
                "https://upload.wikimedia.org:444/wikipedia/a.jpg", "file:///tmp/photo.jpg",
                "https://upload.wikimedia.org/private/photo.jpg", "https://upload.wikimedia.org/wikipedia/a.jpg#x")) {
            assertFalse(url, WallpaperCache.safeUrl(url));
        }
    }

    @Test public void catalogOnlyAcceptsSafeCompleteRowsAndFirstDuplicateWins() {
        String url = "https://upload.wikimedia.org/wikipedia/commons/a/a1/a.jpg";
        Map<String, WallpaperCache.Entry> entries = WallpaperCache.parse(Arrays.asList(
                "# comment", "day-001.jpg\t" + url + "\t\tday",
                "day-001.jpg\t" + url + "\t\tnight",
                "../escape.jpg\t" + url + "\t\tday", "x.jpg\t" + url + "\tbad-hash\tday",
                "y.jpg\t" + url + "\t\tdusk", "z.jpg\t" + url,
                "good.jpg\t" + url + "\t" + new String(new char[64]).replace('\0', 'A') + "\tnight"));
        assertEquals(2, entries.size());
        assertEquals("day", entries.get("day-001.jpg").period);
        assertEquals(64, entries.get("good.jpg").hash.length());
        assertFalse(WallpaperCache.safeName(".hidden.jpg"));
        assertFalse(WallpaperCache.safeName("a..jpg"));
    }

    @Test public void quotaCountsSeedsPartialsAndEvictsOldestButNotPinned() throws Exception {
        File seeds = folder.newFolder("wallpapers"), cache = folder.newFolder("wallpaper-cache");
        File seed = sized(seeds, "seed.jpg", 15_000_000, 1000);
        File old = sized(cache, "old.jpg", 25_000_000, 2000);
        File pin = sized(cache, "pin.jpg", 30_000_000, 1000);
        File recent = sized(cache, "recent.jpg", 20_000_000, 3000);
        File partial = sized(cache, "work.jpg.part", 5_000_000, 4000);
        assertTrue(WallpaperCache.reserve(seeds, cache, "pin.jpg", 20_000_000));
        assertFalse(old.exists());
        assertTrue(seed.exists()); assertTrue(pin.exists()); assertTrue(recent.exists()); assertTrue(partial.exists());
        assertTrue(WallpaperCache.bytes(seeds, true) + WallpaperCache.bytes(cache, false) + 20_000_000 <= WallpaperCache.BUDGET);
    }

    @Test public void unknownFilesCountButAreNeverDeleted() throws Exception {
        File seeds = folder.newFolder("seed"), cache = folder.newFolder("cache");
        File unknown = sized(cache, "personal.txt", 90_000_000, 1000);
        assertFalse(WallpaperCache.reserve(seeds, cache, null, 20_000_000));
        assertTrue(unknown.exists());
        assertFalse(WallpaperCache.reserve(seeds, cache, null, 20_000_001));
    }

    @Test public void directFileRejectsOutsideFiles() throws Exception {
        File owned = folder.newFolder("owned"), outside = folder.newFile("outside.jpg");
        assertFalse(WallpaperCache.directFile(owned, outside));
    }

    @Test public void serverBackoffHonorsSecondsAndBounds() {
        long now = 1_000_000L;
        assertEquals(now + 60_000, WallpaperRemote.retryAfter(null, now));
        assertEquals(now + 60_000, WallpaperRemote.retryAfter("0", now));
        assertEquals(now + 120_000, WallpaperRemote.retryAfter("120", now));
        assertEquals(now + 86_400_000, WallpaperRemote.retryAfter("999999", now));
        assertEquals(now + 60_000, WallpaperRemote.retryAfter("invalid", now));
    }

    @Test public void nullThemeDisablesWithoutReadingRelativeFiles() {
        WallpaperRemote remote = new WallpaperRemote(null);
        assertFalse(remote.enabled());
        assertTrue(remote.files().isEmpty());
        assertTrue(remote.cachedFiles().isEmpty());
        assertNull(remote.resolve(new File("photo.jpg"), null));
        remote.close();
    }

    @Test public void startupOnlyCleansOwnedPartialNames() throws Exception {
        File cache = folder.newFolder("cleanup");
        File partial = sized(cache, "day-001.jpg.part", 10, 1000);
        File unknown = sized(cache, "personal.part", 10, 1000);
        File image = sized(cache, "day-002.jpg", 10, 1000);
        WallpaperCache.cleanPartials(cache);
        assertFalse(partial.exists()); assertTrue(unknown.exists()); assertTrue(image.exists());
    }

    @Test public void coldStartEnforcesQuotaEvenWithoutDownload() throws Exception {
        File theme = folder.newFolder("theme"), seeds = new File(theme, "wallpapers"), cache = new File(theme, "wallpaper-cache");
        assertTrue(seeds.mkdir()); assertTrue(cache.mkdir());
        java.nio.file.Files.write(new File(seeds, "catalog.tsv").toPath(), Arrays.asList(
                "new.jpg\thttps://upload.wikimedia.org/wikipedia/commons/a/a1/a.jpg\t\tday"), java.nio.charset.StandardCharsets.UTF_8);
        File old = sized(cache, "old.jpg", 60_000_000, 1000);
        File newer = sized(cache, "newer.jpg", 50_000_000, 2000);
        WallpaperRemote remote = new WallpaperRemote(theme);
        assertTrue(remote.cachedFiles().isEmpty());
        assertFalse(old.exists()); assertTrue(newer.exists());
        assertTrue(WallpaperCache.bytes(cache, false) <= WallpaperCache.BUDGET);
    }
}
