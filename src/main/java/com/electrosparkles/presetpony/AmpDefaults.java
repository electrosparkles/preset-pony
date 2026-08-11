package com.electrosparkles.presetpony;

/**
 * Per-amp factory-default knob values, loaded from amp-facts.properties.
 * All fields are raw bytes (0-255) ready to write directly to AmpSettings,
 * except sag (0-2 index), noiseGate (0-5 index), brightness (0/1), and
 * the sentinel value -1 for fields that don't exist on this amp model
 * (currently sag and bias on Studio Preamp only).
 *
 * Fields that amp-facts.properties does not supply for a given model
 * (e.g. presence on amps without a presence control) are set to -1,
 * which callers should treat as "leave the current value untouched."
 */
public record AmpDefaults(
        int gain,
        int volume,
        int treble,
        int middle,
        int bass,
        int presence,      // -1 if this model has no presence/cut control
        int gain2,         // -1 if this model has no gain2/blend control
        int masterVolume,  // -1 if this model has no master volume control
        int noiseGate,     // index 0-5
        int threshold,
        int depth,
        int sag,           // -1 = N/A (Studio Preamp only)
        int bias,          // -1 = N/A (Studio Preamp only)
        int brightness     // -1 if this model has no brightness control; 0=off, 1=on
) {}
