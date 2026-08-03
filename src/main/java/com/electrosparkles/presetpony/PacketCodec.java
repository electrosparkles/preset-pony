package com.electrosparkles.presetpony;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;

/**
 * Encodes/decodes MustangPacket payloads. Payload offsets
 * below are PAYLOAD-relative (0-47); MustangPacket handles the +16 shift
 * into the full 64-byte packet internally.
 */
public class PacketCodec {

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    // ---- handshake / control commands ----

    public static MustangPacket encodeInit0() {
        return new MustangPacket(new Header().stage(Stage.INIT0).type(PacketType.INIT0).dsp(Dsp.NONE));
    }

    public static MustangPacket encodeInit1() {
        return new MustangPacket(new Header().stage(Stage.INIT1).type(PacketType.INIT1).dsp(Dsp.NONE));
    }

    /** The "give me everything" command: current preset + full preset name list + capability tables. */
    public static MustangPacket encodeLoadCommand() {
        return new MustangPacket(new Header().stage(Stage.UNKNOWN).type(PacketType.LOAD).dsp(Dsp.NONE));
    }

    public static MustangPacket encodeApplyCommand() {
        return new MustangPacket(new Header().stage(Stage.READY).type(PacketType.DATA).dsp(Dsp.NONE));
    }

    public static MustangPacket encodeLoadSlotCommand(int slot) {
        Header h = new Header().stage(Stage.READY).type(PacketType.OPERATION)
                .dsp(Dsp.OP_SELECT_MEM_BANK).slot(slot).unknown(0x00, 0x01, 0x00);
        return new MustangPacket(h);
    }

    // ---- names ----

    public static String decodeName(MustangPacket packet) {
        byte[] payload = packet.payload();
        int end = 0;
        while (end < 32 && payload[end] != 0) end++;
        return new String(payload, 0, end, StandardCharsets.US_ASCII);
    }

    public static MustangPacket encodeName(int slot, String name) {
        Header h = new Header().stage(Stage.READY).type(PacketType.OPERATION)
                .dsp(Dsp.OP_SAVE).slot(slot).unknown(0x00, 0x01, 0x01);
        MustangPacket p = new MustangPacket(h);
        byte[] nameBytes = name.substring(0, Math.min(name.length(), 32)).getBytes(StandardCharsets.US_ASCII);
        for (int i = 0; i < nameBytes.length; i++) {
            p.setPayloadByte(i, nameBytes[i] & 0xFF);
        }
        return p;
    }

    // ---- amp settings ----

    public static AmpSettings decodeAmpSettings(MustangPacket ampPacket, MustangPacket usbGainPacket) {
        AmpModel model = AmpModel.fromId(ampPacket.payloadByte(0));
        return new AmpSettings(
                model,
                ampPacket.payloadByte(16),
                ampPacket.payloadByte(17),
                ampPacket.payloadByte(18),
                ampPacket.payloadByte(19),
                ampPacket.payloadByte(20),
                ampPacket.payloadByte(21),
                ampPacket.payloadByte(22),
                ampPacket.payloadByte(23),
                ampPacket.payloadByte(24), // unknown24 - per-preset; feeds ControlIndex 8 and 11 in .fuse
                ampPacket.payloadByte(25),
                ampPacket.payloadByte(26),
                ampPacket.payloadByte(31),
                ampPacket.payloadByte(32),
                CabinetModel.fromId(ampPacket.payloadByte(33)),
                ampPacket.payloadByte(35),
                ampPacket.payloadByte(36),
                usbGainPacket.payloadByte(0)
        );
    }

    public static MustangPacket encodeAmpSettings(AmpSettings s) {
        // Header's unknown triple is constant for amp writes, regardless of model
        // (only the PAYLOAD unknown triple below varies per model).
        Header h = new Header().stage(Stage.READY).type(PacketType.DATA).dsp(Dsp.AMP).unknown(0x00, 0x01, 0x01);
        MustangPacket p = new MustangPacket(h);

        p.setPayloadByte(0, s.model().id);
        p.setPayloadByte(16, s.volume());
        p.setPayloadByte(17, s.gain());
        p.setPayloadByte(18, s.gain2());
        p.setPayloadByte(19, s.masterVolume());
        p.setPayloadByte(20, s.treble());
        p.setPayloadByte(21, s.middle());
        p.setPayloadByte(22, s.bass());
        p.setPayloadByte(23, s.presence());
        p.setPayloadByte(26, s.bias());
        // Byte 25 = Depth, only meaningful when Noise Gate == CUSTOM (value 5); at other
        // gate settings this register holds residual/disabled-state data rather than a
        // real user-set depth. Written through as-is on the wire regardless - the export
        // layer (FusePresetExporter.writeAmp) is responsible for treating it as a
        // sentinel when Noise Gate != CUSTOM.
        p.setPayloadByte(25, s.depth());

        // Bytes 24 and 27 hold the same per-preset value; byte 27 mirrors byte 24 in all
        // observed cases. FENDER_65_DELUXE_REVERB is always 0x00 here, which falls out
        // naturally from what the amp itself stores for that model.
        p.setPayloadByte(24, s.unknown24());
        p.setPayloadByte(27, s.unknown24()); // byte 27 mirrors byte 24 in all observed cases
        p.setPayloadByte(37, (s.model().unknownOverride != null) ? s.model().unknownOverride[2] : 0x01);

        int[] spec = s.model().specificBytes;
        p.setPayloadByte(28, spec[0]);
        p.setPayloadByte(29, spec[1]);
        p.setPayloadByte(30, spec[2]);
        p.setPayloadByte(34, spec[3]);
        p.setPayloadByte(38, spec[4]);

        // Noise Gate is 0-5 on the wire: 0-4 are the named presets (Off/Low/Mid/High/Max),
        // and 5 is a distinct "Custom" value (shown as "User" on the amp's own dial),
        // where Threshold/Depth are independently set rather than following the fixed
        // per-preset defaults. Threshold/Depth are always written through as given, since
        // they vary meaningfully across every gate value, not just Custom.
        p.setPayloadByte(31, clamp(s.noiseGate(), 0, 5));
        p.setPayloadByte(32, clamp(s.threshold(), 0, 9));

        p.setPayloadByte(33, s.cabinet().id);
        p.setPayloadByte(35, clamp(s.sag(), 0, 2));
        p.setPayloadByte(36, s.brightness());

        return p;
    }

    public static MustangPacket encodeAmpSettingsUsbGain(AmpSettings s) {
        Header h = new Header().stage(Stage.READY).type(PacketType.DATA).dsp(Dsp.USB_GAIN).unknown(0x00, 0x01, 0x01);
        MustangPacket p = new MustangPacket(h);
        p.setPayloadByte(0, s.usbGain());
        return p;
    }

    // ---- effect settings ----

    private static final EnumSet<EffectModel> MODULATION_STYLE_UNKNOWN = EnumSet.of(
            EffectModel.SINE_CHORUS, EffectModel.TRIANGLE_CHORUS, EffectModel.SINE_FLANGER,
            EffectModel.TRIANGLE_FLANGER, EffectModel.VIBRATONE, EffectModel.VINTAGE_TREMOLO,
            EffectModel.SINE_TREMOLO, EffectModel.STEP_FILTER, EffectModel.PHASER);

    private static final EnumSet<EffectModel> WAH_STYLE_UNKNOWN = EnumSet.of(
            EffectModel.WAH, EffectModel.TOUCH_WAH, EffectModel.RING_MODULATOR, EffectModel.PITCH_SHIFTER,
            EffectModel.WAH_MOD, EffectModel.TOUCH_WAH_MOD);

    private static final EnumSet<EffectModel> DELAY_STYLE_UNKNOWN = EnumSet.of(
            EffectModel.MONO_DELAY, EffectModel.MONO_ECHO_FILTER, EffectModel.STEREO_ECHO_FILTER,
            EffectModel.MULTITAP_DELAY, EffectModel.PING_PONG_DELAY, EffectModel.DUCKING_DELAY,
            EffectModel.REVERSE_DELAY, EffectModel.TAPE_DELAY, EffectModel.STEREO_TAPE_DELAY);

    public static EffectSettings decodeEffectSettings(MustangPacket packet) {
        int modelId = packet.payloadU16LE(0);
        EffectModel model = EffectModel.fromId(modelId);
        int slot = packet.payloadByte(2);
        // Payload byte 6 (packet byte 22) -  0x01 = bypassed, 0x00 = active.
        boolean enabled = packet.payloadByte(6) == 0;
        return new EffectSettings(slot, model,
                packet.payloadByte(16), packet.payloadByte(17), packet.payloadByte(18),
                packet.payloadByte(19), packet.payloadByte(20), packet.payloadByte(21),
                enabled);
    }

    public static MustangPacket encodeEffectSettings(EffectSettings s) {
        // Target DSP unit is derived from the slot position (POS 0-7, category = POS % 4),
        // not from model().dspSlotGroup - EMPTY has no category of its own (dspSlotGroup
        // is invalid/-1 for it), but an empty slot still needs to target the correct DSP
        // unit to clear it. For real effects these always agree (Section 11: each effect
        // model is hard-wired to one fixed DSP category matching its slot's group).
        Header h = new Header().stage(Stage.READY).type(PacketType.DATA)
                .dsp(Dsp.effectSlot(s.slot() % 4)).unknown(0x00, 0x01, 0x01);
        MustangPacket p = new MustangPacket(h);

        p.setPayloadU16LE(0, s.model().id);
        p.setPayloadByte(2, s.slot());

        int knob1 = s.knob1(), knob2 = s.knob2(), knob3 = s.knob3(),
                knob4 = s.knob4(), knob5 = s.knob5(), knob6 = s.knob6();
        int[] unknown = {0x00, 0x08, 0x01}; // default

        if (s.model() == EffectModel.SIMPLE_COMP) {
            knob1 = clamp(knob1, 0, 3);
            knob2 = knob3 = knob4 = knob5 = 0;
            unknown = new int[]{0x08, 0x08, 0x01};
        } else if (WAH_STYLE_UNKNOWN.contains(s.model())) {
            unknown = new int[]{0x01, 0x08, 0x01};
            if (s.model() == EffectModel.RING_MODULATOR) knob4 = clamp(knob4, 0, 1);
        } else if (MODULATION_STYLE_UNKNOWN.contains(s.model())) {
            unknown = new int[]{0x01, 0x01, 0x01};
            if (s.model() == EffectModel.PHASER) knob5 = clamp(knob5, 0, 1);
        } else if (DELAY_STYLE_UNKNOWN.contains(s.model())) {
            unknown = new int[]{0x02, 0x01, 0x01};
            if (s.model() == EffectModel.MULTITAP_DELAY) knob5 = clamp(knob5, 0, 3);
        }
        // else: leave default {0x00, 0x08, 0x01} - covers OVERDRIVE, FUZZ, COMPRESSOR,
        // all reverbs, GREENBOX/ORANGEBOX/BLACKBOX/BIG_FUZZ/RANGER_BOOST, DIATONIC_PITCH_SHIFTER

        p.setPayloadByte(3, unknown[0]);
        p.setPayloadByte(4, unknown[1]);
        p.setPayloadByte(5, unknown[2]);
        p.setPayloadByte(6, s.enabled() ? 0 : 1); // confirmed bypass flag - see decodeEffectSettings

        p.setPayloadByte(16, knob1);
        p.setPayloadByte(17, knob2);
        p.setPayloadByte(18, knob3);
        p.setPayloadByte(19, knob4);
        p.setPayloadByte(20, knob5);
        if (s.model().hasKnob6()) {
            p.setPayloadByte(21, knob6);
        }

        return p;
    }

    public static MustangPacket encodeClearEffectSettings(int dspSlotIndex) {
        Header h = new Header().stage(Stage.READY).type(PacketType.DATA)
                .dsp(Dsp.effectSlot(dspSlotIndex)).unknown(0x00, 0x01, 0x01);
        MustangPacket p = new MustangPacket(h);
        p.setPayloadByte(3, 0x00);
        p.setPayloadByte(4, 0x08);
        p.setPayloadByte(5, 0x01);
        return p;
    }
}
