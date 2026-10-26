package com.gathin.tvhome;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class InputOrderTest {
    @Test public void standbyDevicePrecedesEmptyPorts() {
        assertTrue(InputOrder.compare(true, false, true, "HDMI 3",
                true, false, false, "HDMI 1", true) < 0);
    }

    @Test public void liveDeviceStillPrecedesStandbyDevice() {
        assertTrue(InputOrder.compare(true, true, true, "HDMI 2",
                true, false, true, "HDMI 3", true) < 0);
    }

    @Test public void disabledPriorityKeepsNameOrderForKnownDevices() {
        assertTrue(InputOrder.compare(true, false, true, "HDMI 3",
                true, false, false, "HDMI 1", false) > 0);
    }
    @Test public void connectedHdmiThreePrecedesUnusedHdmiOneAndTwo() {
        List<Source> sources = new ArrayList<>(Arrays.asList(
                new Source("HDMI 2", true, false),
                new Source("HDMI 1", true, false),
                new Source("HDMI 3", true, true)));
        sources.sort((a, b) -> compare(a, b, true));
        assertEquals(Arrays.asList("HDMI 3", "HDMI 1", "HDMI 2"), labels(sources));
    }

    @Test public void namesOrderPortsWithTheSameConnectionState() {
        List<Source> sources = new ArrayList<>(Arrays.asList(
                new Source("HDMI 4", true, true),
                new Source("HDMI 2", true, false),
                new Source("HDMI 3", true, true),
                new Source("HDMI 1", true, false)));
        sources.sort((a, b) -> compare(a, b, true));
        assertEquals(Arrays.asList("HDMI 3", "HDMI 4", "HDMI 1", "HDMI 2"), labels(sources));
    }

    @Test public void connectedAvDoesNotPrecedeUnusedHdmi() {
        assertTrue(InputOrder.compare(true, false, "HDMI 1", false, true, "AV", true) < 0);
        assertTrue(InputOrder.compare(false, false, "AV", false, true, "Component", true) < 0);
    }

    @Test public void disabledOrUnreliableStateKeepsPortNameOrder() {
        assertTrue(InputOrder.compare(true, false, "HDMI 1", true, true, "HDMI 3", false) < 0);
        assertEquals(0, InputOrder.compare(true, true, "hdmi 1", true, true, "HDMI 1", true));
    }

    private static int compare(Source a, Source b, boolean activeFirst) {
        return InputOrder.compare(a.hdmi, a.connected, a.label, b.hdmi, b.connected, b.label, activeFirst);
    }

    private static List<String> labels(List<Source> sources) {
        List<String> labels = new ArrayList<>();
        for (Source source : sources) labels.add(source.label);
        return labels;
    }

    private static final class Source {
        final String label;
        final boolean hdmi, connected;

        Source(String label, boolean hdmi, boolean connected) {
            this.label = label;
            this.hdmi = hdmi;
            this.connected = connected;
        }
    }
}
