package com.electrosparkles.presetpony;

import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Pure-logic scanning and validation for the Preset Explorer tab.
 * Separated from the Swing UI so it can be tested offline without a display.
 *
 * <p>Three status tiers:</p>
 * <ul>
 *   <li>{@link ValidationStatus#VALID}   — parsed successfully; preset is ready to apply.</li>
 *   <li>{@link ValidationStatus#WARNING} — ProductId mismatch (different Mustang model);
 *       amber indicator, not loadable until a bypass-parse path is added.</li>
 *   <li>{@link ValidationStatus#INVALID} — bad XML, empty, too large, missing sections, IO error;
 *       greyed out, not clickable.</li>
 * </ul>
 */
public final class PresetExplorerValidator {

    /** Three-tier validation status. */
    public enum ValidationStatus { VALID, WARNING, BYPASSED, INVALID }

    /** The outcome of validating a single file. */
    public static final class ValidationResult {
        public final ValidationStatus status;
        public final String detail;       // error/warning message; empty string when VALID
        public final CurrentPreset preset; // non-null only when VALID
        public final String presetName;   // preset display name; empty string when unavailable

        private ValidationResult(ValidationStatus status, String detail, CurrentPreset preset, String presetName) {
            this.status     = status;
            this.detail     = detail != null ? detail : "";
            this.preset     = preset;
            this.presetName = (presetName != null && !presetName.isBlank()) ? presetName : (preset != null && preset.name() != null ? preset.name() : "");
        }

        private ValidationResult(ValidationStatus status, String detail, CurrentPreset preset) {
            this(status, detail, preset, "");
        }

        public static ValidationResult valid(CurrentPreset preset) {
            return new ValidationResult(ValidationStatus.VALID, "", preset);
        }

        public static ValidationResult warning(String detail) {
            return new ValidationResult(ValidationStatus.WARNING, detail, null);
        }

        public static ValidationResult invalid(String detail) {
            return new ValidationResult(ValidationStatus.INVALID, detail, null);
        }
    }

    private PresetExplorerValidator() {}

    /**
     * Extract preset name from a .fuse file without full validation.
     * Used to get preset names even for WARNING (ProductId mismatch) files.
     */
    private static String extractNameFromFile(Path path) {
        try {
            String xml = Files.readString(path, java.nio.charset.StandardCharsets.UTF_8);
            // Simple regex to extract name attribute from Info element
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("<Info[^>]*name=\"([^\"]*)\"");
            java.util.regex.Matcher matcher = pattern.matcher(xml);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {
            // Ignore - return empty string if extraction fails
        }
        return "";
    }

    /**
     * Returns all .fuse files in {@code dir} (flat scan, case-insensitive extension match),
     * sorted alphabetically (case-insensitive). Does not recurse into subdirectories.
     *
     * @throws IOException if {@code dir} cannot be listed.
     */
    public static List<Path> discoverFuseFiles(Path dir) throws IOException {
        return Files.list(dir)
                .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".fuse"))
                .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
                .collect(Collectors.toList());
    }

    /**
     * Attempts to parse a single file via {@link FusePresetImporter}.
     * Returns a {@link ValidationResult} with status and detail populated.
     * Safe to call from a background thread.
     */
    public static ValidationResult validate(Path path) {
        try {
            CurrentPreset preset = FusePresetImporter.fromFile(path);
            return ValidationResult.valid(preset);
        } catch (IllegalArgumentException ex) {
            String msg = ex.getMessage() != null ? ex.getMessage() : ex.toString();
            // ProductId mismatch = a different Mustang model (not corrupt) → WARNING.
            if (msg.contains("ProductId")) {
                // Extract preset name even though we're rejecting due to ProductId
                String name = extractNameFromFile(path);
                return new ValidationResult(ValidationStatus.WARNING, msg, null, name);
            }
            return ValidationResult.invalid(msg);
        } catch (IOException ex) {
            return ValidationResult.invalid("IO error: " + ex.getMessage());
        }
    }

    /**
     * Attempts to parse a single file via {@link FusePresetImporter},
     * skipping ProductId validation (used when bypass-warnings is enabled).
     * Returns a {@link ValidationResult} with status and detail populated.
     * Safe to call from a background thread.
     */
    public static ValidationResult validateIgnoringWarnings(Path path) {
        try {
            CurrentPreset preset = FusePresetImporter.fromFileIgnoringProductId(path);
            return ValidationResult.valid(preset);
        } catch (IllegalArgumentException ex) {
            return ValidationResult.invalid(ex.getMessage() != null ? ex.getMessage() : ex.toString());
        } catch (IOException ex) {
            return ValidationResult.invalid("IO error: " + ex.getMessage());
        }
    }
}
