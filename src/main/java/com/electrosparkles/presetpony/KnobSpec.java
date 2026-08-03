package com.electrosparkles.presetpony;

/**
 * Describes one physical knob's control type for a given effect model.
 *
 * Only SIMPLE_COMP's single knob has known named discrete values (Low/Mid/
 * High/Max) - other knobs with small ranges (e.g. Multitap Delay's "Mode",
 * max 3) are kept as plain sliders rather than inventing option names that
 * aren't confirmed.
 */
public record KnobSpec(String label, int max, KnobControlType type, String[] dropdownOptions,
                        Double displayMin, Double displayMax, String displayUnit) {

    public enum KnobControlType { SLIDER, TOGGLE, DROPDOWN }

    public static KnobSpec disabled() {
        return new KnobSpec("", 0, KnobControlType.SLIDER, null, null, null, null);
    }

    public static KnobSpec slider(String label) {
        return new KnobSpec(label, 255, KnobControlType.SLIDER, null, null, null, null);
    }

    public static KnobSpec slider(String label, int max) {
        return new KnobSpec(label, max, KnobControlType.SLIDER, null, null, null, null);
    }

    /**
     * A slider whose raw 0-max range maps linearly onto a real-world display range - e.g.
     * Pitch Shifter's Pitch knob: raw 0-255 -> -24.0 to +24.0 semitones. Purely a display
     * concern: the raw byte is still what's read from/written to the wire; only the knob
     * cell's label is scaled.
     */
    public static KnobSpec slider(String label, int max, double displayMin, double displayMax, String displayUnit) {
        return new KnobSpec(label, max, KnobControlType.SLIDER, null, displayMin, displayMax, displayUnit);
    }

    /** A knob confirmed in the source to only take 2 values (max=1). */
    public static KnobSpec toggle(String label) {
        return new KnobSpec(label, 1, KnobControlType.TOGGLE, null, null, null, null);
    }

    /** Confirmed named discrete options - currently only used for Simple Comp's "Type". */
    public static KnobSpec dropdown(String label, String... options) {
        return new KnobSpec(label, options.length - 1, KnobControlType.DROPDOWN, options, null, null, null);
    }

    public boolean isUsed() {
        return !label.isEmpty();
    }

    public boolean hasDisplayScale() {
        return displayMin != null && displayMax != null;
    }

    /** raw (0-max) -> the real-world display value. Linear interpolation only. */
    public double toDisplayValue(int raw) {
        return displayMin + (raw / (double) max) * (displayMax - displayMin);
    }

    /**
     * {@link #toDisplayValue} truncated down to 1 decimal, matching the amp panel's
     * confirmed truncate-not-round display behavior.  (see {@code AmpKnobScale}).
     */
    public double toDisplayValueTruncated(int raw) {
        return Math.floor(toDisplayValue(raw) * 10.0) / 10.0;
    }

    /** e.g. "+3.5 st" for Pitch Shifter's Pitch knob. Only valid when hasDisplayScale(). */
    public String formatDisplayValue(int raw) {
        return String.format("%+.1f%s", toDisplayValueTruncated(raw), displayUnit);
    }
}
