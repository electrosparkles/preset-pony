package com.electrosparkles.presetpony.tooling;

import com.electrosparkles.presetpony.AmpModel;
import com.electrosparkles.presetpony.AmpSettings;
import com.electrosparkles.presetpony.CurrentPreset;
import com.electrosparkles.presetpony.MustangConnection;

import java.util.Arrays;
import java.util.Map;
import java.util.Scanner;

/**
 * Interactive CLI harness for the British '60s Depth/Cut byte-sweep test
 * Not part of the main
 * app - run standalone: `java SpecificByteSweepTool`.
 *
 * Forces the amp to British '60s, writes a baseline, then steps through candidate
 * payload offsets one at a time, applying a couple of test values to each while
 * everything else stays fixed. Listen to the amp between each step and note what
 * changes - no USB capture tooling needed, since we're the ones generating the
 * write instead of trying to observe Fuse's.
 */
public class SpecificByteSweepTool {

    // The 5 bytes currently written as fixed constants for every amp model
    // (AmpModel.specificBytes, payload offsets 28,29,30,34,38) - the first, most
    // likely place Depth/Cut live. If none of these five turn out to do anything
    // audible, add other currently-unwritten payload offsets here (see the plan
    // doc's Method A, step 5 - PacketCodec.encodeAmpSettings() doesn't touch
    // offsets 1-15 or 39-47 at all today).
    private static final int[] CANDIDATE_OFFSETS = {28, 29, 30, 34, 38};
    private static final int[] TEST_VALUES = {0x00, 0xFF};
    private static final int UNKNOWN_BYTE24 = 0;

    public static void main(String[] args) throws Exception {
        Scanner in = new Scanner(System.in);
        System.out.println("Connecting...");
        MustangConnection conn = MustangConnection.connect();

        CurrentPreset current = conn.readCurrentPreset();
        AmpSettings a = current.amp();
        AmpSettings baseline = new AmpSettings(
                AmpModel.BRITISH_60S,
                a.volume(), a.gain(), a.gain2(), a.masterVolume(),
                a.treble(), a.middle(), a.bass(), a.presence(), UNKNOWN_BYTE24,
                a.depth(), a.bias(), a.noiseGate(), a.threshold(),
                AmpModel.BRITISH_60S.defaultCabinet(), a.sag(), a.brightness(), a.usbGain()
        );

        System.out.println("Writing British '60s baseline (fixed specific bytes: "
                + Arrays.toString(AmpModel.BRITISH_60S.specificBytes) + ").");
        System.out.println("Play a few notes, note the baseline tone, then press Enter to begin the sweep.");
        conn.writeAmpSettings(baseline);
        in.nextLine();

        for (int offset : CANDIDATE_OFFSETS) {
            for (int testVal : TEST_VALUES) {
                System.out.printf("Offset %d -> 0x%02X (baseline was 0x%02X). Play, listen, then press Enter.%n",
                        offset, testVal, baselineByteAt(offset));
                conn.writeAmpSettingsWithOverride(baseline, Map.of(offset, testVal));
                in.nextLine();
            }
            System.out.println("Restoring baseline before the next offset - press Enter once you've heard it settle.");
            conn.writeAmpSettings(baseline);
            in.nextLine();
        }

        System.out.println();
        System.out.println("Sweep complete. For each offset, record in british-60s-depth-cut-wire-test-plan.md:");
        System.out.println("  - no audible change, OR");
        System.out.println("  - a treble/brightness-cut type change (likely \"Cut\"), OR");
        System.out.println("  - some other perceptible change (likely \"Depth\")");
        conn.close();
    }

    /** The baseline value at a given candidate offset, for the printed before/after prompt. */
    private static int baselineByteAt(int offset) {
        int[] spec = AmpModel.BRITISH_60S.specificBytes; // order: offsets 28, 29, 30, 34, 38
        return switch (offset) {
            case 28 -> spec[0];
            case 29 -> spec[1];
            case 30 -> spec[2];
            case 34 -> spec[3];
            case 38 -> spec[4];
            default -> -1; // an offset added outside the known 5 - no baseline reference available
        };
    }
}
