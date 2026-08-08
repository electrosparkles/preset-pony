package com.electrosparkles.presetpony;

import com.electrosparkles.presetpony.ui.*;
import com.electrosparkles.presetpony.ui.shared.ButtonPanel;
import com.electrosparkles.presetpony.ui.shared.StatusPanel;
import com.electrosparkles.presetpony.ui.tabs.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Main frame coordinator. Instantiates all 6 tab panels, StatusPanel, ButtonPanel.
 * Manages connection state, coordinates state updates across tabs via delegates.
 */
public class PresetPony extends JFrame {
    private MustangConnection conn;
    private CurrentPreset current;

    private StatusPanel statusPanel;
    private ButtonPanel buttonPanel;

    private AmpTabPanel ampTab;
    private EffectsTabPanel effectsTab;
    private PresetsTabPanel presetsTab;
    private ToyboxTabPanel toyboxTab;
    private PedalboardSetsTabPanel pedalboardTab;
    private AboutTabPanel aboutTab;

    private final StatusUpdater statusUpdater = text -> {
        if (statusPanel != null) statusPanel.setStatus(text);
    };

    private final ControlStateDelegate controlDelegate = enabled -> {
        if (buttonPanel != null) {
            buttonPanel.setRefreshEnabled(enabled);
            buttonPanel.setExportEnabled(enabled);
        }
        if (ampTab != null) ampTab.setConnectionState(conn, enabled);
        if (effectsTab != null) effectsTab.setConnectionState(conn, enabled);
        if (presetsTab != null) presetsTab.setConnectionState(conn, enabled);
        if (toyboxTab != null) toyboxTab.setConnectionState(conn, enabled);
        if (pedalboardTab != null) pedalboardTab.setConnectionState(conn, enabled);
    };

    public PresetPony() {
        super("Preset Pony - Mustang v2 control");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (conn != null) {
                    try {
                        conn.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        });
        setLayout(new BorderLayout(12, 12));

        List<Image> icons = loadAppIcons();
        setIconImages(icons);
        if (!icons.isEmpty()) setIconImage(icons.get(0));

        // Status panel (top)
        statusPanel = new StatusPanel();
        add(statusPanel, BorderLayout.NORTH);

        // Tabs
        JTabbedPane tabs = new JTabbedPane();
        ampTab = new AmpTabPanel(statusUpdater, controlDelegate);
        effectsTab = new EffectsTabPanel(statusUpdater, controlDelegate);
        presetsTab = new PresetsTabPanel(statusUpdater, controlDelegate);
        toyboxTab = new ToyboxTabPanel(statusUpdater, controlDelegate);
        pedalboardTab = new PedalboardSetsTabPanel(statusUpdater, controlDelegate);
        aboutTab = new AboutTabPanel(statusUpdater, controlDelegate);

        tabs.addTab("Amp", ampTab.getPanel());
        tabs.addTab("Effects", effectsTab.getPanel());
        tabs.addTab("Presets", presetsTab.getPanel());
        tabs.addTab("Toybox", toyboxTab.getPanel());
        tabs.addTab("Pedalboard Sets", pedalboardTab.getPanel());
        tabs.addTab("About", aboutTab.getPanel());
        add(tabs, BorderLayout.CENTER);

        // Button panel (bottom)
        buttonPanel = new ButtonPanel();
        buttonPanel.setOnConnect(e -> connectInBackground());
        buttonPanel.setOnRefresh(e -> refreshInBackground());
        buttonPanel.setOnExport(e -> exportPresetToFuse());
        buttonPanel.setOnImport(e -> importPresetFromFuse());
        add(buttonPanel, BorderLayout.SOUTH);

        setSlidersEnabled(false);
        setSize(560, 860);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void connectInBackground() {
        if (buttonPanel != null && !buttonPanel.getConnectButton().isEnabled()) return;
        if (buttonPanel != null) buttonPanel.getConnectButton().setEnabled(false);
        if (conn != null) {
            try {
                conn.close();
            } catch (Exception ignored) {
            }
            conn = null;
        }
        statusUpdater.updateStatus("Connecting...");
        new SwingWorker<CurrentPreset, Void>() {
            String error = null;

            @Override
            protected CurrentPreset doInBackground() {
                try {
                    conn = MustangConnection.connect();
                    return conn.readCurrentPreset();
                } catch (IllegalStateException e) {
                    error = e.getMessage();
                    return null;
                }
            }

            @Override
            protected void done() {
                try {
                    CurrentPreset preset = get();
                    if (preset == null) {
                        statusUpdater.updateStatus("Error: " + error);
                        return;
                    }
                    current = preset;
                    statusUpdater.updateStatus("Connected");
                    setSlidersEnabled(true);
                    updateAllTabs(current);
                } catch (Exception e) {
                    statusUpdater.updateStatus("Error: " + e.getMessage());
                } finally {
                    if (buttonPanel != null) buttonPanel.getConnectButton().setEnabled(true);
                }
            }
        }.execute();
    }

    private void refreshInBackground() {
        if (conn == null) return;
        if (buttonPanel != null && !buttonPanel.getRefreshButton().isEnabled()) return;
        if (buttonPanel != null) buttonPanel.getRefreshButton().setEnabled(false);
        statusUpdater.updateStatus("Refreshing...");
        new SwingWorker<CurrentPreset, Void>() {
            @Override
            protected CurrentPreset doInBackground() {
                return conn.readCurrentPreset();
            }

            @Override
            protected void done() {
                try {
                    current = get();
                    statusUpdater.updateStatus("Connected");
                    updateAllTabs(current);
                } catch (Exception e) {
                    statusUpdater.updateStatus("Error: " + e.getMessage());
                } finally {
                    if (buttonPanel != null) buttonPanel.getRefreshButton().setEnabled(true);
                }
            }
        }.execute();
    }

    private void updateAllTabs(CurrentPreset preset) {
        String presetNumberText = (preset.presetNumber() >= 0) ? String.valueOf(preset.presetNumber()) : "imported";
        statusPanel.setPreset("Preset: " + presetNumberText + " - " + preset.name());
        statusPanel.setAmpModel("Amp: " + preset.amp().model());

        ampTab.refresh(preset);
        effectsTab.refresh(preset);
        presetsTab.refresh(preset);
        toyboxTab.refresh(preset);
        pedalboardTab.refresh(preset);
    }

    private void setSlidersEnabled(boolean enabled) {
        controlDelegate.setControlsEnabled(enabled);
    }

    private void exportPresetToFuse() {
        if (current == null) {
            JOptionPane.showMessageDialog(this, "Connect and load a preset first.", "Export", JOptionPane.WARNING_MESSAGE);
            return;
        }

        CurrentPreset toExport = buildPresetFromUi();
        String suggested = FusePresetExporter.suggestFileName(toExport.name());
        Path defaultDir = Paths.get(System.getProperty("user.home"), "Documents", "Fender", "FUSE", "Presets");
        JFileChooser chooser = new JFileChooser(defaultDir.toFile());
        chooser.setDialogTitle("Export Fuse preset");
        chooser.setSelectedFile(new java.io.File(suggested));
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Fuse preset (*.fuse)", "fuse"));

        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        Path path = chooser.getSelectedFile().toPath();
        String fileName = path.getFileName().toString();
        if (!fileName.toLowerCase().endsWith(".fuse")) {
            path = path.resolveSibling(fileName + ".fuse");
        }

        try {
            Files.writeString(path, FusePresetExporter.toXml(toExport), StandardCharsets.UTF_8);
            statusUpdater.updateStatus("Exported to " + path.getFileName());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not write file:\n" + ex.getMessage(),
                    "Export failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void importPresetFromFuse() {
        Path defaultDir = Paths.get(System.getProperty("user.home"), "Documents", "Fender", "FUSE");
        JFileChooser chooser = new JFileChooser(defaultDir.toFile());
        chooser.setDialogTitle("Import Fuse preset");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Fuse preset (*.fuse)", "fuse"));

        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path path = chooser.getSelectedFile().toPath();

        try {
            current = FusePresetImporter.fromFile(path);
            setSlidersEnabled(true);
            updateAllTabs(current);
            statusUpdater.updateStatus("Imported " + path.getFileName()
                    + (conn == null ? " (preview only - changes won't be sent until you connect)" : ""));
        } catch (IllegalArgumentException | IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not import file:\n" + ex.getMessage(),
                    "Import failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private CurrentPreset buildPresetFromUi() {
        // Placeholder - tabs hold their own state; would need delegation to read back
        return current;
    }

    private static List<Image> loadAppIcons() {
        List<Image> icons = new ArrayList<>();
        for (int size : new int[]{16, 24, 32, 48, 64, 128, 256}) {
            String resource = "/icons/icon_" + size + ".png";
            java.net.URL url = PresetPony.class.getResource(resource);
            if (url == null) continue;
            try {
                BufferedImage img = ImageIO.read(url);
                if (img != null) icons.add(img);
            } catch (IOException ignored) {
            }
        }
        return icons;
    }

    public static void main(String[] args) {
        EffectKnobScaleFacts.applyDefault();
        SwingUtilities.invokeLater(() -> new PresetPony().setVisible(true));
    }
}
