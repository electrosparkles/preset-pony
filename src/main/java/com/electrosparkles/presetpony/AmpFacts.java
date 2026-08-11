package com.electrosparkles.presetpony;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;

/**
 * Loads amp-facts.properties and exposes per-amp factory defaults and cabinet
 * pairings. Replaces the hardcoded switch statements previously in AmpModel.
 *
 * Call {@link #loadDefault()} once at startup (from PresetPony.main, and at the
 * top of any test that exercises defaults). After that, use the static accessors.
 *
 * All default values in the properties file are raw bytes (0-255) already
 * converted at authoring time - no scale arithmetic happens here. Fields absent
 * for a given amp (e.g. presence on amps without that control) parse as -1 and
 * callers should treat -1 as "leave the current value untouched."
 */
public final class AmpFacts {

    private AmpFacts() {}

    private static final Map<AmpModel, CabinetModel> cabinets  = new EnumMap<>(AmpModel.class);
    private static final Map<AmpModel, AmpDefaults>  defaults  = new EnumMap<>(AmpModel.class);
    private static boolean loaded = false;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Loads amp-facts.properties from the conventional CWD-relative path used
     *  by the other Facts loaders in this project. Safe to call multiple times. */
    public static void loadDefault() {
        load(new File("src/main/resources/config/amp-facts.properties"));
    }

    public static void load(File file) {
        cabinets.clear();
        defaults.clear();
        loaded = false;

        if (!file.isFile()) {
            System.out.println("AmpFacts: " + file + " not found - falling back to AmpModel.defaultCabinet().");
            return;
        }
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(file)) {
            props.load(in);
        } catch (IOException e) {
            System.out.println("AmpFacts: failed to read " + file + ": " + e.getMessage());
            return;
        }

        int loaded_count = 0;
        for (AmpModel model : AmpModel.values()) {
            String prefix = model.name();

            CabinetModel cab = parseCabinet(props, prefix, model);
            AmpDefaults def = parseDefaults(props, prefix, model);

            if (cab != null && def != null) {
                cabinets.put(model, cab);
                defaults.put(model, def);
                loaded_count++;
            } else {
                System.out.println("AmpFacts: incomplete entry for " + model + " - skipping.");
            }
        }
        loaded = (loaded_count == AmpModel.values().length);
        if (!loaded) {
            System.out.println("AmpFacts: only " + loaded_count + "/" + AmpModel.values().length
                    + " amps fully loaded.");
        }
    }

    /**
     * The cabinet Fuse pairs with this amp by default.
     * Falls back to AmpModel.defaultCabinet() if the file was not loaded.
     */
    public static CabinetModel defaultCabinet(AmpModel model) {
        CabinetModel cab = cabinets.get(model);
        return (cab != null) ? cab : model.defaultCabinet();
    }

    /**
     * Factory-default knob values for this amp. Returns null if the file was
     * not loaded or this amp's entry was incomplete - callers should check and
     * fall back gracefully (e.g. don't apply defaults rather than crash).
     */
    public static AmpDefaults defaultsFor(AmpModel model) {
        return defaults.get(model);
    }

    /** True if the file loaded successfully and all 17 amps are present. */
    public static boolean isLoaded() {
        return loaded;
    }

    // -------------------------------------------------------------------------
    // Parsing helpers
    // -------------------------------------------------------------------------

    private static CabinetModel parseCabinet(Properties props, String prefix, AmpModel model) {
        String code = props.getProperty(prefix + ".cabinetCode", "").trim();
        if (code.isEmpty()) return null;
        // Studio Preamp legitimately has no cabinet - represented as CabinetModel.OFF.
        // "OFF" is a valid CabinetModel enum constant.
        try {
            return CabinetModel.valueOf(code);
        } catch (IllegalArgumentException e) {
            System.out.println("AmpFacts: unknown cabinetCode '" + code + "' for " + model);
            return null;
        }
    }

    private static AmpDefaults parseDefaults(Properties props, String prefix, AmpModel model) {
        try {
            int gain        = req(props, prefix, "gain");
            int volume      = req(props, prefix, "volume");
            int treble      = req(props, prefix, "treble");
            int middle      = req(props, prefix, "middle");
            int bass        = req(props, prefix, "bass");
            int noiseGate   = req(props, prefix, "noiseGate");
            int threshold   = req(props, prefix, "threshold");
            int depth       = req(props, prefix, "depth");
            int sag         = req(props, prefix, "sag");   // -1 for Studio Preamp
            int bias        = req(props, prefix, "bias");  // -1 for Studio Preamp

            // Optional per-amp extras - absent means the control doesn't exist on this model.
            int presence    = opt(props, prefix, "presence");
            int gain2       = opt(props, prefix, "gain2");
            int masterVolume = opt(props, prefix, "masterVolume");
            int brightness  = opt(props, prefix, "brightness");

            return new AmpDefaults(gain, volume, treble, middle, bass,
                    presence, gain2, masterVolume,
                    noiseGate, threshold, depth,
                    sag, bias, brightness);

        } catch (MissingFieldException e) {
            System.out.println("AmpFacts: " + model + " missing required field: " + e.field);
            return null;
        } catch (NumberFormatException e) {
            System.out.println("AmpFacts: " + model + " non-numeric value: " + e.getMessage());
            return null;
        }
    }

    /** Reads a required field. Throws MissingFieldException if absent. */
    private static int req(Properties props, String prefix, String field) {
        String key = prefix + ".default." + field;
        String raw = stripInlineComment(props.getProperty(key));
        if (raw == null) throw new MissingFieldException(field);
        return Integer.parseInt(raw.trim());
    }

    /** Reads an optional field. Returns -1 if absent (meaning: not applicable). */
    private static int opt(Properties props, String prefix, String field) {
        String key = prefix + ".default." + field;
        String raw = stripInlineComment(props.getProperty(key));
        if (raw == null) return -1;
        raw = raw.trim();
        if (raw.isEmpty()) return -1;
        return Integer.parseInt(raw);
    }

    /**
     * Properties.load() does NOT strip inline comments (everything after '#' on
     * a value line). Since the amp-facts.properties file uses "key=value  # comment"
     * style extensively, we strip them here before parsing.
     */
    private static String stripInlineComment(String value) {
        if (value == null) return null;
        int hash = value.indexOf('#');
        return (hash >= 0) ? value.substring(0, hash) : value;
    }

    private static final class MissingFieldException extends RuntimeException {
        final String field;
        MissingFieldException(String field) {
            super(field);
            this.field = field;
        }
    }
}
