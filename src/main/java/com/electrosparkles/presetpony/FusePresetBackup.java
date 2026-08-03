package com.electrosparkles.presetpony;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Reads every preset slot from the amp and writes a zip archive of Fuse-compatible
 * {@code .fuse} XML files plus a small JSON manifest. Restore from zip is not
 * implemented yet — see docs/fuse-preset-format.md.
 */
public final class FusePresetBackup {

    public static final int SLOT_COUNT = 100;

    private FusePresetBackup() {}

    /**
     * Called after each slot is read and written. Override {@link #isCancelled()} to stop early.
     */
    public interface Progress {
        default boolean isCancelled() {
            return false;
        }

        void onSlot(int slot, int totalSlots, String presetName);
    }

    public record SlotRecord(int slot, String name, String file) {}

    public record BackupResult(
            Path zipPath,
            int slotsAttempted,
            int slotsSucceeded,
            List<Integer> failedSlots,
            boolean cancelled,
            int restoredToSlot
    ) {}

    /**
     * Switches through slots {@code 0 .. SLOT_COUNT-1}, reads each preset from the
     * amp, and writes a zip. Returns the amp to {@code returnToSlot} when finished
     * or cancelled (best effort on error).
     */
    public static BackupResult exportAll(MustangConnection conn, Path zipPath, int returnToSlot, Progress progress)
            throws IOException {
        List<SlotRecord> exported = new ArrayList<>();
        List<Integer> failed = new ArrayList<>();
        boolean cancelled = false;
        int attempted = 0;

        Path parent = zipPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (OutputStream out = Files.newOutputStream(zipPath);
             ZipOutputStream zos = new ZipOutputStream(out)) {

            List<String> presetNames = new ArrayList<>();
            for (int i = 0; i < SLOT_COUNT; i++) {
                presetNames.add("");
            }

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

                    String entryPath = FusePresetExporter.backupEntryPath(slot, preset.name());
                    byte[] xml = FusePresetExporter.toXml(preset).getBytes(StandardCharsets.UTF_8);

                    ZipEntry entry = new ZipEntry(entryPath);
                    zos.putNextEntry(entry);
                    zos.write(xml);
                    zos.closeEntry();

                    exported.add(new SlotRecord(slot, preset.name(), entryPath));
                    if (progress != null) {
                        progress.onSlot(slot, SLOT_COUNT, preset.name());
                    }
                } catch (RuntimeException ex) {
                    failed.add(slot);
                }
            }

            if (!exported.isEmpty()) {
                writeManifest(zos, exported, failed);
            }
        }

        try {
            conn.readPresetAtSlot(returnToSlot, List.of());
        } catch (RuntimeException ignored) {
            // Best effort — user may still be on last read slot.
        }

        return new BackupResult(
                zipPath,
                attempted,
                exported.size(),
                List.copyOf(failed),
                cancelled,
                returnToSlot
        );
    }

    public static String suggestZipFileName() {
        return "mustang-backup-" + java.time.LocalDate.now() + ".zip";
    }

    private static void writeManifest(ZipOutputStream zos, List<SlotRecord> exported, List<Integer> failed)
            throws IOException {
        StringBuilder json = new StringBuilder(4096);
        json.append("{\n");
        json.append("  \"format\": \"mustang-preset-backup\",\n");
        json.append("  \"formatVersion\": 1,\n");
        json.append("  \"created\": \"").append(Instant.now()).append("\",\n");
        json.append("  \"slotCount\": ").append(SLOT_COUNT).append(",\n");
        json.append("  \"exportedCount\": ").append(exported.size()).append(",\n");
        json.append("  \"failedSlots\": [");
        for (int i = 0; i < failed.size(); i++) {
            if (i > 0) json.append(", ");
            json.append(failed.get(i));
        }
        json.append("],\n");
        json.append("  \"presets\": [\n");
        for (int i = 0; i < exported.size(); i++) {
            SlotRecord r = exported.get(i);
            json.append("    {\"slot\": ").append(r.slot())
                    .append(", \"name\": \"").append(jsonEsc(r.name()))
                    .append("\", \"file\": \"").append(jsonEsc(r.file())).append("\"}");
            if (i + 1 < exported.size()) json.append(',');
            json.append('\n');
        }
        json.append("  ]\n");
        json.append("}\n");

        ZipEntry manifest = new ZipEntry("manifest.json");
        zos.putNextEntry(manifest);
        zos.write(json.toString().getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private static String jsonEsc(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
