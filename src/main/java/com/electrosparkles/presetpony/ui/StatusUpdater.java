package com.electrosparkles.presetpony.ui;

/**
 * Callback interface for UI components to update the application status label.
 * Allows tabs and components to report status without holding a direct reference
 * to the status label widget.
 */
public interface StatusUpdater {
    void updateStatus(String message);
}
