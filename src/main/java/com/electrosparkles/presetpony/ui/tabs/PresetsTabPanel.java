package com.electrosparkles.presetpony.ui.tabs;

import com.electrosparkles.presetpony.*;
import com.electrosparkles.presetpony.ui.ControlStateDelegate;
import com.electrosparkles.presetpony.ui.StatusUpdater;
import com.electrosparkles.presetpony.ui.TabPanel;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Frame;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Presets tab: preset list, switch/save/backup/CSV export buttons.
 */
public class PresetsTabPanel extends TabPanel {
    private final JPanel panel;
    private final DefaultListModel<String> presetListModel = new DefaultListModel<>();
    private final JList<String> presetList = new JList<>(presetListModel);
    private final JButton switchButton;
    private final JButton saveToSlotButton;
    private final JButton backupAllButton;
    private final JButton exportCsvButton;

    public PresetsTabPanel(StatusUpdater statusUpdater, ControlStateDelegate controlDelegate) {
        super(statusUpdater, controlDelegate);
        switchButton = new JButton("Switch to preset");
        saveToSlotButton = new JButton("Save current to slot...");
        backupAllButton = new JButton("Backup all to zip...");
        exportCsvButton = new JButton("Export all to CSV...");
        panel = buildPanel();
    }

    @Override
    public JPanel getPanel() {
        return panel;
    }

    @Override
    public void refresh(CurrentPreset preset) {
        this.current = preset;
        presetListModel.clear();
        List<String> names = current.presetNames();
        for (int i = 0; i < names.size(); i++) {
            presetListModel.addElement(i + ": " + names.get(i));
        }
    }

    @Override
    public void setConnectionState(MustangConnection connection, boolean connected) {
        this.conn = connection;
        switchButton.setEnabled(connected && presetList.getSelectedIndex() >= 0);
        saveToSlotButton.setEnabled(connected && presetList.getSelectedIndex() >= 0 && current != null);
        backupAllButton.setEnabled(connected);
        exportCsvButton.setEnabled(connected);
    }

    private JPanel buildPanel() {
        JPanel panel = new JPanel(new java.awt.BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.add(new JLabel("Stored on the amp:"), java.awt.BorderLayout.NORTH);
        presetList.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
        panel.add(new JScrollPane(presetList), java.awt.BorderLayout.CENTER);

        switchButton.setEnabled(false);
        saveToSlotButton.setEnabled(false);
        backupAllButton.setEnabled(false);
        exportCsvButton.setEnabled(false);

        JPanel presetActions = new JPanel(new java.awt.GridLayout(2, 2, 8, 4));
        presetActions.add(switchButton);
        presetActions.add(saveToSlotButton);
        presetActions.add(backupAllButton);
        presetActions.add(exportCsvButton);
        panel.add(presetActions, java.awt.BorderLayout.SOUTH);

        presetList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                switchButton.setEnabled(presetList.getSelectedIndex() >= 0 && conn != null);
                saveToSlotButton.setEnabled(presetList.getSelectedIndex() >= 0 && conn != null && current != null);
            }
        });

        switchButton.addActionListener(e -> switchToPreset(presetList.getSelectedIndex()));
        saveToSlotButton.addActionListener(e -> saveToSlot(presetList.getSelectedIndex()));
        backupAllButton.addActionListener(e -> backupAllPresetsToZip());
        exportCsvButton.addActionListener(e -> exportAllPresetsToCsv());

        return panel;
    }

    private void switchToPreset(int slot) {
        if (slot < 0 || conn == null) return;
        updateStatus("Switching to preset " + slot + "...");
        switchButton.setEnabled(false);
        new SwingWorker<CurrentPreset, Void>() {
            @Override
            protected CurrentPreset doInBackground() {
                return conn.switchToPreset(slot);
            }

            @Override
            protected void done() {
                try {
                    current = get();
                    updateStatus("Connected");
                } catch (Exception ex) {
                    updateStatus("Error: " + ex.getMessage());
                } finally {
                    switchButton.setEnabled(presetList.getSelectedIndex() >= 0);
                }
            }
        }.execute();
    }

    private void saveToSlot(int slot) {
        if (slot < 0 || conn == null || current == null) return;

        String existingName = (slot < current.presetNames().size()) ? current.presetNames().get(slot) : "";
        String proposedName = JOptionPane.showInputDialog(panel,
                "Save the current live settings to slot " + slot + ".\n"
                        + "This will overwrite whatever is currently stored there"
                        + (existingName.isBlank() ? "." : " (\"" + existingName + "\").") + "\n\n"
                        + "Preset name:",
                "Save to slot " + slot, JOptionPane.WARNING_MESSAGE);
        if (proposedName == null) return;

        int confirm = JOptionPane.showConfirmDialog(panel,
                "Overwrite slot " + slot + " (\"" + existingName + "\") with \"" + proposedName + "\"?\n"
                        + "This cannot be undone.",
                "Confirm overwrite", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        updateStatus("Saving to slot " + slot + "...");
        saveToSlotButton.setEnabled(false);
        new SwingWorker<CurrentPreset, Void>() {
            @Override
            protected CurrentPreset doInBackground() {
                List<String> updatedNames = new ArrayList<>(current.presetNames());
                while (updatedNames.size() <= slot) updatedNames.add("");
                updatedNames.set(slot, proposedName);
                return conn.saveToSlot(slot, proposedName, updatedNames);
            }

            @Override
            protected void done() {
                try {
                    current = get();
                    updateStatus("Saved to slot " + slot);
                } catch (Exception ex) {
                    updateStatus("Error: " + ex.getMessage());
                } finally {
                    saveToSlotButton.setEnabled(presetList.getSelectedIndex() >= 0);
                }
            }
        }.execute();
    }

    private void backupAllPresetsToZip() {
        if (conn == null || current == null) {
            JOptionPane.showMessageDialog(panel, "Connect to the amp first.", "Backup", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int proceed = JOptionPane.showConfirmDialog(panel,
                "This reads all " + FusePresetBackup.SLOT_COUNT + " preset slots from the amp over USB.\n"
                        + "It typically takes 1–3 minutes — keep the amp plugged in and powered on.\n\n"
                        + "Continue?",
                "Backup all presets",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.INFORMATION_MESSAGE);
        if (proceed != JOptionPane.OK_OPTION) return;

        Path defaultDir = Paths.get(System.getProperty("user.home"), "Documents", "Fender", "FUSE", "Backups");
        JFileChooser chooser = new JFileChooser(defaultDir.toFile());
        chooser.setDialogTitle("Backup all presets");
        chooser.setSelectedFile(new java.io.File(FusePresetBackup.suggestZipFileName()));
        chooser.setFileFilter(new FileNameExtensionFilter("Mustang preset backup (*.zip)", "zip"));

        if (chooser.showSaveDialog(panel) != JFileChooser.APPROVE_OPTION) return;

        Path zipPath = chooser.getSelectedFile().toPath();
        String zipName = zipPath.getFileName().toString();
        if (!zipName.toLowerCase().endsWith(".zip")) {
            zipPath = zipPath.resolveSibling(zipName + ".zip");
        }

        final int returnToSlot = current.presetNumber();
        final Path targetZip = zipPath;

        JDialog progressDialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(panel), "Backing up presets", true);
        progressDialog.setLayout(new java.awt.BorderLayout(10, 10));
        JPanel progressNorth = new JPanel(new java.awt.GridLayout(2, 1, 0, 4));
        progressNorth.add(new JLabel("Reading each slot from the amp — usually 1–3 min for 100 presets."));
        JLabel progressLabel = new JLabel("Starting...");
        progressNorth.add(progressLabel);
        progressDialog.add(progressNorth, java.awt.BorderLayout.NORTH);
        JProgressBar progressBar = new JProgressBar(0, FusePresetBackup.SLOT_COUNT);
        progressBar.setStringPainted(true);
        progressDialog.add(progressBar, java.awt.BorderLayout.CENTER);
        JButton cancelButton = new JButton("Cancel");
        progressDialog.add(cancelButton, java.awt.BorderLayout.SOUTH);
        progressDialog.setSize(480, 150);
        progressDialog.setLocationRelativeTo((Frame) SwingUtilities.getWindowAncestor(panel));

        AtomicBoolean cancelled = new AtomicBoolean(false);
        cancelButton.addActionListener(e -> cancelled.set(true));

        backupAllButton.setEnabled(false);
        exportCsvButton.setEnabled(false);

        SwingWorker<FusePresetBackup.BackupResult, Void> worker = new SwingWorker<>() {
            @Override
            protected FusePresetBackup.BackupResult doInBackground() throws Exception {
                return FusePresetBackup.exportAll(conn, targetZip, returnToSlot, new FusePresetBackup.Progress() {
                    @Override
                    public boolean isCancelled() {
                        return cancelled.get();
                    }

                    @Override
                    public void onSlot(int slot, int totalSlots, String presetName) {
                        SwingUtilities.invokeLater(() -> {
                            progressBar.setValue(slot + 1);
                            progressBar.setString((slot + 1) + " / " + totalSlots);
                            progressLabel.setText("Slot " + slot + ": " + presetName);
                        });
                    }
                });
            }

            @Override
            protected void done() {
                progressDialog.dispose();
                backupAllButton.setEnabled(true);
                exportCsvButton.setEnabled(true);
                try {
                    FusePresetBackup.BackupResult result = get();
                    try {
                        current = conn.readPresetAtSlot(returnToSlot, current.presetNames());
                    } catch (Exception ignored) {
                    }
                    String msg = result.cancelled()
                            ? "Backup cancelled.\nSaved " + result.slotsSucceeded() + " preset(s) before stop."
                            : "Backed up " + result.slotsSucceeded() + " / " + FusePresetBackup.SLOT_COUNT + " presets.";
                    if (!result.failedSlots().isEmpty()) {
                        msg += "\nFailed slots: " + result.failedSlots();
                    }
                    msg += "\n\n" + result.zipPath();
                    updateStatus("Backup saved (" + result.slotsSucceeded() + " presets)");
                    JOptionPane.showMessageDialog(panel, msg, "Backup complete",
                            result.failedSlots().isEmpty() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
                } catch (Exception ex) {
                    updateStatus("Backup failed");
                    JOptionPane.showMessageDialog(panel, ex.getMessage(), "Backup failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        worker.execute();
        progressDialog.setVisible(true);
    }

    private void exportAllPresetsToCsv() {
        if (conn == null || current == null) {
            JOptionPane.showMessageDialog(panel, "Connect to the amp first.", "Export CSV", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int proceed = JOptionPane.showConfirmDialog(panel,
                "This reads all " + PresetCsvBackup.SLOT_COUNT + " preset slots from the amp over USB.\n"
                        + "It typically takes 1–3 minutes — keep the amp plugged in and powered on.\n\n"
                        + "Continue?",
                "Export all presets to CSV",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.INFORMATION_MESSAGE);
        if (proceed != JOptionPane.OK_OPTION) return;

        Path defaultDir = Paths.get(System.getProperty("user.home"), "Documents", "Fender", "FUSE");
        JFileChooser chooser = new JFileChooser(defaultDir.toFile());
        chooser.setDialogTitle("Export all presets to CSV");
        chooser.setSelectedFile(new java.io.File(PresetCsvBackup.suggestCsvFileName()));
        chooser.setFileFilter(new FileNameExtensionFilter("CSV (*.csv)", "csv"));

        if (chooser.showSaveDialog(panel) != JFileChooser.APPROVE_OPTION) return;

        Path csvPath = chooser.getSelectedFile().toPath();
        String csvName = csvPath.getFileName().toString();
        if (!csvName.toLowerCase().endsWith(".csv")) {
            csvPath = csvPath.resolveSibling(csvName + ".csv");
        }

        final int returnToSlot = current.presetNumber();
        final Path targetCsv = csvPath;

        JDialog progressDialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(panel), "Exporting presets to CSV", true);
        progressDialog.setLayout(new java.awt.BorderLayout(10, 10));
        JPanel progressNorth = new JPanel(new java.awt.GridLayout(2, 1, 0, 4));
        progressNorth.add(new JLabel("Reading each slot from the amp — usually 1–3 min for 100 presets."));
        JLabel progressLabel = new JLabel("Starting...");
        progressNorth.add(progressLabel);
        progressDialog.add(progressNorth, java.awt.BorderLayout.NORTH);
        JProgressBar progressBar = new JProgressBar(0, PresetCsvBackup.SLOT_COUNT);
        progressBar.setStringPainted(true);
        progressDialog.add(progressBar, java.awt.BorderLayout.CENTER);
        JButton cancelButton = new JButton("Cancel");
        progressDialog.add(cancelButton, java.awt.BorderLayout.SOUTH);
        progressDialog.setSize(480, 150);
        progressDialog.setLocationRelativeTo((Frame) SwingUtilities.getWindowAncestor(panel));

        AtomicBoolean cancelled = new AtomicBoolean(false);
        cancelButton.addActionListener(e -> cancelled.set(true));

        backupAllButton.setEnabled(false);
        exportCsvButton.setEnabled(false);

        SwingWorker<PresetCsvBackup.CsvBackupResult, Void> worker = new SwingWorker<>() {
            @Override
            protected PresetCsvBackup.CsvBackupResult doInBackground() throws Exception {
                return PresetCsvBackup.exportAll(conn, targetCsv, returnToSlot, new PresetCsvBackup.Progress() {
                    @Override
                    public boolean isCancelled() {
                        return cancelled.get();
                    }

                    @Override
                    public void onSlot(int slot, int totalSlots, String presetName) {
                        SwingUtilities.invokeLater(() -> {
                            progressBar.setValue(slot + 1);
                            progressBar.setString((slot + 1) + " / " + totalSlots);
                            progressLabel.setText("Slot " + slot + ": " + presetName);
                        });
                    }
                });
            }

            @Override
            protected void done() {
                progressDialog.dispose();
                backupAllButton.setEnabled(true);
                exportCsvButton.setEnabled(true);
                try {
                    PresetCsvBackup.CsvBackupResult result = get();
                    try {
                        current = conn.readPresetAtSlot(returnToSlot, current.presetNames());
                    } catch (Exception ignored) {
                    }
                    String msg = result.cancelled()
                            ? "Export cancelled.\nSaved " + result.slotsSucceeded() + " preset(s) before stop."
                            : "Exported " + result.slotsSucceeded() + " / " + PresetCsvBackup.SLOT_COUNT + " presets.";
                    if (!result.failedSlots().isEmpty()) {
                        msg += "\nFailed slots: " + result.failedSlots();
                    }
                    msg += "\n\n" + result.csvPath();
                    updateStatus("CSV saved (" + result.slotsSucceeded() + " presets)");
                    JOptionPane.showMessageDialog(panel, msg, "Export complete",
                            result.failedSlots().isEmpty() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
                } catch (Exception ex) {
                    updateStatus("CSV export failed");
                    JOptionPane.showMessageDialog(panel, ex.getMessage(), "Export failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        worker.execute();
        progressDialog.setVisible(true);
    }
}
