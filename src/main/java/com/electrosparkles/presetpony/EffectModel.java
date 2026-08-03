package com.electrosparkles.presetpony;

/**
 * Effect models: wire ID, DSP slot group, display name, per-knob control
 * specs, and factory default knob values.
 * hasKnob6() is now derived (knobs[5] used) rather than a separate flag -
 * matches the source exactly: only the 4 echo/tape-delay variants use it.
 */
public enum EffectModel {
    EMPTY(0x00, -1, "Empty",
            new KnobSpec[]{KnobSpec.disabled(), KnobSpec.disabled(), KnobSpec.disabled(), KnobSpec.disabled(), KnobSpec.disabled(), KnobSpec.disabled()},
            new int[]{0, 0, 0, 0, 0, 0}),

    // ---- slot 0 - distortion/dynamics ----
    OVERDRIVE(0x3c, 0, "Overdrive",
            new KnobSpec[]{KnobSpec.slider("Level"), KnobSpec.slider("Gain"), KnobSpec.slider("Low"), KnobSpec.slider("Medium"), KnobSpec.slider("High"), KnobSpec.disabled()},
            new int[]{0x80, 0x80, 0x80, 0x80, 0x80, 0x00}),
    WAH(0x49, 0, "Wah",
            new KnobSpec[]{KnobSpec.slider("Mix"), KnobSpec.slider("Frequency"), KnobSpec.slider("Heel Freq"), KnobSpec.slider("Toe Freq"), KnobSpec.toggle("High Q"), KnobSpec.disabled()},
            new int[]{0xff, 0x80, 0x00, 0xff, 0x00, 0x00}), // knob4 slider->toggle:  "q (aka high q)" is on/off, matching WAH_MOD's already-correct toggle
    TOUCH_WAH(0x4a, 0, "Touch Wah",
            new KnobSpec[]{KnobSpec.slider("Mix"), KnobSpec.slider("Sensitivity"), KnobSpec.slider("Heel Freq"), KnobSpec.slider("Toe Freq"), KnobSpec.toggle("High Q"), KnobSpec.disabled()},
            new int[]{0xff, 0x80, 0x00, 0xff, 0x00, 0x00}), // knob4 slider->toggle: same fix as WAH above, matching TOUCH_WAH_MOD's already-correct toggle
    FUZZ(0x1a, 0, "Fuzz",
            new KnobSpec[]{KnobSpec.slider("Level"), KnobSpec.slider("Gain"), KnobSpec.slider("Octave"), KnobSpec.slider("Low"), KnobSpec.slider("High"), KnobSpec.disabled()},
            new int[]{0x80, 0x80, 0x80, 0x80, 0x80, 0x00}),
    FUZZ_TOUCH_WAH(0x1c, 0, "Fuzz Touch Wah",
            new KnobSpec[]{KnobSpec.slider("Level"), KnobSpec.slider("Gain"), KnobSpec.slider("Sensitivity"), KnobSpec.slider("Octave"), KnobSpec.slider("Peak"), KnobSpec.disabled()},
            new int[]{0x80, 0x80, 0x80, 0x80, 0x80, 0x80}),
    SIMPLE_COMP(0x88, 0, "Simple Compressor",
            new KnobSpec[]{KnobSpec.dropdown("Type", "Low", "Mid", "High", "Max"), KnobSpec.disabled(), KnobSpec.disabled(), KnobSpec.disabled(), KnobSpec.disabled(), KnobSpec.disabled()},
            new int[]{0x01, 0x00, 0x00, 0x00, 0x00, 0x00}),
    COMPRESSOR(0x07, 0, "Compressor",
            new KnobSpec[]{KnobSpec.slider("Level"), KnobSpec.slider("Threshold"), KnobSpec.slider("Ratio"), KnobSpec.slider("Attack"), KnobSpec.slider("Release"), KnobSpec.disabled()},
            new int[]{0x8d, 0x0f, 0x4f, 0x7f, 0x7f, 0x00}),
    RANGER_BOOST(0x0103, 0, "Ranger Boost",
            new KnobSpec[]{KnobSpec.slider("Level"), KnobSpec.slider("Gain"), KnobSpec.slider("Low"), KnobSpec.slider("Brightness"), KnobSpec.disabled(), KnobSpec.disabled()},
            new int[]{0x64, 0xba, 0x01, 0x9b, 0x00, 0x00}),
    GREENBOX(0xba, 0, "Greenbox",
            new KnobSpec[]{KnobSpec.slider("Level"), KnobSpec.slider("Gain"), KnobSpec.slider("Tone"), KnobSpec.slider("Blend"), KnobSpec.disabled(), KnobSpec.disabled()},
            new int[]{0x81, 0xb1, 0x8c, 0xff, 0x00, 0x00}),
    ORANGEBOX(0x0110, 0, "Orangebox",
            new KnobSpec[]{KnobSpec.slider("Level"), KnobSpec.slider("Distortion"), KnobSpec.slider("Tone"), KnobSpec.disabled(), KnobSpec.disabled(), KnobSpec.disabled()},
            new int[]{0x81, 0x81, 0x81, 0x00, 0x00, 0x00}),
    BLACKBOX(0x0111, 0, "Blackbox",
            new KnobSpec[]{KnobSpec.slider("Level"), KnobSpec.slider("Distortion"), KnobSpec.slider("Filter"), KnobSpec.disabled(), KnobSpec.disabled(), KnobSpec.disabled()},
            new int[]{0x81, 0x81, 0x56, 0x00, 0x00, 0x00}), // knob2 "High"->"Filter": confirmed against both real Fuse and the amp's own panel
    BIG_FUZZ(0x010f, 0, "Big Fuzz",
            new KnobSpec[]{KnobSpec.slider("Level"), KnobSpec.slider("Tone"), KnobSpec.slider("Sustain"), KnobSpec.disabled(), KnobSpec.disabled(), KnobSpec.disabled()},
            new int[]{0xac, 0xac, 0x73, 0x00, 0x00, 0x00}),

    // ---- slot 1 - modulation ----
    SINE_CHORUS(0x12, 1, "Sine Chorus",
            new KnobSpec[]{KnobSpec.slider("Level"), KnobSpec.slider("Rate"), KnobSpec.slider("Depth"), KnobSpec.slider("Avr Delay"), KnobSpec.slider("LR Phase"), KnobSpec.disabled()},
            new int[]{0xff, 0x0e, 0x19, 0x19, 0x80, 0x00}),
    TRIANGLE_CHORUS(0x13, 1, "Triangle Chorus",
            new KnobSpec[]{KnobSpec.slider("Level"), KnobSpec.slider("Rate"), KnobSpec.slider("Depth"), KnobSpec.slider("Avr Delay"), KnobSpec.slider("LR Phase"), KnobSpec.disabled()},
            new int[]{0x5d, 0x0e, 0x19, 0x19, 0x80, 0x00}),
    SINE_FLANGER(0x18, 1, "Sine Flanger",
            new KnobSpec[]{KnobSpec.slider("Level"), KnobSpec.slider("Rate"), KnobSpec.slider("Depth"), KnobSpec.slider("Feedback"), KnobSpec.slider("LR Phase"), KnobSpec.disabled()},
            new int[]{0xff, 0x0e, 0x80, 0x80, 0x80, 0x00}),
    TRIANGLE_FLANGER(0x19, 1, "Triangle Flanger",
            new KnobSpec[]{KnobSpec.slider("Level"), KnobSpec.slider("Rate"), KnobSpec.slider("Depth"), KnobSpec.slider("Feedback"), KnobSpec.slider("LR Phase"), KnobSpec.disabled()},
            new int[]{0xff, 0x00, 0xff, 0x33, 0x41, 0x00}),
    VIBRATONE(0x2d, 1, "Vibratone",
            new KnobSpec[]{KnobSpec.slider("Level"), KnobSpec.slider("Rotor"), KnobSpec.slider("Depth"), KnobSpec.slider("Feedback"), KnobSpec.slider("LR Phase"), KnobSpec.disabled()},
            new int[]{0xf4, 0xff, 0x27, 0xad, 0x82, 0x00}),
    VINTAGE_TREMOLO(0x40, 1, "Vintage Tremolo",
            new KnobSpec[]{KnobSpec.slider("Level"), KnobSpec.slider("Rate"), KnobSpec.slider("Duty Cycle"), KnobSpec.slider("Attack"), KnobSpec.slider("Release"), KnobSpec.disabled()},
            new int[]{0xdb, 0xad, 0x63, 0xf4, 0xf1, 0x00}), // live-validated against real hardware
    SINE_TREMOLO(0x41, 1, "Sine Tremolo",
            new KnobSpec[]{KnobSpec.slider("Level"), KnobSpec.slider("Rate"), KnobSpec.slider("Duty Cycle"), KnobSpec.slider("LFO Clipping"), KnobSpec.slider("Shape"), KnobSpec.disabled()},
            new int[]{0xdb, 0x99, 0x7d, 0x00, 0x00, 0x00}),
    RING_MODULATOR(0x22, 1, "Ring Modulator",
            new KnobSpec[]{KnobSpec.slider("Level"), KnobSpec.slider("Frequency"), KnobSpec.slider("Depth"), KnobSpec.dropdown("Shape", "Sine", "Triangle"), KnobSpec.slider("Phase"), KnobSpec.disabled()},
            new int[]{0xff, 0x80, 0x80, 0x80, 0x80, 0x00}),
    STEP_FILTER(0x29, 1, "Step Filter",
            new KnobSpec[]{KnobSpec.slider("Level"), KnobSpec.slider("Rate"), KnobSpec.slider("Resonance"), KnobSpec.slider("Min Freq"), KnobSpec.slider("Max Freq"), KnobSpec.disabled()},
            new int[]{0xff, 0x80, 0x80, 0x80, 0x80, 0x00}),
    PHASER(0x4f, 1, "Phaser",
            new KnobSpec[]{KnobSpec.slider("Level"), KnobSpec.slider("Rate"), KnobSpec.slider("Depth"), KnobSpec.slider("Feedback"), KnobSpec.dropdown("Shape", "Sine", "Triangle"), KnobSpec.disabled()},
            new int[]{0xfd, 0x00, 0xfd, 0xb8, 0x00, 0x00}),
    PITCH_SHIFTER(0x1f, 1, "Pitch Shifter",
            new KnobSpec[]{KnobSpec.slider("Mix"), KnobSpec.slider("Pitch", 255, -24.0, 24.0, " st"), KnobSpec.slider("Predelay"), KnobSpec.slider("Feedback"), KnobSpec.slider("Tone"), KnobSpec.disabled()},
            new int[]{0xc7, 0x3e, 0x80, 0x00, 0x00, 0x00}), // knob0/2/4 relabeled Level/Detune/Predelay -> Mix/Predelay/Tone: t
    WAH_MOD(0xf4, 1, "Wah",
            new KnobSpec[]{KnobSpec.slider("Mix"), KnobSpec.slider("Frequency"), KnobSpec.slider("Heel Freq"), KnobSpec.slider("Toe Freq"), KnobSpec.toggle("High Q"), KnobSpec.disabled()},
            new int[]{0xff, 0x81, 0x01, 0xff, 0x00, 0x00}),
    TOUCH_WAH_MOD(0xf5, 1, "Touch Wah",
            new KnobSpec[]{KnobSpec.slider("Mix"), KnobSpec.slider("Sensitivity"), KnobSpec.slider("Min Freq"), KnobSpec.slider("Max Freq"), KnobSpec.toggle("High Q"), KnobSpec.disabled()},
            new int[]{0xed, 0x81, 0x07, 0xff, 0x00, 0x00}),
    DIATONIC_PITCH_SHIFTER(0x101f, 1, "Diatonic Pitch Shifter",
            new KnobSpec[]{KnobSpec.slider("Mix"), KnobSpec.dropdown("Pitch", "-Oct", "-7th", "-6th", "-5th", "-4th", "-3rd", "-2nd", "None", "+2nd", "+3rd", "+4th", "+5th", "+6th", "+7th", "+Oct", "+9th", "+10th", "+11th", "+12th", "+13th", "+14th", "+Oct2"), KnobSpec.dropdown("Key", "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"), KnobSpec.dropdown("Scale", "Major", "Dorian", "Phrygian", "Lydian", "Mixolydian", "Minor", "Locrian", "Harmonic Minor", "Melodic Minor"), KnobSpec.slider("Tone"), KnobSpec.disabled()},
            new int[]{0x56, 0x09, 0x04, 0x05, 0xc8, 0x00}),

    // ---- slot 2 - delay ----
    MONO_DELAY(0x16, 2, "Mono Delay",
            new KnobSpec[]{KnobSpec.slider("Level"), KnobSpec.slider("Delay"), KnobSpec.slider("Feedback"), KnobSpec.slider("Brightness"), KnobSpec.slider("Attenuation"), KnobSpec.disabled()},
            new int[]{0xff, 0x80, 0x80, 0x80, 0x80, 0x00}),
    MONO_ECHO_FILTER(0x43, 2, "Mono Echo Filter",
            new KnobSpec[]{KnobSpec.slider("Level"), KnobSpec.slider("Delay"), KnobSpec.slider("Feedback"), KnobSpec.slider("Frequency"), KnobSpec.slider("Resonance"), KnobSpec.slider("In Level")},
            new int[]{0xff, 0x80, 0x80, 0x80, 0x80, 0x80}),
    STEREO_ECHO_FILTER(0x48, 2, "Stereo Echo Filter",
            new KnobSpec[]{KnobSpec.slider("Level"), KnobSpec.slider("Delay"), KnobSpec.slider("Feedback"), KnobSpec.slider("Frequency"), KnobSpec.slider("Resonance"), KnobSpec.slider("In Level")},
            new int[]{0x80, 0xb3, 0x80, 0x80, 0x80, 0x80}),
    MULTITAP_DELAY(0x44, 2, "Multitap Delay",
            new KnobSpec[]{KnobSpec.slider("Level"), KnobSpec.slider("Delay"), KnobSpec.slider("Feedback"), KnobSpec.slider("Brightness"), KnobSpec.dropdown("Mode", "1-2", "2-3", "1-3", "1-2-3"), KnobSpec.disabled()},
            new int[]{0xff, 0x80, 0x66, 0x80, 0x80, 0x00}),
    PING_PONG_DELAY(0x45, 2, "Ping-Pong Delay",
            new KnobSpec[]{KnobSpec.slider("Level"), KnobSpec.slider("Delay"), KnobSpec.slider("Feedback"), KnobSpec.slider("Brightness"), KnobSpec.slider("Stereo"), KnobSpec.disabled()},
            new int[]{0xff, 0x80, 0x80, 0x80, 0x80, 0x00}),
    DUCKING_DELAY(0x15, 2, "Ducking Delay",
            new KnobSpec[]{KnobSpec.slider("Level"), KnobSpec.slider("Delay"), KnobSpec.slider("Feedback"), KnobSpec.slider("Release"), KnobSpec.slider("Threshold"), KnobSpec.disabled()},
            new int[]{0xff, 0x80, 0x80, 0x80, 0x80, 0x00}),
    REVERSE_DELAY(0x46, 2, "Reverse Delay",
            new KnobSpec[]{KnobSpec.slider("Level"), KnobSpec.slider("Delay"), KnobSpec.slider("Feedback"), KnobSpec.slider("RFDBK"), KnobSpec.slider("Tone"), KnobSpec.disabled()},
            new int[]{0xff, 0x80, 0x80, 0x80, 0x80, 0x00}),
    TAPE_DELAY(0x2b, 2, "Tape Delay",
            new KnobSpec[]{KnobSpec.slider("Level"), KnobSpec.slider("Delay"), KnobSpec.slider("Feedback"), KnobSpec.slider("Flutter"), KnobSpec.slider("Brightness"), KnobSpec.slider("Stereo")},
            new int[]{0x7d, 0x1c, 0x00, 0x63, 0x80, 0x00}), // live-validated against real hardware
    STEREO_TAPE_DELAY(0x2a, 2, "Stereo Tape Delay",
            new KnobSpec[]{KnobSpec.slider("Level"), KnobSpec.slider("Delay"), KnobSpec.slider("Feedback"), KnobSpec.slider("Flutter"), KnobSpec.slider("Separation"), KnobSpec.slider("Brightness")},
            new int[]{0x7d, 0x88, 0x1c, 0x63, 0xff, 0x80}),

    // ---- slot 3 - reverb ----
    SMALL_HALL_REVERB(0x24, 3, "Small Hall Reverb",
            new KnobSpec[]{KnobSpec.slider("Level"), KnobSpec.slider("Decay"), KnobSpec.slider("Dwell"), KnobSpec.slider("Diffusion"), KnobSpec.slider("Tone"), KnobSpec.disabled()},
            new int[]{0x6e, 0x5d, 0x6e, 0x80, 0x91, 0x00}),
    LARGE_HALL_REVERB(0x3a, 3, "Large Hall Reverb",
            new KnobSpec[]{KnobSpec.slider("Level"), KnobSpec.slider("Decay"), KnobSpec.slider("Dwell"), KnobSpec.slider("Diffusion"), KnobSpec.slider("Tone"), KnobSpec.disabled()},
            new int[]{0x4f, 0x3e, 0x80, 0x05, 0xb0, 0x00}),
    SMALL_ROOM_REVERB(0x26, 3, "Small Room Reverb",
            new KnobSpec[]{KnobSpec.slider("Level"), KnobSpec.slider("Decay"), KnobSpec.slider("Dwell"), KnobSpec.slider("Diffusion"), KnobSpec.slider("Tone"), KnobSpec.disabled()},
            new int[]{0x80, 0x80, 0x80, 0x80, 0x80, 0x00}), // live-validated against real hardware
    LARGE_ROOM_REVERB(0x3b, 3, "Large Room Reverb",
            new KnobSpec[]{KnobSpec.slider("Level"), KnobSpec.slider("Decay"), KnobSpec.slider("Dwell"), KnobSpec.slider("Diffusion"), KnobSpec.slider("Tone"), KnobSpec.disabled()},
            new int[]{0x80, 0x80, 0x80, 0x80, 0x80, 0x00}),
    SMALL_PLATE_REVERB(0x4e, 3, "Small Plate Reverb",
            new KnobSpec[]{KnobSpec.slider("Level"), KnobSpec.slider("Decay"), KnobSpec.slider("Dwell"), KnobSpec.slider("Diffusion"), KnobSpec.slider("Tone"), KnobSpec.disabled()},
            new int[]{0x80, 0x80, 0x80, 0x80, 0x80, 0x00}),
    LARGE_PLATE_REVERB(0x4b, 3, "Large Plate Reverb",
            new KnobSpec[]{KnobSpec.slider("Level"), KnobSpec.slider("Decay"), KnobSpec.slider("Dwell"), KnobSpec.slider("Diffusion"), KnobSpec.slider("Tone"), KnobSpec.disabled()},
            new int[]{0x38, 0x80, 0x91, 0x80, 0xb6, 0x00}),
    AMBIENT_REVERB(0x4c, 3, "Ambient Reverb",
            new KnobSpec[]{KnobSpec.slider("Level"), KnobSpec.slider("Decay"), KnobSpec.slider("Dwell"), KnobSpec.slider("Diffusion"), KnobSpec.slider("Tone"), KnobSpec.disabled()},
            new int[]{0xff, 0x80, 0x80, 0x80, 0x80, 0x00}),
    ARENA_REVERB(0x4d, 3, "Arena Reverb",
            new KnobSpec[]{KnobSpec.slider("Level"), KnobSpec.slider("Decay"), KnobSpec.slider("Dwell"), KnobSpec.slider("Diffusion"), KnobSpec.slider("Tone"), KnobSpec.disabled()},
            new int[]{0xff, 0x80, 0x80, 0x80, 0x80, 0x00}),
    FENDER_63_SPRING_REVERB(0x21, 3, "Fender '63 Spring Reverb",
            new KnobSpec[]{KnobSpec.slider("Level"), KnobSpec.slider("Decay"), KnobSpec.slider("Dwell"), KnobSpec.slider("Diffusion"), KnobSpec.slider("Tone"), KnobSpec.disabled()},
            new int[]{0x80, 0x80, 0x80, 0x80, 0x80, 0x00}),
    FENDER_65_SPRING_REVERB(0x0b, 3, "Fender '65 Spring Reverb",
            new KnobSpec[]{KnobSpec.slider("Level"), KnobSpec.slider("Decay"), KnobSpec.slider("Dwell"), KnobSpec.slider("Diffusion"), KnobSpec.slider("Tone"), KnobSpec.disabled()},
            new int[]{0x80, 0x8b, 0x49, 0xff, 0x80, 0x00});

    public final int id;
    public final int dspSlotGroup;
    public final String displayName;
    public final KnobSpec[] knobs; // always length 6
    public final int[] defaultValues; // factory default raw values, always length 6

    EffectModel(int id, int dspSlotGroup, String displayName, KnobSpec[] knobs, int[] defaultValues) {
        this.id = id;
        this.dspSlotGroup = dspSlotGroup;
        this.displayName = displayName;
        this.knobs = knobs;
        this.defaultValues = defaultValues;
    }

    public boolean hasKnob6() {
        return knobs[5].isUsed();
    }

    /**
     * Effects present in offa/plug's shared effects_enum.h (which spans multiple
     * Mustang models) but confirmed to not exist on Mustang III v2's own panel or in real Fuse.
     *
     * Known caveat: if a legacy/community .fuse file (authored for a different
     * Mustang model) is imported with one of these IDs, FusePresetImporter/
     * EffectModel.fromId() will still resolve and display the model name correctly,
     * but JComboBox.setSelectedItem() has nothing to select since it's absent from
     * the combo's item list, so the picker will appear blank/unselected for that slot
     * even though CurrentPreset still holds the real model internally.
     */
    public static final java.util.EnumSet<EffectModel> NOT_SUPPORTED_ON_MUSTANG_III_V2 =
            java.util.EnumSet.of(FUZZ_TOUCH_WAH);

    public static EffectModel fromId(int id) {
        for (EffectModel m : values()) {
            if (m.id == id) return m;
        }
        return null;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
