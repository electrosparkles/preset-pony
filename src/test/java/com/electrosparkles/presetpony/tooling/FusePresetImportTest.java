package com.electrosparkles.presetpony.tooling;

import com.electrosparkles.presetpony.AmpSettings;
import com.electrosparkles.presetpony.CurrentPreset;
import com.electrosparkles.presetpony.EffectSettings;
import com.electrosparkles.presetpony.FusePresetImporter;

import java.nio.file.Path;

/**
 * Offline test - no amp connection needed. Reads a .fuse file and prints the
 * decoded CurrentPreset, so the import logic can be validated without needing live hardware.
 *
 * Usage: java FusePresetImportTest <path-to-.fuse-file>
 */
public class FusePresetImportTest {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java FusePresetImportTest <path-to-.fuse-file>");
            return;
        }

        try {
            CurrentPreset preset = FusePresetImporter.fromFile(Path.of(args[0]));
            print(preset);
        } catch (Exception e) {
            System.out.println("Import failed: " + e.getMessage());
            if (e.getCause() != null) {
                System.out.println("  caused by: " + e.getCause());
            }
        }
    }

    private static void print(CurrentPreset preset) {
        System.out.println("Preset: " + preset.name());
        AmpSettings a = preset.amp();
        System.out.println("Amp: " + a.model() + " (id=0x" + Integer.toHexString(a.model().id) + ")");
        System.out.println("  volume=" + a.volume() + " gain=" + a.gain() + " gain2=" + a.gain2()
                + " masterVolume=" + a.masterVolume());
        System.out.println("  treble=" + a.treble() + " middle=" + a.middle() + " bass=" + a.bass()
                + " presence=" + a.presence());
        System.out.println("  depth=" + a.depth() + " bias=" + a.bias() + " noiseGate=" + a.noiseGate()
                + " threshold=" + a.threshold());
        System.out.println("  cabinet=" + a.cabinet() + " sag=" + a.sag() + " brightness=" + a.brightness()
                + " usbGain=" + a.usbGain());

        for (EffectSettings fx : preset.effects()) {
            System.out.println("Effect slot " + fx.slot() + ": " + fx.model()
                    + " enabled=" + fx.enabled()
                    + " knobs=[" + fx.knob1() + "," + fx.knob2() + "," + fx.knob3()
                    + "," + fx.knob4() + "," + fx.knob5() + "," + fx.knob6() + "]");
        }
    }
}
