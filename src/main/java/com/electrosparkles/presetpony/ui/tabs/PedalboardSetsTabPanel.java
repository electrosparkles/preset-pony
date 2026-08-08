package com.electrosparkles.presetpony.ui.tabs;

import com.electrosparkles.presetpony.*;
import com.electrosparkles.presetpony.ui.ControlStateDelegate;
import com.electrosparkles.presetpony.ui.StatusUpdater;
import com.electrosparkles.presetpony.ui.TabPanel;
import com.electrosparkles.presetpony.ui.components.PedalboardTableModel;
import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.List;

/**
 * Pedalboard Sets tab: file browser, MRU strip, save/load/rename/delete.
 */
public class PedalboardSetsTabPanel extends TabPanel {
    private final JPanel panel;

    private static final int MRU_DISPLAY_LIMIT = 6;
    private Path pedalboardDir;
    private final Map<Path, PedalboardSet> pedalboardCache = new HashMap<>();
    private final List<Path> pedalboardRows = new ArrayList<>();
    private final PedalboardTableModel tableModel;
    private final JTable pedalboardTable;
    private final JPanel pedalboardMruChips;
    private final JLabel pedalboardFolderLabel;

    public PedalboardSetsTabPanel(StatusUpdater statusUpdater, ControlStateDelegate controlDelegate) {
        super(statusUpdater, controlDelegate);
        tableModel = new PedalboardTableModel(pedalboardRows, pedalboardCache);
        pedalboardTable = new JTable(tableModel);
        pedalboardMruChips = new JPanel();
        pedalboardFolderLabel = new JLabel();
        panel = buildPanel();
    }

    @Override
    public JPanel getPanel() {
        return panel;
    }

    @Override
    public void refresh(CurrentPreset preset) {
        this.current = preset;
    }

    @Override
    public void setConnectionState(MustangConnection connection, boolean connected) {
        this.conn = connection;
    }

    private JPanel buildPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

        // Folder row: label + path (left, wraps), button (right, top-aligned)
        JPanel folderRow = new JPanel(new BorderLayout(6, 4));
        folderRow.setBorder(BorderFactory.createEmptyBorder(4, 0, 8, 0));
        folderRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        folderRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));

        JPanel folderLeft = new JPanel(new BorderLayout(6, 0));
        folderLeft.add(new JLabel("Folder:"), BorderLayout.WEST);
        
        // Make folder path wrap if too long (use HTML with width constraint)
        pedalboardFolderLabel.setFont(pedalboardFolderLabel.getFont().deriveFont(10f));
        pedalboardFolderLabel.setVerticalAlignment(SwingConstants.TOP);
        pedalboardFolderLabel.setHorizontalAlignment(SwingConstants.LEFT);
        folderLeft.add(pedalboardFolderLabel, BorderLayout.CENTER);
        
        JButton changeFolderButton = new JButton("Change folder...");
        changeFolderButton.addActionListener(e -> onChangePedalboardFolder());

        folderRow.add(folderLeft, BorderLayout.CENTER);
        folderRow.add(changeFolderButton, BorderLayout.NORTH);
        top.add(folderRow);

        // MRU chips
        pedalboardMruChips.setLayout(new BoxLayout(pedalboardMruChips, BoxLayout.X_AXIS));
        JScrollPane mruScroll = new JScrollPane(pedalboardMruChips,
                JScrollPane.VERTICAL_SCROLLBAR_NEVER, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        mruScroll.setBorder(BorderFactory.createTitledBorder("Quick switch (most recent)"));
        mruScroll.setPreferredSize(new Dimension(10, 58));
        mruScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
        top.add(mruScroll);
        panel.add(top, BorderLayout.NORTH);

        // Table
        pedalboardTable.setFillsViewportHeight(true);
        pedalboardTable.setRowSelectionAllowed(true);
        pedalboardTable.setColumnSelectionAllowed(false);
        pedalboardTable.setAutoCreateRowSorter(false);
        javax.swing.table.DefaultTableCellRenderer modifiedRenderer = new javax.swing.table.DefaultTableCellRenderer();
        modifiedRenderer.setHorizontalAlignment(SwingConstants.RIGHT);
        var modifiedColumn = pedalboardTable.getColumnModel().getColumn(1);
        modifiedColumn.setCellRenderer(modifiedRenderer);
        int modifiedWidth = pedalboardTable.getFontMetrics(pedalboardTable.getFont())
                .stringWidth("0000-00-00 00:00:00") + 24;
        modifiedColumn.setPreferredWidth(modifiedWidth);
        modifiedColumn.setMaxWidth(modifiedWidth);
        modifiedColumn.setMinWidth(modifiedWidth);
        panel.add(new JScrollPane(pedalboardTable), BorderLayout.CENTER);

        // Action buttons
        JButton saveAsButton = new JButton("Save current as...");
        JButton loadButton = new JButton("Load selected");
        JButton renameButton = new JButton("Rename selected...");
        JButton deleteButton = new JButton("Delete selected");
        saveAsButton.addActionListener(e -> onSavePedalboardSetAs());
        loadButton.addActionListener(e -> {
            Path selected = selectedPedalboardPath();
            if (selected != null) applyPedalboardSet(selected);
        });
        renameButton.addActionListener(e -> onRenamePedalboardSet());
        deleteButton.addActionListener(e -> onDeletePedalboardSet());

        JPanel buttonsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        buttonsRow.add(saveAsButton);
        buttonsRow.add(loadButton);
        buttonsRow.add(renameButton);
        buttonsRow.add(deleteButton);
        panel.add(buttonsRow, BorderLayout.SOUTH);

        pedalboardDir = AppSettings.pedalboardSetsFolder();
        refreshPedalboardSetsUi();

        return panel;
    }

    private Path selectedPedalboardPath() {
        int row = pedalboardTable.getSelectedRow();
        return (row >= 0 && row < pedalboardRows.size()) ? pedalboardRows.get(row) : null;
    }

    private void refreshPedalboardSetsUi() {
        String pathText = pedalboardDir.toString();
        if (!AppSettings.isPedalboardSetsFolderConfigured()) {
            pathText += "  (not yet chosen - will be set on first save)";
        }
        // Wrap in HTML with width constraint to force text wrapping
        pedalboardFolderLabel.setText("<html><div style='width:300px'>" + pathText + "</div></html>");

        pedalboardRows.clear();
        pedalboardCache.clear();
        try {
            for (Path file : PedalboardSetStore.list(pedalboardDir)) {
                try {
                    pedalboardCache.put(file, PedalboardSetStore.peek(file));
                } catch (Exception ignored) {
                }
                pedalboardRows.add(file);
            }
        } catch (IOException ex) {
            updateStatus("Couldn't list pedalboard sets: " + ex.getMessage());
        }
        tableModel.fireTableDataChanged();

        pedalboardMruChips.removeAll();
        try {
            List<Path> recent = PedalboardSetStore.recent(pedalboardDir);
            for (int i = 0; i < Math.min(MRU_DISPLAY_LIMIT, recent.size()); i++) {
                Path file = recent.get(i);
                PedalboardSet cached = pedalboardCache.get(file);
                String label = (cached != null) ? cached.name() : file.getFileName().toString();
                JButton chip = new JButton(label);
                chip.addActionListener(e -> applyPedalboardSet(file));
                pedalboardMruChips.add(chip);
            }
        } catch (IOException ignored) {
        }
        pedalboardMruChips.revalidate();
        pedalboardMruChips.repaint();
    }

    private boolean ensurePedalboardFolderChosen() {
        if (AppSettings.isPedalboardSetsFolderConfigured()) return true;

        Path suggested = AppSettings.defaultPedalboardSetsFolder();
        Object[] options = {"Use this folder", "Choose a different folder..."};
        int choice = JOptionPane.showOptionDialog(panel,
                "Where should your pedalboard sets be saved?\n\nSuggested:\n" + suggested,
                "Choose Pedalboard Sets folder", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]);

        Path chosen;
        if (choice == 0) {
            chosen = suggested;
        } else if (choice == 1) {
            JFileChooser chooser = new JFileChooser(suggested.getParent() != null ? suggested.getParent().toFile() : suggested.toFile());
            chooser.setDialogTitle("Choose Pedalboard Sets folder");
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(panel) != JFileChooser.APPROVE_OPTION) return false;
            chosen = chooser.getSelectedFile().toPath();
        } else {
            return false;
        }

        AppSettings.setPedalboardSetsFolder(chosen);
        pedalboardDir = chosen;
        refreshPedalboardSetsUi();
        return true;
    }

    private void onSavePedalboardSetAs() {
        if (current == null) {
            JOptionPane.showMessageDialog(panel, "Connect (or import a preset) first.",
                    "Pedalboard Sets", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!ensurePedalboardFolderChosen()) return;

        String name = JOptionPane.showInputDialog(panel, "Name for this pedalboard set:",
                "Save Pedalboard Set", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.isBlank()) return;

        try {
            PedalboardSetStore.save(pedalboardDir, PedalboardSet.capture(name.trim(), current.effects()));
            refreshPedalboardSetsUi();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(panel, "Could not save set:\n" + ex.getMessage(),
                    "Save failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void applyPedalboardSet(Path file) {
        if (current == null) {
            JOptionPane.showMessageDialog(panel, "Connect (or import a preset) first.",
                    "Pedalboard Sets", JOptionPane.WARNING_MESSAGE);
            return;
        }
        updateStatus(conn == null ? "Applying pedalboard set (preview only - not connected)..." : "Applying pedalboard set...");

        new SwingWorker<PedalboardSet, Void>() {
            @Override
            protected PedalboardSet doInBackground() throws Exception {
                PedalboardSet set = PedalboardSetStore.load(file);
                if (conn != null) {
                    for (int slot = 0; slot < 4; slot++) {
                        conn.writeEffectSettings(slot, set.effects()[slot]);
                    }
                }
                return set;
            }

            @Override
            protected void done() {
                try {
                    PedalboardSet set = get();
                    current = new CurrentPreset(current.presetNumber(), current.name(), current.amp(),
                            set.effects(), current.presetNames());
                    updateStatus(conn == null ? "Preview only (not connected) - change not sent" : "Connected");
                } catch (Exception ex) {
                    updateStatus("Error: " + ex.getMessage());
                    JOptionPane.showMessageDialog(panel, "Couldn't load that set:\n" + ex.getMessage(),
                            "Pedalboard Sets", JOptionPane.ERROR_MESSAGE);
                }
                refreshPedalboardSetsUi();
            }
        }.execute();
    }

    private void onRenamePedalboardSet() {
        Path selected = selectedPedalboardPath();
        if (selected == null) return;
        PedalboardSet existing = pedalboardCache.get(selected);
        if (existing == null) {
            JOptionPane.showMessageDialog(panel, "That file couldn't be read - fix or remove it before renaming.",
                    "Pedalboard Sets", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String newName = (String) JOptionPane.showInputDialog(panel, "Rename to:", "Rename Pedalboard Set",
                JOptionPane.PLAIN_MESSAGE, null, null, existing.name());
        if (newName == null || newName.isBlank() || newName.trim().equals(existing.name())) return;

        try {
            PedalboardSetStore.delete(selected);
            PedalboardSetStore.save(pedalboardDir, existing.withName(newName.trim()));
            refreshPedalboardSetsUi();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(panel, "Could not rename set:\n" + ex.getMessage(),
                    "Rename failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onDeletePedalboardSet() {
        Path selected = selectedPedalboardPath();
        if (selected == null) return;
        PedalboardSet existing = pedalboardCache.get(selected);
        String label = (existing != null) ? existing.name() : selected.getFileName().toString();

        int confirm = JOptionPane.showConfirmDialog(panel, "Delete pedalboard set \"" + label + "\"?\nThis cannot be undone.",
                "Confirm delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        try {
            PedalboardSetStore.delete(selected);
            refreshPedalboardSetsUi();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(panel, "Could not delete set:\n" + ex.getMessage(),
                    "Delete failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onChangePedalboardFolder() {
        Path startAt = AppSettings.isPedalboardSetsFolderConfigured() ? pedalboardDir : AppSettings.defaultPedalboardSetsFolder();
        JFileChooser chooser = new JFileChooser(startAt.toFile());
        chooser.setDialogTitle("Choose pedalboard sets folder");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        if (chooser.showOpenDialog(panel) != JFileChooser.APPROVE_OPTION) return;
        Path newDir = chooser.getSelectedFile().toPath();

        AppSettings.setPedalboardSetsFolder(newDir);
        pedalboardDir = newDir;
        refreshPedalboardSetsUi();
    }
}
