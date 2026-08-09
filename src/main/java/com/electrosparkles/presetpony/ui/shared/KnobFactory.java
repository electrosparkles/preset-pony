package com.electrosparkles.presetpony.ui.shared;

import com.electrosparkles.presetpony.AmpKnobScale;
import com.electrosparkles.presetpony.KnobSpec;

import javax.swing.*;
import java.awt.*;
import java.util.function.IntFunction;

/**
 * Factory and utility methods for creating knob controls (sliders, labels, compound panels).
 * 
 * These utilities are used across multiple tabs to maintain consistent styling and behavior.
 * Extracted here to avoid duplication and centralize control-construction logic.
 */
public final class KnobFactory {
    private KnobFactory() {
        // Utility class, non-instantiable
    }

    /**
     * Creates a standard knob slider: 0-255 range, major tick spacing every 64 units,
     * ticks drawn. Used for most amp and effect knobs.
     */
    public static JSlider newKnobSlider() {
        JSlider s = new JSlider(0, 255, 0);
        s.setMajorTickSpacing(64);
        s.setPaintTicks(true);
        return s;
    }

    /**
     * Labeled slider showing the name and real-time formatted value.
     * Used for main EQ knobs (volume, gain, treble, middle, bass, presence).
     * 
     * @param name the control label
     * @param slider the slider to wrap
     * @return a JPanel containing name label (top) and slider (center)
     */
    public static JPanel labeledScaled(String name, JSlider slider) {
        return labeledScaled(new JLabel(name), slider);
    }

    /**
     * Labeled slider variant that takes an existing JLabel, allowing the label text
     * to be updated dynamically without rebuilding the panel.
     * Used when a label's text depends on amp model (e.g., Presence vs Cut on British '60s).
     * 
     * @param nameLabel the label to display (caller retains reference for updates)
     * @param slider the slider to wrap
     * @return a JPanel containing name label (top) and slider (center)
     */
    public static JPanel labeledScaled(JLabel nameLabel, JSlider slider) {
        JLabel valueLabel = new JLabel(AmpKnobScale.formatMainEq(slider.getValue()), SwingConstants.RIGHT);
        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.add(nameLabel, BorderLayout.WEST);
        titleRow.add(valueLabel, BorderLayout.EAST);

        JPanel p = new JPanel(new BorderLayout());
        p.add(titleRow, BorderLayout.NORTH);
        p.add(slider, BorderLayout.CENTER);

        slider.addChangeListener(e -> {
            String scaled = AmpKnobScale.formatMainEq(slider.getValue());
            valueLabel.setText(scaled);
            slider.setToolTipText("raw " + slider.getValue() + " -> " + scaled);
        });
        slider.setToolTipText("raw " + slider.getValue() + " -> " + AmpKnobScale.formatMainEq(slider.getValue()));
        return p;
    }

    /**
     * Compact slider + trailing value label for tight 2-column grids (otherParams).
     * The formatter renders the raw slider value using amp-specific display scales
     * (formatBiasPercent, formatDepthPercent, etc.).
     * 
     * @param slider the slider to wrap
     * @param formatter function to convert raw value to display string
     * @return a JPanel containing slider (center) and value label (east)
     */
    public static JPanel compactSliderWithValue(JSlider slider, IntFunction<String> formatter) {
        JLabel valueLabel = new JLabel(formatter.apply(slider.getValue()), SwingConstants.RIGHT);
        valueLabel.setPreferredSize(new Dimension(60, valueLabel.getPreferredSize().height));
        JPanel p = new JPanel(new BorderLayout(4, 0));
        p.add(slider, BorderLayout.CENTER);
        p.add(valueLabel, BorderLayout.EAST);
        slider.addChangeListener(e -> {
            String text = formatter.apply(slider.getValue());
            valueLabel.setText(text);
            slider.setToolTipText("raw " + slider.getValue() + " -> " + text);
        });
        slider.setToolTipText("raw " + slider.getValue() + " -> " + formatter.apply(slider.getValue()));
        return p;
    }

    /**
     * Renders a knob spec's label for display in a knob cell.
     * - For sliders: renders name and current value as stacked HTML (name above, value below)
     * - For dropdowns/toggles: just the label
     * - For unused specs: a single space
     * 
     * @param spec the knob specification
     * @param rawValue the current raw value from the slider/dropdown/toggle
     * @return the label text (possibly HTML) for the knob cell
     */
    public static String knobLabelText(KnobSpec spec, int rawValue) {
        if (!spec.isUsed()) return " ";
        if (spec.type() == KnobSpec.KnobControlType.SLIDER) {
            return twoLineCenteredHtml(spec.label(), knobDisplayValueText(spec, rawValue));
        }
        return spec.label();
    }

    /**
     * Renders a raw slider value as its display string.
     * If the spec has a confirmed real-world scale (e.g., Pitch Shifter's Pitch range),
     * uses that formatter. Otherwise returns a plain 0-100% percentage.
     * 
     * @param spec the knob specification
     * @param rawValue the current raw value (0 to spec.max())
     * @return the display string for the value
     */
    public static String knobDisplayValueText(KnobSpec spec, int rawValue) {
        if (spec.hasDisplayScale()) {
            return spec.formatDisplayValue(rawValue);
        }
        return String.format("%.1f%%", rawValue * 100.0 / spec.max());
    }

    /**
     * Wraps two lines of text in centered HTML so they stack vertically
     * (e.g., knob label above, value below) instead of running together horizontally.
     * 
     * @param topLine the first line
     * @param bottomLine the second line
     * @return HTML markup with centered, stacked layout
     */
    public static String twoLineCenteredHtml(String topLine, String bottomLine) {
        return "<html><div style='text-align:center;'>" + topLine + "<br>" + bottomLine + "</div></html>";
    }

    /**
     * Creates a simple labeled panel (name above slider, no value display).
     * Less commonly used than labeledScaled; kept for backward compatibility.
     * 
     * @param name the control label
     * @param slider the slider to wrap
     * @return a JPanel with label (top) and slider (center)
     */
    public static JPanel labeled(String name, JSlider slider) {
        JPanel p = new JPanel(new BorderLayout());
        p.add(new JLabel(name), BorderLayout.NORTH);
        p.add(slider, BorderLayout.CENTER);
        return p;
    }
}
