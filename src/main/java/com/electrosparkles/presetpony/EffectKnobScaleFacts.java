package com.electrosparkles.presetpony;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Loads effect-knob-scales.properties and applies the facts to EffectModel.knobs at
 * startup, upgrading plain 0-max sliders to display-scaled ones where a fact is
 * known - reusing KnobSpec's existing displayMin/displayMax/displayUnit mechanism
 * (the same one Pitch Shifter's Pitch knob already used before this file existed).
 *
 */
public final class EffectKnobScaleFacts {

    private EffectKnobScaleFacts() {}

    /** Loads effect-knob-scales.properties from the current working directory. */
    public static void applyDefault() {
        apply(new File("src/main/resources/config/effect-knob-scales.properties"));
    }

    public static void apply(File file) {
        if (!file.isFile()) {
            System.out.println("Effect1KnobScaleFacts: " + file + " not found, skipping (raw sliders only).");
            return;
        }
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(file)) {
            props.load(in);
        } catch (IOException e) {
            System.out.println("EffectKnobScaleFacts: failed to read " + file + ": " + e.getMessage());
            return;
        }

        // Group flat "<EFFECT>.knob<N>.<field>=value" keys by their "<EFFECT>.knob<N>" prefix.
        Map<String, Map<String, String>> byPrefix = new LinkedHashMap<>();
        for (String key : props.stringPropertyNames()) {
            int knobMarker = key.lastIndexOf(".knob");
            if (knobMarker < 0) continue;
            int fieldDot = key.indexOf('.', knobMarker + 5);
            if (fieldDot < 0) continue;
            String prefix = key.substring(0, fieldDot);
            String field = key.substring(fieldDot + 1);
            byPrefix.computeIfAbsent(prefix, k -> new LinkedHashMap<>()).put(field, props.getProperty(key));
        }

        int applied = 0;
        for (Map.Entry<String, Map<String, String>> entry : byPrefix.entrySet()) {
            String prefix = entry.getKey(); // e.g. "COMPRESSOR.knob2"
            Map<String, String> fields = entry.getValue();
            if (!fields.containsKey("displayMin") || !fields.containsKey("displayMax")) {
                continue; // note-only or incomplete entry, nothing to apply
            }

            int knobMarker = prefix.lastIndexOf(".knob");
            String effectName = prefix.substring(0, knobMarker);
            int knobIdx;
            try {
                knobIdx = Integer.parseInt(prefix.substring(knobMarker + 5));
            } catch (NumberFormatException e) {
                System.out.println("EffectKnobScaleFacts: bad knob index in '" + prefix + "', skipping.");
                continue;
            }

            EffectModel model;
            try {
                model = EffectModel.valueOf(effectName);
            } catch (IllegalArgumentException e) {
                System.out.println("EffectKnobScaleFacts: unknown effect '" + effectName + "' in '" + prefix + "', skipping.");
                continue;
            }

            if (knobIdx < 0 || knobIdx >= model.knobs.length) {
                System.out.println("EffectKnobScaleFacts: knob index out of range in '" + prefix + "', skipping.");
                continue;
            }
            KnobSpec existing = model.knobs[knobIdx];
            if (!existing.isUsed()) {
                System.out.println("EffectKnobScaleFacts: '" + prefix + "' targets a disabled/unused knob, skipping.");
                continue;
            }

            try {
                double min = Double.parseDouble(fields.get("displayMin"));
                double max = Double.parseDouble(fields.get("displayMax"));
                String unit = fields.getOrDefault("unit", "");
                model.knobs[knobIdx] = new KnobSpec(existing.label(), existing.max(), existing.type(),
                        existing.dropdownOptions(), min, max, unit);
                applied++;
            } catch (NumberFormatException e) {
                System.out.println("EffectKnobScaleFacts: non-numeric displayMin/displayMax in '" + prefix + "', skipping.");
            }
        }
        // don't report if read successfully
        //System.out.println("EffectKnobScaleFacts: applied " + applied + " display-scale fact(s) from " + file);
    }
}
