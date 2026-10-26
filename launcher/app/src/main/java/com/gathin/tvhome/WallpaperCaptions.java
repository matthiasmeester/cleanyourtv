package com.gathin.tvhome;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Names for a wallpaper collection (wallpaper_captions): {@code wallpapers/captions.txt}, one
 * "filename<TAB>name" per line. Blank lines, lines starting with # and lines without a tab are skipped; a name
 * is cut to {@link #MAX_LENGTH} characters. No Android types, so it is tested on the JVM.
 */
final class WallpaperCaptions {
    static final String FILE_NAME = "captions.txt";
    static final String CREDITS_FILE_NAME = "credits.txt";
    static final int MAX_LENGTH = 80;

    private WallpaperCaptions() { }

    static Map<String, String> parse(List<String> lines) {
        return parse(lines, MAX_LENGTH);
    }

    private static Map<String, String> parse(List<String> lines, int maxLength) {
        Map<String, String> names = new HashMap<>();
        for (String line : lines) {
            if (line == null || line.trim().isEmpty() || line.trim().startsWith("#")) continue;
            int tab = line.indexOf('\t');
            if (tab <= 0) continue;
            String file = line.substring(0, tab).trim();
            String name = line.substring(tab + 1).trim();
            if (file.isEmpty() || name.isEmpty()) continue;
            if (name.length() > maxLength) name = name.substring(0, maxLength).trim();
            names.put(file, name);
        }
        return names;
    }

    /** The names in {@code folder}/captions.txt, or none when the file is missing or unreadable. */
    static Map<String, String> read(File folder) {
        return read(folder, FILE_NAME, MAX_LENGTH);
    }

    /** Collection attribution stays beside the photos instead of making every APK larger. */
    static Map<String, String> readCredits(File folder) {
        Map<String, String> credits = read(folder, CREDITS_FILE_NAME, 4096);
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, String> entry : credits.entrySet()) {
            if (ThemeFiles.collectionImage(entry.getKey())) {
                result.put(entry.getKey(), entry.getValue().replace("\\n", "\n"));
            }
        }
        return result;
    }

    private static Map<String, String> read(File folder, String filename, int maxLength) {
        File file = folder == null ? null : new File(folder, filename);
        if (file == null || !file.isFile() || file.length() > 1024 * 1024) return Collections.emptyMap();
        try {
            return parse(Files.readAllLines(file.toPath(), StandardCharsets.UTF_8), maxLength);
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
}
