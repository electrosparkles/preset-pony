package com.electrosparkles.presetpony;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Tiny app-wide settings — currently just the user-relocatable pedalboard sets folder
 * (see docs/pedalboard-sets-plan.md). Stored via the JDK's own per-user preferences
 * store ({@link Preferences} — Windows Registry under
 * {@code HKCU\Software\JavaSoft\Prefs\...}, a plist under macOS's
 * {@code Library/Preferences}, {@code ~/.java/.userPrefs} on Linux), not a file this
 * app writes into the user's home directory unprompted. This is the platform-idiomatic
 * place for "a handful of small named settings" on every OS Java runs on — no new
 * folder or file location for this app to justify at all, and no file dotfolder in the
 * user's home root that this app hadn't been given any real permission to be writing
 * into. See the About tab for a one-line summary of what's stored here and a button to
 * clear it (clearAll()).
 */
public final class AppSettings {

    private static final String KEY_PEDALBOARD_SETS_FOLDER = "pedalboardSetsFolder";

    private AppSettings() {
    }

    private static Preferences prefs() {
        return Preferences.userNodeForPackage(PresetPony.class);
    }

    /** True once the user has actually chosen (or accepted a suggested) folder - as
     * opposed to {@link #pedalboardSetsFolder()}, which always returns *something*
     * (falling back to the suggested default) and so can't itself be used to tell
     * "configured" from "not asked yet". Used to gate the first-save folder prompt. */
    public static boolean isPedalboardSetsFolderConfigured() {
        return prefs().get(KEY_PEDALBOARD_SETS_FOLDER, null) != null;
    }

    /** The configured pedalboard sets folder, or {@link #defaultPedalboardSetsFolder()}
     * if nothing's been configured yet. */
    public static Path pedalboardSetsFolder() {
        String stored = prefs().get(KEY_PEDALBOARD_SETS_FOLDER, null);
        return (stored != null) ? Paths.get(stored) : defaultPedalboardSetsFolder();
    }

    public static void setPedalboardSetsFolder(Path folder) {
        prefs().put(KEY_PEDALBOARD_SETS_FOLDER, folder.toString());
    }

    /**
     * Suggested pedalboard-sets folder before the user has chosen one — under the OS's
     * actual "Documents" folder (via Swing's {@code FileSystemView}, which resolves the
     * real shell-reported location, including Windows' localized folder names), not a
     * hidden dotfolder under the home directory. A dotfolder would always sort before
     * Documents/Downloads in a file chooser, mixed in among unrelated dotfolders,
     * rather than living alongside the high-usage folders users actually navigate to.
     */
    public static Path defaultPedalboardSetsFolder() {
        Path documents;
        try {
            documents = javax.swing.filechooser.FileSystemView.getFileSystemView().getDefaultDirectory().toPath();
        } catch (RuntimeException ex) {
            documents = Paths.get(System.getProperty("user.home"));
        }
        return documents.resolve("Preset Pony").resolve("Pedalboard Sets");
    }

    /** Removes every preference this app has ever stored (currently just the pedalboard
     * sets folder choice) - see the About tab's "Clear saved preferences" button. Does
     * not touch any actual pedalboard-set files on disk, only this one remembered
     * setting - next time a folder is needed, the app will ask again from scratch. */
    public static void clearAll() {
        try {
            prefs().clear();
        } catch (BackingStoreException ex) {
            throw new RuntimeException("Could not clear saved preferences: " + ex.getMessage(), ex);
        }
    }
}
