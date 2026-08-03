package com.electrosparkles.presetpony;

public class VolumeTest {
    public static void main(String[] args) {
        try (MustangConnection conn = MustangConnection.connect()) {
            System.out.println("Connected.\n");

            CurrentPreset preset = conn.readCurrentPreset();
            printPreset(preset);

            // Nudge volume down a touch, same test as before, now via the real API.
            AmpSettings lower = preset.amp().withVolume(Math.max(0, preset.amp().volume() - 13));
            System.out.println("\n--> Writing volume " + preset.amp().volume() + " -> " + lower.volume() + "...");
            conn.writeAmpSettings(lower);
            System.out.println("Done - check the amp's panel.");

        } catch (IllegalStateException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private static void printPreset(CurrentPreset preset) {
        System.out.println("Preset: " + preset.name());
        AmpSettings a = preset.amp();
        System.out.println("Amp: " + a.model() + " | volume=" + a.volume() + " gain=" + a.gain()
                + " treble=" + a.treble() + " middle=" + a.middle() + " bass=" + a.bass()
                + " presence=" + a.presence() + " cabinet=" + a.cabinet() + " sag=" + a.sag());
        for (EffectSettings fx : preset.effects()) {
            System.out.println("Effect slot " + fx.slot() + ": " + fx.model()
                    + " knobs=[" + fx.knob1() + "," + fx.knob2() + "," + fx.knob3()
                    + "," + fx.knob4() + "," + fx.knob5() + "," + fx.knob6() + "]");
        }
    }
}
