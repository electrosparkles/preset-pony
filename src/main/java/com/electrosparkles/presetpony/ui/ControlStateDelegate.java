package com.electrosparkles.presetpony.ui;

/**
 * Callback interface for managing the enabled/disabled state of UI controls
 * based on connection state and other app conditions.
 * Allows tabs to coordinate their control states without direct frame access.
 */
public interface ControlStateDelegate {
    /**
     * Enable or disable all controls based on connection state.
     * Called when the device connects/disconnects or when app state changes.
     *
     * @param enabled true to enable controls, false to disable
     */
    void setControlsEnabled(boolean enabled);
}
