package com.electrosparkles.presetpony.ui.shared;

import javax.swing.*;
import java.awt.event.ActionListener;

/**
 * Shared button panel at the bottom of the main frame.
 * 
 * Contains four action buttons:
 * - Connect (opens/initiates USB connection to amp)
 * - Refresh from amp (reloads current preset from the device)
 * - Export preset... (saves current to .fuse XML file)
 * - Import preset... (loads a preset from .fuse XML file)
 * 
 * Encapsulates button layout and event wiring. The frame passes listener callbacks
 * during construction, and tabs can enable/disable buttons through this panel's
 * public enable/disable methods.
 */
public class ButtonPanel extends JPanel {
    private final JButton connectButton;
    private final JButton refreshButton;
    private final JButton exportPresetButton;
    private final JButton importPresetButton;

    /**
     * Constructs the button panel with all four buttons.
     * Listeners are initially null; callers should call setOn*() methods
     * during frame construction to wire up behavior.
     */
    public ButtonPanel() {
        connectButton = new JButton("Connect");
        refreshButton = new JButton("Refresh from amp");
        exportPresetButton = new JButton("Export preset...");
        importPresetButton = new JButton("Import preset...");

        exportPresetButton.setEnabled(false); // Disabled until connected

        add(connectButton);
        add(refreshButton);
        add(exportPresetButton);
        add(importPresetButton);
    }

    /**
     * Sets the listener for Connect button clicks.
     * Called by the frame during construction to wire up connectInBackground().
     * 
     * @param listener the action listener (may be null to clear)
     */
    public void setOnConnect(ActionListener listener) {
        // Remove old listener(s) if any
        for (ActionListener al : connectButton.getActionListeners()) {
            connectButton.removeActionListener(al);
        }
        if (listener != null) {
            connectButton.addActionListener(listener);
        }
    }

    /**
     * Sets the listener for Refresh button clicks.
     * Called by the frame during construction to wire up refreshInBackground().
     * 
     * @param listener the action listener (may be null to clear)
     */
    public void setOnRefresh(ActionListener listener) {
        for (ActionListener al : refreshButton.getActionListeners()) {
            refreshButton.removeActionListener(al);
        }
        if (listener != null) {
            refreshButton.addActionListener(listener);
        }
    }

    /**
     * Sets the listener for Export Preset button clicks.
     * Called by the frame during construction to wire up exportPresetToFuse().
     * 
     * @param listener the action listener (may be null to clear)
     */
    public void setOnExport(ActionListener listener) {
        for (ActionListener al : exportPresetButton.getActionListeners()) {
            exportPresetButton.removeActionListener(al);
        }
        if (listener != null) {
            exportPresetButton.addActionListener(listener);
        }
    }

    /**
     * Sets the listener for Import Preset button clicks.
     * Called by the frame during construction to wire up importPresetFromFuse().
     * 
     * @param listener the action listener (may be null to clear)
     */
    public void setOnImport(ActionListener listener) {
        for (ActionListener al : importPresetButton.getActionListeners()) {
            importPresetButton.removeActionListener(al);
        }
        if (listener != null) {
            importPresetButton.addActionListener(listener);
        }
    }

    // ===== Enable/Disable control state =====
    // Called by the frame's ControlStateDelegate when connection state changes

    /**
     * Enables or disables the Connect button.
     * 
     * @param enabled true to enable, false to disable
     */
    public void setConnectEnabled(boolean enabled) {
        connectButton.setEnabled(enabled);
    }

    /**
     * Enables or disables the Refresh button.
     * Only meaningful when connected.
     * 
     * @param enabled true to enable, false to disable
     */
    public void setRefreshEnabled(boolean enabled) {
        refreshButton.setEnabled(enabled);
    }

    /**
     * Enables or disables the Export Preset button.
     * Only meaningful when connected and a preset is loaded.
     * 
     * @param enabled true to enable, false to disable
     */
    public void setExportEnabled(boolean enabled) {
        exportPresetButton.setEnabled(enabled);
    }

    /**
     * Enables or disables the Import Preset button.
     * Always available (can import even without connection for preview).
     * 
     * @param enabled true to enable, false to disable
     */
    public void setImportEnabled(boolean enabled) {
        importPresetButton.setEnabled(enabled);
    }

    // ===== Getters for button references (for backward compatibility) =====

    public JButton getConnectButton() {
        return connectButton;
    }

    public JButton getRefreshButton() {
        return refreshButton;
    }

    public JButton getExportButton() {
        return exportPresetButton;
    }

    public JButton getImportButton() {
        return importPresetButton;
    }
}
