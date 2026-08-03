package com.electrosparkles.presetpony;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads every preset slot from the amp and writes a single CSV file - one
 * row per preset, all amp + effect settings as columns (see
 * PresetCsvExporter). Mirrors FusePresetBackup's per-slot iteration and
 * defensive error handling (same lightweight per-slot read via
 * MustangConnection.readPresetAtSlot(), same "continue on failure, report
 * at the end" approach)
 */
public final class PresetCsvBackup {

    public static final int SLOT_COUNT = 100;

    private PresetCsvBackup() {
    }

    public interface Progress {
        default boolean isCancelled() {
            return false;
        }

        void onSlot(int slot, int totalSlots, String presetName);
    }

    public record CsvBackupResult(
            Path csvPath,
            int slotsAttempted,
            int slotsSucceeded,
            List<Integer> failedSlots,
            boolean cancelled,
            int restoredToSlot
    ) {
    }

    public static CsvBackupResult exportAll(MustangConnection conn, Path csvPath, int returnToSlot, Progress progress)
            throws IOException {
        List<Integer> failed = new ArrayList<>();
        boolean cancelled = false;
        int attempted = 0;
        int succeeded = 0;

        Path parent = csvPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        List<String> presetNames = new ArrayList<>();
        for (int i = 0; i < SLOT_COUNT; i++) {
            presetNames.add("");
        }

        StringBuilder csv = new StringBuilder(SLOT_COUNT * 200);
        csv.append(PresetCsvExporter.header()).append('\n');

        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if (progress != null && progress.isCancelled()) {
                cancelled = true;
                break;
            }

            attempted++;
            try {
                CurrentPreset preset = conn.readPresetAtSlot(slot, presetNames);
                while (presetNames.size() <= slot) {
                    presetNames.add("");
                }
                presetNames.set(slot, preset.name());

                csv.append(PresetCsvExporter.toCsvRow(preset)).append('\n');
                succeeded++;
                if (progress != null) {
                    progress.onSlot(slot, SLOT_COUNT, preset.name());
                }
            } catch (RuntimeException ex) {
                failed.add(slot);
            }
        }

        Files.writeString(csvPath, csv.toString(), StandardCharsets.UTF_8);

        try {
            conn.readPresetAtSlot(returnToSlot, List.of());
        } catch (RuntimeException ignored) {
            // Best effort - user may still be on the last read slot.
        }

        return new CsvBackupResult(csvPath, attempted, succeeded, List.copyOf(failed), cancelled, returnToSlot);
    }

    public static String suggestCsvFileName() {
        return "mustang-presets-" + LocalDate.now() + ".csv";
    }
}
