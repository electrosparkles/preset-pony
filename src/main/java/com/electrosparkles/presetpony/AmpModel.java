package com.electrosparkles.presetpony;

/**
 * Amp models, with wire IDs and the 5 "amp-specific" bytes each model writes
 * (payload-relative offsets 28,29,30,34,38 - see PacketCodec). IDs and specific
 * bytes transcribed directly from offa/plug's PacketSerializer.cpp. Real-world
 * equivalent names from the Fender Mustang Amps and Fuse Fandom wiki
 * (https://fender-mustang-amps-and-fuse.fandom.com/wiki/Amp_Models), used in
 * place of the source's plain enum names for a couple of the less obvious ones
 * (e.g. METAL_2000 is modeled on a Peavey 5150, not a Fender-branded amp).
 *
 * Live-validated: FENDER_57_TWIN (0xf6) was confirmed against real hardware
 * during protocol validation (see requirements doc appendix).
 */
public enum AmpModel {
    FENDER_57_DELUXE(0x67, "Fender '57 Deluxe", 0x01, 0x01, 0x01, 0x01, 0x53),
    FENDER_59_BASSMAN(0x64, "Fender '59 Bassman", 0x02, 0x02, 0x02, 0x02, 0x67),
    FENDER_57_CHAMP(0x7c, "Fender '57 Champ", 0x0c, 0x0c, 0x0c, 0x0c, 0x00),
    FENDER_65_DELUXE_REVERB(0x53, "Fender '65 Deluxe Reverb", 0x03, 0x03, 0x03, 0x03, 0x6a, 0x00, 0x00, 0x01), // has its own unknown-triple override
    FENDER_65_PRINCETON(0x6a, "Fender '65 Princeton Reverb", 0x04, 0x04, 0x04, 0x04, 0x61),
    FENDER_65_TWIN_REVERB(0x75, "Fender '65 Twin Reverb", 0x05, 0x05, 0x05, 0x05, 0x72),
    FENDER_SUPER_SONIC(0x72, "Fender Super-Sonic 22", 0x06, 0x06, 0x06, 0x06, 0x79),
    BRITISH_60S(0x61, "British '60s (VOX AC30)", 0x07, 0x07, 0x07, 0x07, 0x5e),
    BRITISH_70S(0x79, "British '70s (Marshall 1959SLP Plexi)", 0x0b, 0x0b, 0x0b, 0x0b, 0x7c),
    BRITISH_80S(0x5e, "British '80s (Marshall JCM 800)", 0x09, 0x09, 0x09, 0x09, 0x5d),
    AMERICAN_90S(0x5d, "American '90s (Mesa/Boogie Dual Rectifier)", 0x0a, 0x0a, 0x0a, 0x0a, 0x6d),
    METAL_2000(0x6d, "Metal 2000 (Peavey 5150)", 0x08, 0x08, 0x08, 0x08, 0x75),
    STUDIO_PREAMP(0xf1, "Studio Preamp (clean, no modeling)", 0x0d, 0x0d, 0x0d, 0x0d, 0xf6),
    FENDER_57_TWIN(0xf6, "Fender '57 Twin", 0x0e, 0x0e, 0x0e, 0x0e, 0xf9),
    FENDER_60_THRIFT(0xf9, "'60s Thrift (Sears 1964 Silvertone)", 0x0f, 0x0f, 0x0f, 0x0f, 0xfc),
    BRITISH_COLOUR(0xfc, "British Colour (Orange Custom Shop)", 0x10, 0x10, 0x10, 0x10, 0xff),
    BRITISH_WATTS(0xff, "British Watts (HiWatt 100 DR103)", 0x11, 0x11, 0x11, 0x11, 0x00);

    public final int id;
    public final String displayName;
    public final int[] specificBytes; // written at payload offsets 28,29,30,34,38 (in that order)
    public final int[] unknownOverride; // null unless this model overrides the default (0x80,0x80,0x01)

    AmpModel(int id, String displayName, int s0, int s1, int s2, int s3, int s4) {
        this(id, displayName, s0, s1, s2, s3, s4, -1, -1, -1);
    }

    AmpModel(int id, String displayName, int s0, int s1, int s2, int s3, int s4, int u0, int u1, int u2) {
        this.id = id;
        this.displayName = displayName;
        this.specificBytes = new int[]{s0, s1, s2, s3, s4};
        this.unknownOverride = (u0 == -1) ? null : new int[]{u0, u1, u2};
    }

    public static AmpModel fromId(int id) {
        for (AmpModel m : values()) {
            if (m.id == id) return m;
        }
        throw new IllegalArgumentException("Unknown amp model id: 0x" + Integer.toHexString(id));
    }

    /**
     * The cabinet Fuse pairs this amp with by default. The 13 v1 amps are
     * confirmed from amplifier.cpp's choose_amp() switch (see requirements
     * doc Section 11) - Plug's own source - EXCEPT Studio Preamp:
     * which actually defaults to CabinetModel.OFF (no cabinet
     * simulation, which fits its "clean, no modeling" nature). The 4
     * v2-exclusive amps come from directly observing real Fuse's UI
     */
    public CabinetModel defaultCabinet() {
        return switch (this) {
            case FENDER_57_DELUXE -> CabinetModel.FENDER_57_DELUXE;
            case FENDER_59_BASSMAN -> CabinetModel.FENDER_59_BASSMAN;
            case FENDER_57_CHAMP -> CabinetModel.FENDER_57_CHAMPION;
            case FENDER_65_DELUXE_REVERB -> CabinetModel.FENDER_65_DELUXE;
            case FENDER_65_PRINCETON -> CabinetModel.FENDER_65_PRINCETON;
            case FENDER_65_TWIN_REVERB -> CabinetModel.FENDER_65_TWIN;
            case FENDER_SUPER_SONIC -> CabinetModel.SUPER_SONIC_1X12;
            case BRITISH_60S -> CabinetModel.VOX_2X12_CELESTION;
            case BRITISH_70S -> CabinetModel.MARSHALL_4X12_GREENBACKS;
            case BRITISH_80S -> CabinetModel.MARSHALL_4X12_MODERN;
            case AMERICAN_90S -> CabinetModel.MARSHALL_4X12_VINTAGE;
            case METAL_2000 -> CabinetModel.MARSHALL_4X12_GREENBACKS;
            case STUDIO_PREAMP -> CabinetModel.OFF;
            // v2-exclusive - observed directly in real Fuse, not from Plug source:
            case FENDER_57_TWIN -> CabinetModel.FENDER_65_TWIN;
            case FENDER_60_THRIFT -> CabinetModel.FENDER_57_DELUXE;
            case BRITISH_COLOUR -> CabinetModel.MARSHALL_4X12_GREENBACKS;
            case BRITISH_WATTS -> CabinetModel.MARSHALL_4X12_VINTAGE;
        };
    }

    @Override
    public String toString() {
        return displayName;
    }

    /**
     * Amps with a Brightness switch:
     * Fender '65 Twin Reverb and British '60s/VOX AC30
     * For other amps payload byte 36 is still read/written on the wire
     * but it isn't a real user-facing control
     */
    public static final java.util.EnumSet<AmpModel> SUPPORTS_BRIGHTNESS =
            java.util.EnumSet.of(FENDER_65_TWIN_REVERB, BRITISH_60S);

    /**
     * The UI's label for the presence() control - "Presence" for every amp except
     * British '60s, where it's relabeled "Cut"
     */
    public String presenceUiLabel() {
        return this == BRITISH_60S ? "Cut" : "Presence";
    }

    /**
     * Amps where the Gain 2 wire byte drives a Blend control instead of a second gain
     * knob: Fender '59 Bassman and British '70s (Marshall 1959SLP Plexi). Same pattern
     * as SUPPORTS_BRIGHTNESS and presenceUiLabel() above - one shared wire byte,
     *  When active, Gain 2 is shown on Fuse and panel as 'Blend' with -50%/+50% control
     */
    public static final java.util.EnumSet<AmpModel> SUPPORTS_GAIN2_BLEND =
            java.util.EnumSet.of(FENDER_59_BASSMAN, BRITISH_70S);

    /** The UI's label for the gain2() control - "Blend" for the amps in
     * SUPPORTS_GAIN2_BLEND, "Gain 2" for every other amp. */
    public String gain2UiLabel() {
        return SUPPORTS_GAIN2_BLEND.contains(this) ? "Blend" : "Gain 2";
    }

    /**
     * Studio Preamp has no Sag or Bias control at all
     */
    public static final java.util.EnumSet<AmpModel> STUDIO_PREAMP_HAS_NO_SAG_BIAS =
            java.util.EnumSet.of(STUDIO_PREAMP);
}
