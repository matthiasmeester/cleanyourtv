package com.gathin.tvhome;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure-Java policy for the bounded, disposable wallpaper cache. */
final class WallpaperCache {
    static final long BUDGET = 100_000_000L;
    static final long TRANSFER_LIMIT = 20_000_000L;
    static final class Entry {
        final String name, url, hash, period;
        Entry(String name, String url, String hash, String period) {
            this.name = name; this.url = url; this.hash = hash; this.period = period;
        }
    }
    private WallpaperCache() { }

    static boolean safeName(String name) {
        return name != null && name.length() <= 150
                && name.matches("[A-Za-z0-9][A-Za-z0-9_-]*\\.jpg");
    }

    static boolean safeUrl(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getUserInfo() == null
                    && (uri.getPort() == -1 || uri.getPort() == 443) && uri.getFragment() == null
                    && ("upload.wikimedia.org".equalsIgnoreCase(host)
                    || "thumb.wikimedia.org".equalsIgnoreCase(host))
                    && uri.getPath() != null && uri.getPath().startsWith("/wikipedia/");
        } catch (Exception badUrl) { return false; }
    }

    static Map<String, Entry> parse(List<String> lines) {
        Map<String, Entry> entries = new LinkedHashMap<>();
        for (String line : lines) {
            if (line == null || line.trim().isEmpty() || line.startsWith("#")) continue;
            String[] fields = line.split("\t", -1);
            if (fields.length != 4 || !safeName(fields[0]) || !safeUrl(fields[1])) continue;
            String hash = fields[2].toLowerCase(java.util.Locale.ROOT);
            if (!hash.isEmpty() && !hash.matches("[0-9a-f]{64}")) continue;
            if (!"day".equals(fields[3]) && !"night".equals(fields[3])) continue;
            if (!entries.containsKey(fields[0])) entries.put(fields[0],
                    new Entry(fields[0], fields[1], hash, fields[3]));
        }
        return entries;
    }

    /** Never follows a child symlink outside (or within) the owned directory. */
    static boolean directFile(File folder, File file) {
        try {
            return file.isFile() && file.getAbsoluteFile().equals(file.getCanonicalFile())
                    && folder.getCanonicalFile().equals(file.getCanonicalFile().getParentFile());
        } catch (IOException failure) { return false; }
    }

    static long bytes(File folder, boolean seeds) {
        long result = 0;
        File[] files = folder.listFiles();
        if (files != null) for (File file : files) {
            if (directFile(folder, file) && (!seeds || file.getName().matches("(?i).*\\.(jpg|jpeg|png|webp)"))) {
                result += file.length();
            }
        }
        return result;
    }

    static void cleanPartials(File cache) {
        File[] files = cache.listFiles();
        if (files != null) for (File file : files) {
            String name = file.getName();
            if (name.endsWith(".part") && safeName(name.substring(0, name.length() - 5))
                    && directFile(cache, file)) file.delete();
        }
    }

    /** Reserve the entire bounded transfer before opening a network stream. Seeds are never deleted. */
    static boolean reserve(File seeds, File cache, String pinned, long incoming) {
        if (incoming < 0 || incoming > TRANSFER_LIMIT) return false;
        long used = bytes(seeds, true) + bytes(cache, false);
        if (used + incoming <= BUDGET) return true;
        List<File> candidates = new ArrayList<>();
        File[] files = cache.listFiles();
        if (files != null) for (File file : files) {
            String name = file.getName();
            String base = name.endsWith(".part") ? name.substring(0, name.length() - 5) : name;
            if (safeName(base) && !base.equals(pinned) && directFile(cache, file)) candidates.add(file);
        }
        candidates.sort(Comparator.comparingLong(File::lastModified).thenComparing(File::getName));
        for (File candidate : candidates) {
            long size = candidate.length();
            if (candidate.delete()) used -= size;
            if (used + incoming <= BUDGET) return true;
        }
        return false;
    }
}
