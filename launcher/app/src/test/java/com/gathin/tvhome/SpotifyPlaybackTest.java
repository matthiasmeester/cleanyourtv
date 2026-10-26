package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SpotifyPlaybackTest {
    @Test public void likedSongsAcceptsOnlyExactCollection() {
        assertEquals("spotify:collection:tracks", SpotifyPlayback.normalizePlaylistUri("spotify:collection:tracks"));
        assertEquals("spotify:collection:tracks", SpotifyPlayback.normalizePlaylistUri("https://open.spotify.com/collection/tracks"));
        assertEquals("", SpotifyPlayback.normalizePlaylistUri("spotify:collection:albums"));
    }
    private static final String ID = "0123456789ABCDEFGHIJKL";
    private static final String URI = "spotify:playlist:" + ID;
    private static final String URL = "https://open.spotify.com/playlist/" + ID;

    @Test public void playlistUriKeepsItsCaseSensitiveId() {
        assertEquals(URI, SpotifyPlayback.normalizePlaylistUri(URI));
        assertEquals(URI, SpotifyPlayback.normalizePlaylistUri("  " + URI + "  "));
    }

    @Test public void sharedPlaylistUrlDiscardsQueryParameters() {
        assertEquals(URI, SpotifyPlayback.normalizePlaylistUri(URL));
        assertEquals(URI, SpotifyPlayback.normalizePlaylistUri(URL + "?si=sharing-token&nd=1"));
    }

    @Test public void emptyInputMeansResume() {
        assertEquals("", SpotifyPlayback.normalizePlaylistUri(null));
        assertEquals("", SpotifyPlayback.normalizePlaylistUri(""));
        assertEquals("", SpotifyPlayback.normalizePlaylistUri("   "));
    }

    @Test public void onlyExactBase62PlaylistIdsAreAccepted() {
        String[] invalid = {
                "spotify:playlist:" + ID.substring(1),
                URI + "M",
                "spotify:playlist:" + ID.substring(0, 21) + "_",
                "spotify:playlist:" + ID.substring(0, 21) + "-",
                URI + "?si=token",
                "spotify:track:" + ID,
                "https://open.spotify.com/track/" + ID
        };
        for (String link : invalid) assertEquals(link, "", SpotifyPlayback.normalizePlaylistUri(link));
    }

    @Test public void foreignHostsAndOtherSchemesAreRejected() {
        String[] invalid = {
                "http://open.spotify.com/playlist/" + ID,
                "https://open.spotify.com.evil.example/playlist/" + ID,
                "https://open.spotify.com@evil.example/playlist/" + ID,
                "https://evil.example/open.spotify.com/playlist/" + ID,
                "javascript:" + URL,
                "intent://open.spotify.com/playlist/" + ID
        };
        for (String link : invalid) assertEquals(link, "", SpotifyPlayback.normalizePlaylistUri(link));
    }

    @Test public void pathTraversalAndEncodedPathsAreRejected() {
        String[] invalid = {
                URL + "/../another-playlist",
                "https://open.spotify.com/playlist/../" + ID,
                URL + "%2F..%2Fother",
                "https://open.spotify.com/playlist/%2e%2e/" + ID,
                URL + "#fragment"
        };
        for (String link : invalid) assertEquals(link, "", SpotifyPlayback.normalizePlaylistUri(link));
    }

    @Test public void controlsAreRejectedBeforeTrimmingOrQueryRemoval() {
        String[] invalid = {
                URI + "\n",
                "\r" + URL,
                URL + "?si=token\n&other=value",
                URL + "?si=token\r\nnext=value",
                URL + "?si=token\tother=value",
                URI + "\u0000"
        };
        for (String link : invalid) assertEquals(link, "", SpotifyPlayback.normalizePlaylistUri(link));
    }
}
