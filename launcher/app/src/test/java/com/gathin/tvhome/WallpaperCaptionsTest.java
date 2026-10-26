package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.io.File;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class WallpaperCaptionsTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test public void creditsKeepStoriesAndDecodeLineBreaks() throws Exception {
        File directory = folder.newFolder();
        String credit = "Artist and venue. " + new String(new char[120]).replace('\0', 'x');
        Files.write(new File(directory, "credits.txt").toPath(), Arrays.asList(
                "day-001.jpg\t" + credit + "\\nhttps://example.org/photo",
                "../escape.jpg\tInvalid", "notes.txt\tNot a photograph"), StandardCharsets.UTF_8);
        Map<String, String> credits = WallpaperCaptions.readCredits(directory);
        assertEquals(credit + "\nhttps://example.org/photo", credits.get("day-001.jpg"));
        assertEquals(1, credits.size());
        assertTrue(WallpaperCaptions.readCredits(null).isEmpty());
    }

    @Test public void rejectsOversizedMetadata() throws Exception {
        File directory = folder.newFolder();
        Files.write(new File(directory, "credits.txt").toPath(), new byte[1024 * 1024 + 1]);
        assertTrue(WallpaperCaptions.readCredits(directory).isEmpty());
    }

    @Test
    public void readsFilenameTabName() {
        Map<String, String> names = WallpaperCaptions.parse(Arrays.asList(
                "001.jpg\tRed Spider Nebula · Hubble",
                "002.jpg\t  Falcon 9 at sunset  "));
        assertEquals("Red Spider Nebula · Hubble", names.get("001.jpg"));
        assertEquals("Falcon 9 at sunset", names.get("002.jpg"));
    }

    @Test
    public void skipsCommentsBlankAndMalformedLines() {
        Map<String, String> names = WallpaperCaptions.parse(Arrays.asList(
                "# captions for the collection", "", "   ", "no-tab-here.jpg Dusk", "\tno file", "003.jpg\t", null));
        assertTrue(names.isEmpty());
    }

    @Test
    public void cutsVeryLongNames() {
        StringBuilder longName = new StringBuilder();
        for (int i = 0; i < 30; i++) longName.append("word ");
        Map<String, String> names = WallpaperCaptions.parse(Arrays.asList("004.jpg\t" + longName));
        assertTrue(names.get("004.jpg").length() <= WallpaperCaptions.MAX_LENGTH);
    }

    @Test
    public void laterLineWinsForTheSameFile() {
        Map<String, String> names = WallpaperCaptions.parse(Arrays.asList("005.jpg\tOld", "005.jpg\tNew"));
        assertEquals("New", names.get("005.jpg"));
        assertFalse(names.containsKey("006.jpg"));
    }

    @Test
    public void missingFolderHasNoNames() {
        assertTrue(WallpaperCaptions.read(null).isEmpty());
    }
}
