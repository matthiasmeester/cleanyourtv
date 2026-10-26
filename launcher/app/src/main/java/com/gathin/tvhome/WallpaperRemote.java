package com.gathin.tvhome;

import android.graphics.BitmapFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

/** Optional public-photo downloads. Call resolve/cachedFiles only on the wallpaper decoder worker. */
final class WallpaperRemote {
    // Activity recreation can briefly overlap decoder workers. Serialize quota accounting and partial cleanup.
    private static final Object CACHE_LOCK = new Object();
    private final File seeds, cache;
    private final Map<String, WallpaperCache.Entry> entries;
    private final Map<String, Long> failedUntil = new HashMap<>();
    private final Map<String, String> checked = new HashMap<>();
    private volatile boolean closed;
    private volatile HttpsURLConnection connection;
    private long serverBackoff;
    private boolean prepared;

    WallpaperRemote(File themeDir) {
        if (themeDir == null) {
            seeds = null;
            cache = null;
            entries = Collections.emptyMap();
            return;
        }
        seeds = new File(themeDir, "wallpapers");
        cache = new File(themeDir, "wallpaper-cache");
        Map<String, WallpaperCache.Entry> parsed = Collections.emptyMap();
        File catalogue = new File(seeds, "catalog.tsv");
        try {
            if (WallpaperCache.directFile(seeds, catalogue) && catalogue.length() <= 1024 * 1024) {
                parsed = WallpaperCache.parse(Files.readAllLines(catalogue.toPath(), StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) { /* Invalid/missing catalogue leaves the local-only path intact. */ }
        entries = parsed;
    }

    boolean enabled() { return !entries.isEmpty(); }

    List<File> files() {
        List<File> files = new ArrayList<>();
        for (String name : entries.keySet()) files.add(new File(cache, name));
        return files;
    }

    String period(String name) {
        WallpaperCache.Entry entry = entries.get(name);
        return entry == null ? "" : entry.period;
    }

    /** Validated local fallbacks; never starts a network request. */
    synchronized List<File> cachedFiles() {
        synchronized (CACHE_LOCK) { return cachedFilesLocked(); }
    }

    private List<File> cachedFilesLocked() {
        List<File> result = new ArrayList<>();
        if (closed || !enabled() || !prepare(null)) return result;
        for (WallpaperCache.Entry entry : entries.values()) {
            File file = local(entry);
            if (file != null) result.add(file);
        }
        return result;
    }

    synchronized File resolve(File wanted, String pinnedName) {
        synchronized (CACHE_LOCK) { return resolveLocked(wanted, pinnedName); }
    }

    private File resolveLocked(File wanted, String pinnedName) {
        if (closed || wanted == null) return null;
        WallpaperCache.Entry entry = entries.get(wanted.getName());
        if (entry == null) return null;
        if (!prepare(pinnedName)) return null;
        File local = local(entry);
        if (local != null) { touch(local, entry); return local; }
        long now = System.currentTimeMillis();
        if (now < serverBackoff || now < failedUntil.getOrDefault(entry.name, 0L)) return null;
        File partial = new File(cache, entry.name + ".part");
        File target = new File(cache, entry.name);
        boolean success = false;
        try {
            if (!cache.exists() && !cache.mkdirs()) return null;
            if (!cache.getAbsoluteFile().equals(cache.getCanonicalFile())) return null;
            if (partial.exists() && (!WallpaperCache.directFile(cache, partial) || !partial.delete())) return null;
            if (!WallpaperCache.reserve(seeds, cache, pinnedName, WallpaperCache.TRANSFER_LIMIT)) return null;
            String address = entry.url;
            HttpsURLConnection http = null;
            for (int redirects = 0; redirects <= 3; redirects++) {
                if (closed || !WallpaperCache.safeUrl(address)) return null;
                http = (HttpsURLConnection) new URL(address).openConnection();
                connection = http;
                http.setConnectTimeout(8_000);
                http.setReadTimeout(8_000);
                http.setInstanceFollowRedirects(false);
                http.setUseCaches(false);
                http.setRequestProperty("User-Agent", "CleanYourTV/1.0 (https://github.com/matthiasmeester/cleanyourtv)");
                http.setRequestProperty("Accept", "image/jpeg");
                int status = http.getResponseCode();
                if (status == 429 || status == 503) {
                    serverBackoff = retryAfter(http.getHeaderField("Retry-After"), now);
                    return null;
                }
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    String redirect = http.getHeaderField("Location");
                    if (redirect == null || redirects == 3) return null;
                    address = new URL(new URL(address), redirect).toString();
                    http.disconnect();
                    continue;
                }
                if (status != 200) return null;
                break;
            }
            if (http == null || closed) return null;
            long announced = http.getContentLengthLong();
            if (announced > WallpaperCache.TRANSFER_LIMIT) return null;
            long size = 0;
            long deadline = System.nanoTime() + 45_000_000_000L;
            try (InputStream input = new BufferedInputStream(http.getInputStream());
                 FileOutputStream output = new FileOutputStream(partial)) {
                byte[] buffer = new byte[32 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (closed || System.nanoTime() > deadline) return null;
                    size += count;
                    if (size > WallpaperCache.TRANSFER_LIMIT) return null;
                    output.write(buffer, 0, count);
                }
            }
            if (closed || !valid(partial, entry)) return null;
            if (target.exists() && (!WallpaperCache.directFile(cache, target) || !target.delete())) return null;
            if (!partial.renameTo(target)) return null;
            touch(target, entry);
            failedUntil.remove(entry.name);
            success = true;
            return target;
        } catch (Exception failed) {
            // A disconnected TV must not spend one timeout per catalogue entry.
            serverBackoff = Math.max(serverBackoff, System.currentTimeMillis() + 60_000L);
            return null;
        } finally {
            HttpsURLConnection http = connection;
            connection = null;
            if (http != null) http.disconnect();
            if (WallpaperCache.directFile(cache, partial)) partial.delete();
            if (!success) failedUntil.put(entry.name, System.currentTimeMillis() + 60_000L);
        }
    }

    private File local(WallpaperCache.Entry entry) {
        File seed = new File(seeds, entry.name);
        if (WallpaperCache.directFile(seeds, seed) && valid(seed, entry)) return seed;
        File cached = new File(cache, entry.name);
        return WallpaperCache.directFile(cache, cached) && valid(cached, entry) ? cached : null;
    }

    private boolean prepare(String pinned) {
        if (!prepared) {
            WallpaperCache.cleanPartials(cache);
            prepared = true;
        }
        return WallpaperCache.reserve(seeds, cache, pinned, 0);
    }

    private void touch(File file, WallpaperCache.Entry entry) {
        // Called only after successful validation; keep the validation memo in sync with our LRU touch.
        file.setLastModified(System.currentTimeMillis());
        checked.put(file.getAbsolutePath(), file.length() + ":" + file.lastModified() + ":" + entry.hash);
    }

    private boolean valid(File file, WallpaperCache.Entry entry) {
        if (closed || file.length() <= 0 || file.length() > WallpaperCache.TRANSFER_LIMIT) return false;
        String signature = file.length() + ":" + file.lastModified() + ":" + entry.hash;
        if (signature.equals(checked.get(file.getAbsolutePath()))) return true;
        try {
            try (FileInputStream input = new FileInputStream(file)) {
                if (input.read() != 0xff || input.read() != 0xd8 || input.read() != 0xff) return false;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            if (!"image/jpeg".equals(options.outMimeType) || options.outWidth < 2560
                    || options.outHeight < 1440 || options.outWidth <= options.outHeight
                    || (long) options.outWidth * options.outHeight > 40_000_000L) return false;
            if (!entry.hash.isEmpty()) {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                try (InputStream input = new FileInputStream(file)) {
                    byte[] buffer = new byte[32 * 1024];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (closed) return false;
                        digest.update(buffer, 0, count);
                    }
                }
                StringBuilder actual = new StringBuilder();
                for (byte b : digest.digest()) actual.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
                if (!entry.hash.equals(actual.toString())) return false;
            }
            checked.put(file.getAbsolutePath(), signature);
            return true;
        } catch (Exception invalid) { return false; }
    }

    static long retryAfter(String value, long now) {
        long delay = 60_000L;
        if (value != null) {
            try { delay = Math.multiplyExact(Long.parseLong(value.trim()), 1000L); }
            catch (Exception notSeconds) {
                try { delay = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - now; }
                catch (Exception notDate) { /* Minimum one minute. */ }
            }
        }
        return now + Math.max(60_000L, Math.min(24 * 60 * 60_000L, delay));
    }

    void close() {
        closed = true;
        HttpsURLConnection http = connection;
        if (http != null) http.disconnect();
    }
}
