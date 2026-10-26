package com.gathin.tvhome;

/** Shared status policy for the Inputs menu and its top-bar indicator. */
final class InputStatus {
    private InputStatus() { }

    static boolean unreliableHdmi(int ports, int connected) {
        return ports > 1 && connected == ports;
    }

    static int displayedState(boolean hdmi, boolean unreliable, int state) {
        return !hdmi || unreliable ? -1 : state;
    }
}
