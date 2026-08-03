package com.electrosparkles.presetpony;

/** A full 64-byte packet: 16-byte Header + 48-byte payload. */
public class MustangPacket {

    public static final int PACKET_SIZE = 64;
    public static final int PAYLOAD_SIZE = 48;

    private final Header header;
    private final byte[] payload; // 48 bytes, payload-relative indexing (add Header.SIZE for full-packet offset)

    public MustangPacket(Header header, byte[] payload) {
        this.header = header;
        this.payload = payload.clone();
    }

    public MustangPacket(Header header) {
        this(header, new byte[PAYLOAD_SIZE]);
    }

    public Header header() {
        return header;
    }

    public byte[] payload() {
        return payload.clone();
    }

    public int payloadByte(int index) {
        return payload[index] & 0xFF;
    }

    public void setPayloadByte(int index, int value) {
        payload[index] = (byte) value;
    }

    public void setPayloadU16LE(int index, int value) {
        payload[index] = (byte) (value & 0xFF);
        payload[index + 1] = (byte) ((value >> 8) & 0xFF);
    }

    public int payloadU16LE(int index) {
        return (payload[index] & 0xFF) | ((payload[index + 1] & 0xFF) << 8);
    }

    public byte[] toBytes() {
        byte[] out = new byte[PACKET_SIZE];
        System.arraycopy(header.toBytes(), 0, out, 0, Header.SIZE);
        System.arraycopy(payload, 0, out, Header.SIZE, PAYLOAD_SIZE);
        return out;
    }

    public static MustangPacket fromBytes(byte[] data) {
        Header h = Header.fromBytes(data, 0);
        byte[] p = new byte[PAYLOAD_SIZE];
        System.arraycopy(data, Header.SIZE, p, 0, PAYLOAD_SIZE);
        return new MustangPacket(h, p);
    }
}
