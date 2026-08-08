package com.electrosparkles.presetpony.ui.shared;

import javax.swing.*;
import java.awt.*;

/**
 * Shared status display panel containing three label rows at the top of the main frame.
 * 
 * Shows:
 * - Status (connection state, operation progress)
 * - Current preset name and slot number
 * - Current amp model
 * 
 * Encapsulates layout (GridLayout 3×1) and provides simple setter methods for each label,
 * allowing tabs to update status without holding direct references to the frame's labels.
 */
public class StatusPanel extends JPanel {
    private final JLabel statusLabel;
    private final JLabel presetLabel;
    private final JLabel ampModelLabel;

    public StatusPanel() {
        super(new GridLayout(3, 1));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));

        statusLabel = new JLabel("Not connected");
        presetLabel = new JLabel("Preset: -");
        ampModelLabel = new JLabel("Amp: -");

        add(statusLabel);
        add(presetLabel);
        add(ampModelLabel);
    }

    /**
     * Updates the connection status line.
     * Examples: "Connected", "Connecting...", "Sending...", "Error: USB device not found"
     * 
     * @param text the status message
     */
    public void setStatus(String text) {
        statusLabel.setText(text);
    }

    /**
     * Updates the preset display line.
     * Example: "Preset: 5 - Classic Clean"
     * 
     * @param text the preset display text
     */
    public void setPreset(String text) {
        presetLabel.setText(text);
    }

    /**
     * Updates the amp model display line.
     * Example: "Amp: Fender '65 Twin Reverb"
     * 
     * @param text the amp model display text
     */
    public void setAmpModel(String text) {
        ampModelLabel.setText(text);
    }

    /**
     * Gets the underlying status label.
     * Retained for backward compatibility during migration - prefer setStatus() instead.
     * 
     * @return the status JLabel widget
     */
    public JLabel getStatusLabel() {
        return statusLabel;
    }

    /**
     * Gets the underlying preset label.
     * Retained for backward compatibility during migration - prefer setPreset() instead.
     * 
     * @return the preset JLabel widget
     */
    public JLabel getPresetLabel() {
        return presetLabel;
    }

    /**
     * Gets the underlying amp model label.
     * Retained for backward compatibility during migration - prefer setAmpModel() instead.
     * 
     * @return the amp model JLabel widget
     */
    public JLabel getAmpModelLabel() {
        return ampModelLabel;
    }
}
