package com.electrosparkles.presetpony;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Pure-logic engine for the Toybox tab's "Randomise" feature - see
 * docs/toybox-randomise-handover.md for the full spec. Deliberately kept
 * Swing/UI-free so it's testable the same way AmpKnobScale/KnobSpec are.
 *
 * Open questions carried over from the handover doc, resolved here as follows
 * (flagged in case they need revisiting with the user):
 * - Studio Preamp + Sag/Bias: skipped (left untouched) when Amp Tuning is
 *   randomised and the (possibly just-randomised) amp model is Studio Preamp -
 *   handover doc was "leaning yes", not confirmed.
 * - Gain 2 / Blend: moved from "Amp EQ" to "Amp tuning" - confirmed it lives
 *   on page 2 of the amp's own settings screen alongside the other tuning
 *   controls, not with the page-1 EQ knobs.
 * - Brightness: doc places it in "Amp tuning" but doesn't give it an explicit
 *   value range as it does Sag/Noise Gate/Threshold - treated as its real
 *   0/1 on-off domain (AmpSettings.brightness()), not a blind 0-255 roll.
 * - USB gain: excluded from randomisation entirely (always passed through
 *   unchanged) - not a useful randomisable parameter; it's a USB-recording-only
 *   level with no physical control on the amp, so a random value here doesn't
 *   change what you hear.
 * - Effect "enabled": not discussed in the spec at all - defaults to true for
 *   any freshly-randomised real effect model, false for EMPTY, matching the
 *   existing UI convention in updateEmptySlotState().
 */
public final class RandomiseEngine {

    private RandomiseEngine() {
    }

    /**
     * Which categories to leave untouched. For effect slots 5-8 (Stomp/Mod/Delay/
     * Reverb), keepEffectModel[slot] and keepEffectSettings[slot] combine per the
     * handover doc's 3-state rule:
     * - model=false             -> new random model + new random knobs
     * - model=true, settings=false -> same model, new random knobs
     * - model=true, settings=true  -> slot untouched entirely
     * (settings=true with model=false is not a reachable UI state - the settings
     * checkbox is disabled whenever the effect checkbox is unchecked - but if it
     * ever arrives that way here, model=false wins: settings can't be "kept" while
     * the model itself changes.)
     */
    public record KeepFlags(
            boolean keepAmpModel,
            boolean keepAmpEq,
            boolean keepAmpTuning,
            boolean keepCab,
            boolean pairedCab,
            boolean[] keepEffectModel,   // length 4, indexed 0-3 (stomp/mod/delay/reverb)
            boolean[] keepEffectSettings // length 4, same indexing
    ) {
        public KeepFlags {
            if (keepEffectModel.length != 4 || keepEffectSettings.length != 4) {
                throw new IllegalArgumentException("keepEffectModel/keepEffectSettings must both be length 4");
            }
        }

        /** Convenience: nothing kept, no pairing - "full random" one-liner for callers/tests. */
        public static KeepFlags none() {
            return new KeepFlags(false, false, false, false, false,
                    new boolean[]{false, false, false, false}, new boolean[]{false, false, false, false});
        }
    }

    /** Randomises amp settings, effect settings, or both, per keep - see class javadoc. */
    public static CurrentPreset randomise(CurrentPreset current, KeepFlags keep, Random rng) {
        AmpSettings newAmp = randomiseAmp(current.amp(), keep, rng);
        EffectSettings[] newEffects = new EffectSettings[4];
        for (int slot = 0; slot < 4; slot++) {
            newEffects[slot] = randomiseEffect(current.effects()[slot],
                    keep.keepEffectModel()[slot], keep.keepEffectSettings()[slot], rng);
        }
        return new CurrentPreset(current.presetNumber(), current.name(), newAmp, newEffects, current.presetNames());
    }

    public static AmpSettings randomiseAmp(AmpSettings cur, KeepFlags keep, Random rng) {
        AmpModel model = keep.keepAmpModel() ? cur.model() : randomElement(AmpModel.values(), rng);

        CabinetModel cabinet;
        if (keep.keepCab()) {
            cabinet = cur.cabinet();
        } else if (keep.pairedCab() && !keep.keepAmpModel()) {
            // Paired only makes sense when Amp is also actually being randomised -
            // if Amp is kept, there's no "new amp model" to pair the cab against.
            cabinet = model.defaultCabinet();
        } else {
            cabinet = randomElement(CabinetModel.values(), rng);
        }

        int volume = keep.keepAmpEq() ? cur.volume() : rng.nextInt(256);
        int gain = keep.keepAmpEq() ? cur.gain() : rng.nextInt(256);
        int treble = keep.keepAmpEq() ? cur.treble() : rng.nextInt(256);
        int middle = keep.keepAmpEq() ? cur.middle() : rng.nextInt(256);
        int bass = keep.keepAmpEq() ? cur.bass() : rng.nextInt(256);
        int presence = keep.keepAmpEq() ? cur.presence() : rng.nextInt(256);

        boolean skipSagBias = AmpModel.STUDIO_PREAMP_HAS_NO_SAG_BIAS.contains(model);
        int sag = (keep.keepAmpTuning() || skipSagBias) ? cur.sag() : rng.nextInt(3);
        int bias = (keep.keepAmpTuning() || skipSagBias) ? cur.bias() : rng.nextInt(256);
        int gain2 = keep.keepAmpTuning() ? cur.gain2() : rng.nextInt(256); // page 2 of the amp's own screen, alongside tuning

        int noiseGate;
        int threshold;
        int depth;
        if (keep.keepAmpTuning()) {
            noiseGate = cur.noiseGate();
            threshold = cur.threshold();
            depth = cur.depth();
        } else {
            noiseGate = rng.nextInt(6); // 0-5 uniform, per handover doc
            if (AmpKnobScale.isCustomGate(noiseGate)) {
                threshold = rng.nextInt(10); // 0-9
                depth = rng.nextInt(256);
            } else {
                // Named preset - Threshold/Depth follow the gate, not independently random.
                threshold = AmpKnobScale.defaultThresholdForGate(noiseGate);
                depth = AmpKnobScale.defaultDepthForGate(noiseGate);
            }
        }

        int usbGain = cur.usbGain(); // never randomised - see class javadoc
        int masterVolume = keep.keepAmpTuning() ? cur.masterVolume() : rng.nextInt(256);
        int brightness = keep.keepAmpTuning() ? cur.brightness() : rng.nextInt(2);

        return new AmpSettings(model, volume, gain, gain2, masterVolume, treble, middle, bass, presence,
                cur.unknown24(), depth, bias, noiseGate, threshold, cabinet, sag, brightness, usbGain);
    }

    /**
     * Randomises one effect slot per the keep-effect/keep-settings 3-state rule.
     * cur.slot() (the fxSlotId, 0-7, encoding pre/post-preamp) is always preserved -
     * FX Loop routing is a separate, unrelated concern the Toybox spec doesn't touch.
     */
    public static EffectSettings randomiseEffect(EffectSettings cur, boolean keepModel, boolean keepSettings, Random rng) {
        if (keepModel && keepSettings) {
            return cur; // untouched entirely
        }

        EffectModel model;
        if (keepModel) {
            model = (cur.model() != null) ? cur.model() : EffectModel.EMPTY;
        } else {
            int groupIndex = ((cur.slot() % 4) + 4) % 4; // fxSlotId -> dspSlotGroup index (0-3)
            model = randomEffectModelForGroup(groupIndex, rng);
        }

        int[] knobs = randomKnobsForModel(model, rng);
        boolean enabled = model != EffectModel.EMPTY;

        return new EffectSettings(cur.slot(), model, knobs[0], knobs[1], knobs[2], knobs[3], knobs[4], knobs[5], enabled);
    }

    /** EMPTY + every model whose dspSlotGroup matches, excluding models not supported on Mustang III v2. */
    private static EffectModel randomEffectModelForGroup(int groupIndex, Random rng) {
        List<EffectModel> pool = new ArrayList<>();
        pool.add(EffectModel.EMPTY);
        for (EffectModel m : EffectModel.values()) {
            if (m.dspSlotGroup == groupIndex && !EffectModel.NOT_SUPPORTED_ON_MUSTANG_III_V2.contains(m)) {
                pool.add(m);
            }
        }
        return pool.get(rng.nextInt(pool.size()));
    }

    /** One value per knob (0-5), respecting each KnobSpec's real type/range; unused (disabled) knobs stay 0. */
    private static int[] randomKnobsForModel(EffectModel model, Random rng) {
        int[] values = new int[6];
        for (int k = 0; k < 6; k++) {
            KnobSpec spec = model.knobs[k];
            if (!spec.isUsed()) {
                values[k] = 0;
                continue;
            }
            values[k] = switch (spec.type()) {
                case TOGGLE -> rng.nextInt(2);
                case DROPDOWN -> rng.nextInt(spec.dropdownOptions().length);
                case SLIDER -> rng.nextInt(spec.max() + 1);
            };
        }
        return values;
    }

    private static <T> T randomElement(T[] values, Random rng) {
        return values[rng.nextInt(values.length)];
    }
}
