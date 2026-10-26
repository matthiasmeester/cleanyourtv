package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class InputStatusTest {
    @Test public void alwaysConnectedAvNeverLightsIndicator() {
        assertEquals(-1, InputStatus.displayedState(false, false, 0));
    }

    @Test public void unreliableAllConnectedPortsSuppressBothIndicators() {
        assertTrue(InputStatus.unreliableHdmi(3, 3));
        assertEquals(-1, InputStatus.displayedState(true, true, 0));
    }

    @Test public void realHdmiStatesArePreserved() {
        assertFalse(InputStatus.unreliableHdmi(3, 1));
        assertFalse(InputStatus.unreliableHdmi(1, 1));
        assertFalse(InputStatus.unreliableHdmi(0, 0));
        for (int state = 0; state <= 2; state++)
            assertEquals(state, InputStatus.displayedState(true, false, state));
    }
}
