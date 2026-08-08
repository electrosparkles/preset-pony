package com.electrosparkles.presetpony;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * File-backed storage for {@link PedalboardSet}s — see docs/pedalboard-sets-plan.md for
 * the format, folder, and MRU design. Swing-free and directory-parameterized throughout
 * so it's testable against a temp dir, same as the rest of this project's I/O classes.
 */
public final class PedalboardSetStore {

    public static final String FORMAT = "preset-pony-pedalboard-set";
    public static final int FORMAT_VERSION = 1;
    public static final String FILE_SUFFIX = ".pbset.json";
    public static final int DEFAULT_MRU_CAPACITY = 6;

    private static final String RECENT_FILE_NAME = "recent.json";

    private PedalboardSetStore() {
    }

    public static Path defaultDirectory() {
        return Paths.get(System.getProperty("user.home"), ".preset-pony", "pedalboard-sets");
    }

    // ---- Save / load ----

    /** Writes a brand-new file (slugified name, collision-suffixed), and records it as
     * the most-recently-used entry. Returns the path written. */
    public static Path save(Path dir, PedalboardSet set) throws IOException {
        Files.createDirectories(dir);
        String fileName = uniqueFileName(dir, slugify(set.name()));
        Path file = dir.resolve(fileName);
        Files.writeString(file, toJson(set), StandardCharsets.UTF_8);
        touchRecent(dir, file, DEFAULT_MRU_CAPACITY);
        return file;
    }

    /** Overwrites an existing file in place (same path, same name) — used when re-saving
     * over a set the user already has loaded, rather than creating a new file/name. */
    public static void overwrite(Path file, PedalboardSet set) throws IOException {
        PedalboardSet touched = set.withEffects(set.effects()); // same content, modified bumped to now
        Files.writeString(file, toJson(touched), StandardCharsets.UTF_8);
        Path dir = file.getParent();
        if (dir != null) touchRecent(dir, file, DEFAULT_MRU_CAPACITY);
    }

    public static PedalboardSet load(Path file) throws IOException {
        PedalboardSet set = readOnly(file);
        Path dir = file.getParent();
        if (dir != null) touchRecent(dir, file, DEFAULT_MRU_CAPACITY);
        return set;
    }

    /** Same as {@link #load(Path)} but doesn't touch the MRU ring - for browsing/listing
     * (e.g. populating a display cache) where reading the file shouldn't itself count as
     * "using" that set. */
    public static PedalboardSet peek(Path file) throws IOException {
        return readOnly(file);
    }

    private static PedalboardSet readOnly(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        return fromJson(text);
    }

    public static void delete(Path file) throws IOException {
        Files.deleteIfExists(file);
        Path dir = file.getParent();
        if (dir != null) {
            List<Path> current = new ArrayList<>(recent(dir));
            if (current.remove(file)) {
                writeRecent(dir, current);
            }
        }
    }

    /** All {@code .pbset.json} files in {@code dir}, alphabetical by file name. Empty list
     * (not an error) if the directory doesn't exist yet - nothing saved there yet. */
    public static List<Path> list(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return List.of();
        try (var stream = Files.list(dir)) {
            return stream
                    .filter(p -> p.getFileName().toString().endsWith(FILE_SUFFIX))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .collect(Collectors.toList());
        }
    }

    // ---- Most-recently-used, fixed-capacity ring ----

    private static Path recentFilePath(Path dir) {
        return dir.resolve(RECENT_FILE_NAME);
    }

    /** Newest-first list of set file paths, capped at whatever capacity it was last written
     * with. Entries pointing at since-deleted files are silently dropped, not surfaced as
     * an error - this list is a shortcut, the folder listing is the source of truth. */
    @SuppressWarnings("unchecked")
    public static List<Path> recent(Path dir) throws IOException {
        Path recentFile = recentFilePath(dir);
        if (!Files.exists(recentFile)) return List.of();
        Object parsed = Json.parse(Files.readString(recentFile, StandardCharsets.UTF_8));
        List<Object> raw = (List<Object>) parsed;
        List<Path> out = new ArrayList<>();
        for (Object o : raw) {
            Path p = Paths.get((String) o);
            if (Files.exists(p)) out.add(p);
        }
        return out;
    }

    /** Moves {@code file} to the front of the MRU ring, evicting the oldest entry once
     * {@code capacity} is exceeded - a fixed-size ring, not an unbounded history. */
    public static void touchRecent(Path dir, Path file, int capacity) throws IOException {
        List<Path> current = new ArrayList<>(recent(dir));
        current.remove(file);
        current.add(0, file);
        while (current.size() > capacity) {
            current.remove(current.size() - 1);
        }
        writeRecent(dir, current);
    }

    private static void writeRecent(Path dir, List<Path> entries) throws IOException {
        List<Object> asStrings = entries.stream().map(p -> (Object) p.toString()).collect(Collectors.toList());
        Files.createDirectories(dir);
        Files.writeString(recentFilePath(dir), Json.write(asStrings), StandardCharsets.UTF_8);
    }

    // ---- JSON mapping ----

    static String toJson(PedalboardSet set) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("format", FORMAT);
        root.put("formatVersion", (double) FORMAT_VERSION);
        root.put("name", set.name());
        root.put("created", set.created().toString());
        root.put("modified", set.modified().toString());

        List<Object> effects = new ArrayList<>();
        for (EffectSettings fx : set.effects()) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("slot", (double) fx.slot());
            EffectModel model = (fx.model() != null) ? fx.model() : EffectModel.EMPTY;
            e.put("model", model.name());
            e.put("enabled", fx.enabled());
            List<Object> knobs = List.of(
                    (double) fx.knob1(), (double) fx.knob2(), (double) fx.knob3(),
                    (double) fx.knob4(), (double) fx.knob5(), (double) fx.knob6());
            e.put("knobs", knobs);
            effects.add(e);
        }
        root.put("effects", effects);
        return Json.write(root);
    }

    @SuppressWarnings("unchecked")
    static PedalboardSet fromJson(String text) {
        Object parsed;
        try {
            parsed = Json.parse(text);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Not valid JSON: " + ex.getMessage(), ex);
        }
        if (!(parsed instanceof Map)) {
            throw new IllegalArgumentException("Expected a JSON object at the top level");
        }
        Map<String, Object> root = (Map<String, Object>) parsed;

        String format = (String) root.get("format");
        if (!FORMAT.equals(format)) {
            throw new IllegalArgumentException("Not a " + FORMAT + " file (found: " + format + ")");
        }

        String name = (String) root.get("name");
        if (name == null) {
            throw new IllegalArgumentException("Missing 'name'");
        }
        Instant created = parseInstantOr(root.get("created"), Instant.EPOCH);
        Instant modified = parseInstantOr(root.get("modified"), Instant.EPOCH);

        Object effectsRawObj = root.get("effects");
        if (!(effectsRawObj instanceof List)) {
            throw new IllegalArgumentException("Missing or malformed 'effects' array");
        }
        List<Object> effectsRaw = (List<Object>) effectsRawObj;
        if (effectsRaw.size() != 4) {
            throw new IllegalArgumentException("Expected exactly 4 effect entries, found " + effectsRaw.size());
        }

        EffectSettings[] effects = new EffectSettings[4];
        for (Object o : effectsRaw) {
            Map<String, Object> e = (Map<String, Object>) o;
            int slot = ((Double) e.get("slot")).intValue();
            String modelName = (String) e.get("model");
            EffectModel model;
            try {
                model = EffectModel.valueOf(modelName);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Unrecognized effect model name: " + modelName, ex);
            }
            boolean enabled = Boolean.TRUE.equals(e.get("enabled"));
            List<Object> knobsRaw = (List<Object>) e.get("knobs");
            if (knobsRaw == null || knobsRaw.size() != 6) {
                throw new IllegalArgumentException("Expected exactly 6 knob values for slot " + slot);
            }
            int[] k = new int[6];
            for (int i = 0; i < 6; i++) {
                k[i] = ((Double) knobsRaw.get(i)).intValue();
            }
            int categoryIndex = ((slot % 4) + 4) % 4;
            effects[categoryIndex] = new EffectSettings(slot, model, k[0], k[1], k[2], k[3], k[4], k[5], enabled);
        }
        for (int i = 0; i < 4; i++) {
            if (effects[i] == null) {
                throw new IllegalArgumentException("Effect entries don't cover all 4 slot categories (0-3)");
            }
        }

        return new PedalboardSet(name, effects, created, modified);
    }

    private static Instant parseInstantOr(Object value, Instant fallback) {
        if (!(value instanceof String s)) return fallback;
        try {
            return Instant.parse(s);
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    // ---- Filename helpers ----

    static String slugify(String name) {
        String slug = name.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (slug.isEmpty()) slug = "set";
        return slug;
    }

    private static String uniqueFileName(Path dir, String slug) {
        String candidate = slug + FILE_SUFFIX;
        int n = 2;
        while (Files.exists(dir.resolve(candidate))) {
            candidate = slug + "-" + n + FILE_SUFFIX;
            n++;
        }
        return candidate;
    }
}
