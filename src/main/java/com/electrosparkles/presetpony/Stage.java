package com.electrosparkles.presetpony;

/** Header byte[0]. Values confirmed from offa/plug's Packet.cpp. */
public enum Stage {
    INIT0(0x00),
    INIT1(0x1a),
    READY(0x1c),
    UNKNOWN(0xff); // used for the "load everything" command - falls through to this in the source too

    public final int wireValue;

    Stage(int wireValue) {
        this.wireValue = wireValue;
    }

    public static Stage fromWire(int b) {
        for (Stage s : values()) {
            if (s.wireValue == b) return s;
        }
        return UNKNOWN;
    }
}
