package com.electrosparkles.presetpony;

/** Header byte[2] - which DSP unit / operation a packet targets. */
public enum Dsp {
    NONE(0x00),
    AMP(0x05),
    USB_GAIN(0x0d),
    EFFECT0(0x06),
    EFFECT1(0x07),
    EFFECT2(0x08),
    EFFECT3(0x09),
    OP_SAVE(0x03),
    OP_SAVE_EFFECT_NAME(0x04),
    OP_SELECT_MEM_BANK(0x01);

    public final int wireValue;

    Dsp(int wireValue) {
        this.wireValue = wireValue;
    }

    public static Dsp fromWire(int b) {
        for (Dsp d : values()) {
            if (d.wireValue == b) return d;
        }
        throw new IllegalArgumentException("Unknown DSP byte: 0x" + Integer.toHexString(b));
    }

    /** effect0-3 by index 0-3, matching the four effect slots. */
    public static Dsp effectSlot(int index) {
        return switch (index) {
            case 0 -> EFFECT0;
            case 1 -> EFFECT1;
            case 2 -> EFFECT2;
            case 3 -> EFFECT3;
            default -> throw new IllegalArgumentException("Effect slot must be 0-3, got " + index);
        };
    }
}
