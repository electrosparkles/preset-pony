package com.electrosparkles.presetpony;

/**
 * Header byte[1]. Note: INIT1 and LOAD share the same wire value (0xc1) in
 * the real protocol - they're only distinguished by which Stage accompanies
 * them. fromWire() can't disambiguate; callers who need to tell them apart
 * should look at the Stage byte too.
 */
public enum PacketType {
    OPERATION(0x01),
    DATA(0x03),
    INIT0(0xc3),
    INIT1(0xc1),
    LOAD(0xc1);

    public final int wireValue;

    PacketType(int wireValue) {
        this.wireValue = wireValue;
    }

    public static PacketType fromWire(int b) {
        for (PacketType t : values()) {
            if (t.wireValue == b) return t;
        }
        throw new IllegalArgumentException("Unknown packet type byte: 0x" + Integer.toHexString(b));
    }
}
