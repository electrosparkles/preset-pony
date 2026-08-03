package com.electrosparkles.presetpony;

import java.util.Arrays;

/**
 * The 16-byte header at the start of every 64-byte Mustang packet.
 * Layout from offa/plug
 *   [0] stage, [1] type, [2] dsp, [3] unknown0, [4] slot,
 *   [5] unused, [6] unknown1, [7] unknown2, [8..15] padding
 */
public class Header {

    public static final int SIZE = 16;

    private final byte[] bytes = new byte[SIZE];

    public Header() {
    }

    public Header stage(Stage stage) {
        bytes[0] = (byte) stage.wireValue;
        return this;
    }

    public Stage stage() {
        return Stage.fromWire(bytes[0] & 0xFF);
    }

    public Header type(PacketType type) {
        bytes[1] = (byte) type.wireValue;
        return this;
    }

    public int typeRaw() {
        return bytes[1] & 0xFF;
    }

    public Header dsp(Dsp dsp) {
        bytes[2] = (byte) dsp.wireValue;
        return this;
    }

    public int dspRaw() {
        return bytes[2] & 0xFF;
    }

    public Header slot(int slot) {
        bytes[4] = (byte) slot;
        return this;
    }

    public int slot() {
        return bytes[4] & 0xFF;
    }

    public Header unknown(int value0, int value1, int value2) {
        bytes[3] = (byte) value0;
        bytes[6] = (byte) value1;
        bytes[7] = (byte) value2;
        return this;
    }

    public byte[] toBytes() {
        return bytes.clone();
    }

    public static Header fromBytes(byte[] data, int offset) {
        Header h = new Header();
        System.arraycopy(data, offset, h.bytes, 0, SIZE);
        return h;
    }

    @Override
    public String toString() {
        return "Header{stage=0x" + Integer.toHexString(bytes[0] & 0xFF)
                + ", type=0x" + Integer.toHexString(bytes[1] & 0xFF)
                + ", dsp=0x" + Integer.toHexString(bytes[2] & 0xFF)
                + ", slot=" + (bytes[4] & 0xFF) + "}";
    }
}
