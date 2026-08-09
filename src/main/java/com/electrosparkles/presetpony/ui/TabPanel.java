package com.electrosparkles.presetpony.ui;

import com.electrosparkles.presetpony.*;
import javax.swing.*;

/**
 * Abstract base class for all tab panels in the PresetPony UI.
 * 
 * Provides standardized lifecycle and state management:
 * - refresh() — update UI when CurrentPreset changes
 * - setConnectionState() — update enabled/disabled when device connects/disconnects
 * - getPanel() — return the JPanel for this tab
 * 
 * Subclasses hold references to MustangConnection and CurrentPreset, provided
 * by the frame coordinator. Tabs write changes back to the amp via
 * writeAmpSettingsInBackground() and writeEffectSettingsInBackground() helpers,
 * which use SwingWorker to avoid blocking the UI.
 */
public abstract class TabPanel {
    protected MustangConnection conn;
    protected CurrentPreset current;
    protected StatusUpdater statusUpdater;
    protected ControlStateDelegate controlDelegate;
    protected PresetChangedCallback presetChangedCallback;
    
    /**
     * Flag to suppress listener callbacks during programmatic updates.
     * Set to true before bulk-updating multiple controls, then back to false.
     * Listeners check this flag and ignore events if true.
     */
    protected boolean applyingProgrammatically = false;
    
    /**
     * Constructs a tab panel with required delegates for communication with the frame.
     *
     * @param statusUpdater callback to update status label
     * @param controlDelegate callback to manage control enable/disable state
     */
    protected TabPanel(StatusUpdater statusUpdater, ControlStateDelegate controlDelegate) {
        this.statusUpdater = statusUpdater;
        this.controlDelegate = controlDelegate;
    }
    
    /**
     * Sets the callback for when a tab changes the preset state.
     *
     * @param callback the callback to invoke when preset changes
     */
    public final void setPresetChangedCallback(PresetChangedCallback callback) {
        this.presetChangedCallback = callback;
    }
    
    /**
     * @return the JPanel that represents this tab's UI
     */
    public abstract JPanel getPanel();
    
    /**
     * Called when the current preset is updated (after connect, refresh, or switch).
     * Subclasses should refresh all their UI elements from the new preset.
     *
     * @param preset the new current preset (never null)
     */
    public abstract void refresh(CurrentPreset preset);
    
    /**
     * Called when the connection state changes (connected or disconnected).
     * Subclasses should update their UI to enable/disable based on connection state
     * and store the connection reference for future operations.
     *
     * @param connection the MustangConnection (may be null if disconnected)
     * @param connected true if connected and ready to send commands, false otherwise
     */
    public abstract void setConnectionState(MustangConnection connection, boolean connected);
    
    /**
     * Helper method to write updated amp settings to the amp in the background.
     * This method:
     * 1. Updates the status label to "Sending..."
     * 2. Spawns a SwingWorker to write settings without blocking the UI
     * 3. Updates the CurrentPreset once the write completes
     * 4. Notifies the frame coordinator via callback to refresh all tabs
     *
     * @param settings the new AmpSettings to send
     */
    protected final void writeAmpSettingsInBackground(AmpSettings settings) {
        if (conn == null) {
            statusUpdater.updateStatus("Preview only (not connected) - change not sent");
            current = new CurrentPreset(current.presetNumber(), current.name(), settings, current.effects(), current.presetNames());
            return;
        }
        statusUpdater.updateStatus("Sending...");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                conn.writeAmpSettings(settings);
                return null;
            }

            @Override
            protected void done() {
                current = new CurrentPreset(current.presetNumber(), current.name(), settings, current.effects(), current.presetNames());
                statusUpdater.updateStatus("Connected");
                if (presetChangedCallback != null) {
                    presetChangedCallback.onPresetChanged(current);
                }
            }
        }.execute();
    }
    
    /**
     * Helper method to write updated effect settings for a specific slot to the amp in the background.
     * This method:
     * 1. Updates the status label to "Sending..."
     * 2. Spawns a SwingWorker to write settings without blocking the UI
     * 3. Updates the CurrentPreset once the write completes
     * 4. Notifies the frame coordinator via callback to refresh all tabs
     *
     * @param slotIndex the effect slot (0-3)
     * @param settings the new EffectSettings to send
     */
    protected final void writeEffectSettingsInBackground(int slotIndex, EffectSettings settings) {
        if (conn == null) {
            statusUpdater.updateStatus("Preview only (not connected) - change not sent");
            EffectSettings[] updated = current.effects().clone();
            updated[slotIndex] = settings;
            current = new CurrentPreset(current.presetNumber(), current.name(), current.amp(), updated, current.presetNames());
            return;
        }
        statusUpdater.updateStatus("Sending...");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                conn.writeEffectSettings(slotIndex, settings);
                return null;
            }

            @Override
            protected void done() {
                EffectSettings[] updated = current.effects().clone();
                updated[slotIndex] = settings;
                current = new CurrentPreset(current.presetNumber(), current.name(), current.amp(), updated, current.presetNames());
                statusUpdater.updateStatus("Connected");
                if (presetChangedCallback != null) {
                    presetChangedCallback.onPresetChanged(current);
                }
            }
        }.execute();
    }
    
    /**
     * Utility method for tabs to update the status label.
     *
     * @param message the status message to display
     */
    protected final void updateStatus(String message) {
        statusUpdater.updateStatus(message);
    }
    
    /**
     * Utility method for tabs to notify the frame of preset changes.
     * Called by tabs when they directly update the preset (e.g., switchToPreset, saveToSlot).
     *
     * @param preset the updated preset
     */
    protected final void notifyPresetChanged(CurrentPreset preset) {
        if (presetChangedCallback != null) {
            presetChangedCallback.onPresetChanged(preset);
        }
    }
}
