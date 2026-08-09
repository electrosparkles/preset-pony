package com.electrosparkles.presetpony.ui;

import com.electrosparkles.presetpony.CurrentPreset;

/**
 * Callback interface for notifying tabs when the current preset changes.
 * Allows centralized state updates without tabs polling or holding references.
 */
public interface PresetUpdateListener {
    /**
     * Called when the current preset has been updated (after connect, refresh, or preset switch).
     * Tabs should refresh their UI from this preset.
     *
     * @param preset the new current preset (never null)
     */
    void onPresetUpdated(CurrentPreset preset);
}
