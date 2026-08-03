package com.electrosparkles.presetpony;

/**
 * Decoded/encodable effect-slot settings. model is null for an empty slot.
 *
 * `enabled` reflects the amp's real bypass state, p
 * ayload byte 6 (packet byte 22) is 0x01 when bypassed,
 * 0x00 when active
 */
public record EffectSettings(
        int slot,
        EffectModel model,
        int knob1,
        int knob2,
        int knob3,
        int knob4,
        int knob5,
        int knob6,
        boolean enabled
) {
}
