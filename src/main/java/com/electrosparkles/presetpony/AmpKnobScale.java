package com.electrosparkles.presetpony;

/**
 * Raw byte (0-255) -&gt; real-world amp panel display conversions for the main amp knobs.
 * Pulled out of {@code PresetPony} so these formulas are testable independent of any
 * Swing/UI dependency.
 */
public final class AmpKnobScale {

    private AmpKnobScale() {
    }

    /**
     * Raw 0-255 -&gt; the amp panel's displayed 1.0-10.0 scale (main EQ knobs: gain, volume,
     * treble, middle, bass, presence).
     */
    public static double mainEqValue(int raw) {
        return 1.0 + raw / 255.0 * 9.0;
    }

    /** {@link #mainEqValue} truncated down to the panel's actual 1-decimal display
     * resolution; the panel truncates rather than rounds. */
    public static double mainEqDisplayValue(int raw) {
        return Math.floor(mainEqValue(raw) * 10.0) / 10.0;
    }

    public static String formatMainEq(int raw) {
        return String.format("%.1f", mainEqDisplayValue(raw));
    }

    /**
     * Raw 0-255 -&gt; the amp panel's displayed -50% to +49% scale (Bias), centered exactly
     * at raw 128 (divisor 256, not 255). The panel shows a whole percent, truncated
     * downward, not a decimal.
     */
    public static int biasPercent(int raw) {
        return (int) Math.floor((raw - 128) * 100.0 / 256.0);
    }

    public static String formatBiasPercent(int raw) {
        return String.format("%+d%%", biasPercent(raw));
    }

    /**
     * Raw 0-255 -&gt; Fuse's Advanced amp controls display of 0% to 100%. USB gain is not
     * an amp-specific field: it's read/written as one {@code AmpSettings} value regardless
     * of which amp model is selected, so this formatting applies uniformly with no
     * per-amp-model branching needed.
     */
    public static double usbGainPercent(int raw) {
        return raw / 255.0 * 100.0;
    }

    public static String formatUsbGainPercent(int raw) {
        return String.format("%.1f%%", usbGainPercent(raw));
    }

    /**
     * Gain 2's "normal" scale (when it functions as an actual second gain knob rather
     * than Blend - see {@link AmpModel#SUPPORTS_GAIN2_BLEND}): a plain 0.0-10.0 dial,
     * unlike the 1.0-10.0 range used by the primary EQ knobs ({@link #mainEqValue}).
     * Gain 2 has no physical "floor" at raw 0, so it reads a true 0.0 there.
     */
    public static double gain2Value(int raw) {
        return raw / 255.0 * 10.0;
    }

    /** {@link #gain2Value} truncated down to 1-decimal display resolution, matching the
     * truncation behavior of the other main-scale knobs - see {@link #mainEqDisplayValue}. */
    public static double gain2DisplayValue(int raw) {
        return Math.floor(gain2Value(raw) * 10.0) / 10.0;
    }

    public static String formatGain2Value(int raw) {
        return String.format("%.1f", gain2DisplayValue(raw));
    }

    /**
     * Gain 2's alternate "Blend" scale, active only on the amps
     * {@link AmpModel#SUPPORTS_GAIN2_BLEND} lists (Fender '59 Bassman, British '70s /
     * Marshall Plexi). On those models the same Gain 2 wire byte instead drives a Blend
     * control, shown on Fuse's own panel as -50%/+50%, using the same formula as
     * {@link #biasPercent} (raw 128 = the 0% center). Kept as its own named method purely
     * for readability at the call site, since it's a semantically different control that
     * happens to share Bias's raw layout.
     */
    public static int gain2BlendPercent(int raw) {
        return biasPercent(raw);
    }

    public static String formatGain2BlendPercent(int raw) {
        return String.format("%+d%%", gain2BlendPercent(raw));
    }

    /**
     * Depth's scale: like USB Gain, exposed only on Fuse's Advanced panel, shown as a
     * plain 0%-100%, unscaled - same linear formula as {@link #usbGainPercent}. Kept as
     * its own named method for the same readability reason as {@link #gain2BlendPercent}.
     */
    public static double depthPercent(int raw) {
        return usbGainPercent(raw);
    }

    public static String formatDepthPercent(int raw) {
        return String.format("%.1f%%", depthPercent(raw));
    }

    /**
     * The Noise Gate dial has 6 wire values, 0-5: Off/Low/Mid/High/Max map to 0-4, and 5
     * is a distinct "Custom" value, at which point the Threshold and Depth controls
     * become independently editable rather than following the named-preset defaults
     * below. The amp's own physical dial shows this state as "User", matching Fuse's
     * "Custom" dropdown label.
     * <pre>
     *   Off:  gate=0, threshold=2, depth inconsistent (0/128/255 seen; not of
     *         consequence when the gate itself is off)
     *   Low:  gate=1, threshold=2, depth=0
     *   Mid:  gate=2, threshold=3, depth=255
     *   High: gate=3, threshold=4, depth=255
     *   Max:  gate=4, threshold=5, depth=255
     *   Custom (User): gate=5, threshold/depth independently set - no table default,
     *         by definition.
     * </pre>
     * Threshold/Depth default tables below only cover indices 0-4; Custom has no
     * default to look up and callers should never query this table for index 5.
     */
    public static final String[] NOISE_GATE_LABELS = {"Off", "Low", "Mid", "High", "Max", "Custom"};
    private static final int[] NOISE_GATE_THRESHOLD_DEFAULT = {2, 2, 3, 4, 5};
    private static final int[] NOISE_GATE_DEPTH_DEFAULT = {0x80, 0x00, 0xFF, 0xFF, 0xFF};

    public static int defaultThresholdForGate(int gateIndex) {
        return NOISE_GATE_THRESHOLD_DEFAULT[clampNamedGateIndex(gateIndex)];
    }

    public static int defaultDepthForGate(int gateIndex) {
        return NOISE_GATE_DEPTH_DEFAULT[clampNamedGateIndex(gateIndex)];
    }

    /** Is this raw gate value the Custom/User position (5), vs. one of the 5 named
     * presets (0-4)? */
    public static boolean isCustomGate(int gateIndex) {
        return gateIndex >= 5;
    }

    // Clamps to the 5 named presets only (0-4); Custom (5) has no table entry.
    private static int clampNamedGateIndex(int gateIndex) {
        return Math.max(0, Math.min(NOISE_GATE_THRESHOLD_DEFAULT.length - 1, gateIndex));
    }
}