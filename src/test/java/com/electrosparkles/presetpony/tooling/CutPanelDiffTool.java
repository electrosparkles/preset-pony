package com.electrosparkles.presetpony.tooling;

import com.electrosparkles.presetpony.MustangConnection;

import java.util.Scanner;

/**
 * Read-only diagnostic harness for the British '60s "Cut" control
 * Not part of the main  * app - run standalone: `java CutPanelDiffTool`.
 *
 * Unlike SpecificByteSweepTool (which writes test values ourselves and listens for
 * an audible change), this one does no writing at all: Cut is a
 * virtual dial on the amp's own front panel, so we just read the full raw amp
 * payload before and after a real panel turn and diff every byte - the amp itself
 * is the ground truth here, the same way the background listener/live-sync feature
 * already picks up manual panel adjustments.
 *
 * Usage: make sure the amp model is already set to British '60s (via the app or a
 * previous read) before running this, note where Cut currently sits, run the tool,
 * follow the prompts, then physically turn the Cut dial when asked.
 */
public class CutPanelDiffTool {

    public static void main(String[] args) throws Exception {
        Scanner in = new Scanner(System.in);
        System.out.println("Connecting...");
        MustangConnection conn = MustangConnection.connect();

        System.out.println("Reading baseline. Make sure the amp is on British '60s and don't touch Cut yet.");
        System.out.println("Press Enter to read the baseline.");
        in.nextLine();
        conn.readCurrentPreset();
        byte[] before = conn.lastAmpPayloadRaw();
        System.out.println("Baseline payload: " + toHex(before));

        System.out.println();
        System.out.println("Now physically turn the Cut dial on the amp to a clearly different position");
        System.out.println("(the bigger the move, the easier the diff is to trust), then press Enter.");
        in.nextLine();

        conn.readCurrentPreset();
        byte[] after = conn.lastAmpPayloadRaw();
        System.out.println("After payload:    " + toHex(after));

        System.out.println();
        System.out.println("Differences (payload offset: before -> after):");
        boolean any = false;
        for (int i = 0; i < before.length; i++) {
            if (before[i] != after[i]) {
                System.out.printf("  offset %2d: 0x%02X -> 0x%02X%n", i, before[i] & 0xFF, after[i] & 0xFF);
                any = true;
            }
        }
        if (!any) {
            System.out.println("  (none found - try a bigger dial movement, confirm the amp model is really");
            System.out.println("   British '60s, and confirm Refresh/read is actually re-reading live panel");
            System.out.println("   state rather than a cached value)");
        } else {
            System.out.println();
            System.out.println("Record the changed offset(s) in british-60s-depth-cut-wire-test-plan.md.");
            System.out.println("If more than one offset changed, repeat with a different Cut position to see");
            System.out.println("which one moves consistently with Cut (vs. incidental noise/rounding).");
        }
        conn.close();
    }

    private static String toHex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x ", x));
        return sb.toString().trim();
    }
}
