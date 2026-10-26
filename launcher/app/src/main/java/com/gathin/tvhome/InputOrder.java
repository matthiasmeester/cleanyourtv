package com.gathin.tvhome;

/** HDMI ports precede other sources; optionally prefer connected HDMI ports within that group. */
final class InputOrder {
    private InputOrder() { }

    static int compare(boolean hdmiA, boolean connectedA, String labelA,
                       boolean hdmiB, boolean connectedB, String labelB, boolean activeFirst) {
        return compare(hdmiA, connectedA, false, labelA, hdmiB, connectedB, false, labelB, activeFirst);
    }

    static int compare(boolean hdmiA, boolean connectedA, boolean deviceA, String labelA,
                       boolean hdmiB, boolean connectedB, boolean deviceB, String labelB, boolean activeFirst) {
        if (hdmiA != hdmiB) return hdmiA ? -1 : 1;
        if (activeFirst && hdmiA && connectedA != connectedB) return connectedA ? -1 : 1;
        if (activeFirst && hdmiA && deviceA != deviceB) return deviceA ? -1 : 1;
        return labelA.compareToIgnoreCase(labelB);
    }
}
