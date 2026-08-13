package com.electrosparkles.presetpony.ui;

import com.electrosparkles.presetpony.CurrentPreset;

/**
 * Callback interface for notifying the frame when a tab has changed the preset state.
 * Used by tabs to trigger a full UI refresh across all tabs after operations like
 * switching presets, saving to slot, or loading pedalboards.
 */
public interface PresetChangedCallback {
    /**
     * Called when a tab has updated the preset state.
     * The frame should refresh all tabs from this new preset.
     *
     * @param preset the updated preset
     */
    void onPresetChanged(CurrentPreset preset);
}
