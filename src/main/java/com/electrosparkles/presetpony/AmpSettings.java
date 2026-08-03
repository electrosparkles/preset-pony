package com.electrosparkles.presetpony;

/** Decoded/encodable amp-channel settings. All 0-255 unless noted. */
public record AmpSettings(
        AmpModel model,
        int volume,
        int gain,
        int gain2,
        int masterVolume,
        int treble,
        int middle,
        int bass,
        int presence,
        int unknown24,   // per-preset stored byte; feeds ControlIndex 8 and 11 in .fuse export
        int depth,
        int bias,
        int noiseGate,   // 0-5 (clamped)
        int threshold,   // only meaningful when noiseGate == 5
        CabinetModel cabinet,
        int sag,         // 0-2 (clamped)
        int brightness,
        int usbGain
) {
    /** Same settings with volume replaced - handy for the "nudge a knob" case. */
    public AmpSettings withVolume(int newVolume) {
        return new AmpSettings(model, newVolume, gain, gain2, masterVolume, treble, middle, bass,
                presence, unknown24, depth, bias, noiseGate, threshold, cabinet, sag, brightness, usbGain);
    }
}
