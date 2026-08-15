package com.electrosparkles.presetpony;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Tiny app-wide settings — currently just the user-relocatable pedalboard folder
 * Stored via the JDK's own per-user preferences
 * store ({@link Preferences} — Windows Registry under
 * {@code HKCU\Software\JavaSoft\Prefs\...}, a plist under macOS's
 * {@code Library/Preferences}, {@code ~/.java/.userPrefs} on Linux)
 * See the About tab for a one-line summary of what's stored here and a button to
 * clear it (clearAll()).
 */
public final class AppSettings {

    private static final String KEY_PEDALBOARD_FOLDER = "pedalboardsFolder";
    private static final String KEY_EXPLORER_FOLDER = "explorerFolder";
    private static final String KEY_PRESETS_IMPORT_FOLDER = "presetsImportFolder";

    private AppSettings() {
    }

    private static Preferences prefs() {
        return Preferences.userNodeForPackage(PresetPony.class);
    }

    /** True once the user has actually chosen (or accepted a suggested) folder - as
     * opposed to {@link #pedalboardsFolder()}, which always returns *something*
     * (falling back to the suggested default) and so can't itself be used to tell
     * "configured" from "not asked yet". Used to gate the first-save folder prompt. */
    public static boolean isPedalboardsFolderConfigured() {
        return prefs().get(KEY_PEDALBOARD_FOLDER, null) != null;
    }

    /** The configured pedalboard sets folder, or {@link #defaultPedalboardsFolder()}
     * if nothing's been configured yet. */
    public static Path pedalboardsFolder() {
        String stored = prefs().get(KEY_PEDALBOARD_FOLDER, null);
        return (stored != null) ? Paths.get(stored) : defaultPedalboardsFolder();
    }

    public static void setPedalboardsFolder(Path folder) {
        prefs().put(KEY_PEDALBOARD_FOLDER, folder.toString());
    }

    /** The configured preset explorer folder, or {@link #defaultDocumentsFolder()}
     * if nothing's been configured yet. */
    public static Path explorerFolder() {
        String stored = prefs().get(KEY_EXPLORER_FOLDER, null);
        return (stored != null) ? Paths.get(stored) : defaultDocumentsFolder();
    }

    public static void setExplorerFolder(Path folder) {
        prefs().put(KEY_EXPLORER_FOLDER, folder.toString());
    }

    /** The configured presets import folder, or {@link #defaultDocumentsFolder()}
     * if nothing's been configured yet. */
    public static Path presetsImportFolder() {
        String stored = prefs().get(KEY_PRESETS_IMPORT_FOLDER, null);
        return (stored != null) ? Paths.get(stored) : defaultDocumentsFolder();
    }

    public static void setPresetsImportFolder(Path folder) {
        prefs().put(KEY_PRESETS_IMPORT_FOLDER, folder.toString());
    }

    /**
     * The OS's Documents folder — shared default for all browse operations.
     * Resolves via Swing's {@code FileSystemView} to handle localized Windows folder names.
     */
    public static Path defaultDocumentsFolder() {
        try {
            return javax.swing.filechooser.FileSystemView.getFileSystemView().getDefaultDirectory().toPath();
        } catch (RuntimeException ex) {
            return Paths.get(System.getProperty("user.home"));
        }
    }

    /**
     * Suggested pedalboard folder before the user has chosen one — under the OS's
     * actual "Documents" folder (via Swing's {@code FileSystemView}, which resolves the
     * real shell-reported location, including Windows' localized folder names).
     */
    public static Path defaultPedalboardsFolder() {
        return defaultDocumentsFolder().resolve("Preset Pony").resolve("Pedalboards");
    }

    /** Removes every preference this app has ever stored (currently just the pedalboard
     * sets folder choice) - see the About tab's "Clear saved preferences" button. Does
     * not touch any actual pedalboard files on disk, only this one remembered
     * setting - next time a folder is needed, the app will ask again from scratch. */
    public static void clearAll() {
        try {
            prefs().clear();
        } catch (BackingStoreException ex) {
            throw new RuntimeException("Could not clear saved preferences: " + ex.getMessage(), ex);
        }
    }
}
