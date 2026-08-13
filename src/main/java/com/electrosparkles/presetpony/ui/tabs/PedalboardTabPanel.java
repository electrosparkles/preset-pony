package com.electrosparkles.presetpony.ui.tabs;

import com.electrosparkles.presetpony.*;
import com.electrosparkles.presetpony.ui.ControlStateDelegate;
import com.electrosparkles.presetpony.ui.StatusUpdater;
import com.electrosparkles.presetpony.ui.TabPanel;
import com.electrosparkles.presetpony.ui.components.PedalboardTableModel;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.List;

/**
 * Pedalboards tab: file browser, MRU strip, save/load/rename/delete.
 */
public class PedalboardTabPanel extends TabPanel {
    private final JPanel panel;

    private static final int MRU_DISPLAY_LIMIT = 6;
    private Path pedalboardDir;
    private Path currentlyLoadedBoard;  // Track which pedalboard is currently loaded
    private final Map<Path, Pedalboard> pedalboardCache = new HashMap<>();
    private final List<Path> pedalboardRows = new ArrayList<>();
    private final PedalboardTableModel tableModel;
    private final JTable pedalboardTable;
    private final JPanel pedalboardMruChips;
    private final JLabel pedalboardFolderLabel;
    private final JButton saveButton;  // Only enabled when a board is loaded

    public PedalboardTabPanel(StatusUpdater statusUpdater, ControlStateDelegate controlDelegate) {
        super(statusUpdater, controlDelegate);
        tableModel = new PedalboardTableModel(pedalboardRows, pedalboardCache);
        pedalboardTable = new JTable(tableModel);
        pedalboardMruChips = new JPanel();
        pedalboardFolderLabel = new JLabel();
        saveButton = new JButton("Save");
        currentlyLoadedBoard = null;
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

        JLabel hintLabel = new JLabel("Save and recall your four effects slots as a named pedalboard, independent of your amp preset.");
        hintLabel.setFont(hintLabel.getFont().deriveFont(10f));
        hintLabel.setForeground(Color.GRAY);
        hintLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        hintLabel.setBorder(BorderFactory.createEmptyBorder(0, 2, 6, 2));
        top.add(hintLabel);

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

        // Table with light horizontal grid lines only
        pedalboardTable.setFillsViewportHeight(true);
        pedalboardTable.setRowSelectionAllowed(true);
        pedalboardTable.setColumnSelectionAllowed(false);
        pedalboardTable.setAutoCreateRowSorter(false);
        pedalboardTable.setShowVerticalLines(false);
        pedalboardTable.setGridColor(new Color(230, 230, 230));
        
        DefaultTableCellRenderer modifiedRenderer = new DefaultTableCellRenderer();
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
        
        saveButton.setEnabled(false);  // Only enable when a board is loaded
        saveAsButton.addActionListener(e -> onSavePedalboardAs());
        saveButton.addActionListener(e -> onSavePedalboard());
        loadButton.addActionListener(e -> {
            Path selected = selectedPedalboardPath();
            if (selected != null) applyPedalboard(selected);
        });
        renameButton.addActionListener(e -> onRenamePedalboard());
        deleteButton.addActionListener(e -> onDeletePedalboard());

        JPanel buttonsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        buttonsRow.add(saveAsButton);
        buttonsRow.add(saveButton);
        buttonsRow.add(loadButton);
        buttonsRow.add(renameButton);
        buttonsRow.add(deleteButton);
        panel.add(buttonsRow, BorderLayout.SOUTH);

        pedalboardDir = AppSettings.pedalboardsFolder();
        refreshPedalboardsUi();

        return panel;
    }

    private Path selectedPedalboardPath() {
        int row = pedalboardTable.getSelectedRow();
        return (row >= 0 && row < pedalboardRows.size()) ? pedalboardRows.get(row) : null;
    }

    private void refreshPedalboardsUi() {
        String pathText = pedalboardDir.toString();
        if (!AppSettings.isPedalboardsFolderConfigured()) {
            pathText += "  (not yet chosen - will be set on first save)";
        }
        // Wrap in HTML with width constraint to force text wrapping
        pedalboardFolderLabel.setText("<html><div style='width:300px'>" + pathText + "</div></html>");

        pedalboardRows.clear();
        pedalboardCache.clear();
        try {
            for (Path file : PedalboardStore.list(pedalboardDir)) {
                try {
                    pedalboardCache.put(file, PedalboardStore.peek(file));
                } catch (Exception ignored) {
                }
                pedalboardRows.add(file);
            }
        } catch (IOException ex) {
            updateStatus("Couldn't list pedalboards: " + ex.getMessage());
        }
        tableModel.fireTableDataChanged();

        pedalboardMruChips.removeAll();
        try {
            List<Path> recent = PedalboardStore.recent(pedalboardDir);
            for (int i = 0; i < Math.min(MRU_DISPLAY_LIMIT, recent.size()); i++) {
                Path file = recent.get(i);
                Pedalboard cached = pedalboardCache.get(file);
                String label = (cached != null) ? cached.name() : file.getFileName().toString();
                JButton chip = new JButton(label);
                chip.addActionListener(e -> applyPedalboard(file));
                pedalboardMruChips.add(chip);
            }
        } catch (IOException ignored) {
        }
        pedalboardMruChips.revalidate();
        pedalboardMruChips.repaint();
    }

    private boolean ensurePedalboardFolderChosen() {
        if (AppSettings.isPedalboardsFolderConfigured()) return true;

        Path suggested = AppSettings.defaultPedalboardsFolder();
        Object[] options = {"Use this folder", "Choose a different folder..."};
        int choice = JOptionPane.showOptionDialog(panel,
                "Where should your pedalboard be saved?\n\nSuggested:\n" + suggested,
                "Choose Pedalboard folder", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]);

        Path chosen;
        if (choice == 0) {
            chosen = suggested;
        } else if (choice == 1) {
            JFileChooser chooser = new JFileChooser(suggested.getParent() != null ? suggested.getParent().toFile() : suggested.toFile());
            chooser.setDialogTitle("Choose Pedalboards folder");
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(panel) != JFileChooser.APPROVE_OPTION) return false;
            chosen = chooser.getSelectedFile().toPath();
        } else {
            return false;
        }

        AppSettings.setPedalboardsFolder(chosen);
        pedalboardDir = chosen;
        refreshPedalboardsUi();
        return true;
    }

    private void onSavePedalboardAs() {
        if (current == null) {
            JOptionPane.showMessageDialog(panel, "Connect (or import a preset) first.",
                    "Pedalboard", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!ensurePedalboardFolderChosen()) return;

        String name = JOptionPane.showInputDialog(panel, "Name for this pedalboard:",
                "Save Pedalboard", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.isBlank()) return;

        try {
            Path saved = PedalboardStore.save(pedalboardDir, Pedalboard.capture(name.trim(), current.effects()));
            currentlyLoadedBoard = saved;
            saveButton.setEnabled(true);
            refreshPedalboardsUi();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(panel, "Could not save pedalboard:\n" + ex.getMessage(),
                    "Save failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onSavePedalboard() {
        if (currentlyLoadedBoard == null || current == null) {
            JOptionPane.showMessageDialog(panel, "Load a pedalboard first, then modify and save.",
                    "Pedalboards", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            PedalboardStore.overwrite(currentlyLoadedBoard, Pedalboard.capture(
                    pedalboardCache.get(currentlyLoadedBoard).name(), current.effects()));
            updateStatus("Saved to " + currentlyLoadedBoard.getFileName());
            refreshPedalboardsUi();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(panel, "Could not save pedalboard:\n" + ex.getMessage(),
                    "Save failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void applyPedalboard(Path file) {
        if (current == null) {
            JOptionPane.showMessageDialog(panel, "Connect (or import a preset) first.",
                    "Pedalboard", JOptionPane.WARNING_MESSAGE);
            return;
        }
        updateStatus(conn == null ? "Applying pedalboard (preview only - not connected)..." : "Applying pedalboard ...");

        new SwingWorker<Pedalboard, Void>() {
            @Override
            protected Pedalboard doInBackground() throws Exception {
                Pedalboard pedalboard
                        = PedalboardStore.load(file);
                if (conn != null) {
                    for (int slot = 0; slot < 4; slot++) {
                        conn.writeEffectSettings(slot, pedalboard.effects()[slot]);
                    }
                }
                return pedalboard;
            }

            @Override
            protected void done() {
                try {
                    Pedalboard pedalboard = get();
                    current = new CurrentPreset(current.presetNumber(), current.name(), current.amp(),
                            pedalboard.effects(), current.presetNames());
                    currentlyLoadedBoard = file;
                    saveButton.setEnabled(true);
                    updateStatus(conn == null ? "Preview only (not connected) - change not sent" : "Connected");
                } catch (Exception ex) {
                    currentlyLoadedBoard = null;
                    saveButton.setEnabled(false);
                    updateStatus("Error: " + ex.getMessage());
                    JOptionPane.showMessageDialog(panel, "Couldn't load that pedalboard:\n" + ex.getMessage(),
                            "Pedalboards", JOptionPane.ERROR_MESSAGE);
                }
                refreshPedalboardsUi();
            }
        }.execute();
    }

    private void onRenamePedalboard() {
        Path selected = selectedPedalboardPath();
        if (selected == null) return;
        Pedalboard existing = pedalboardCache.get(selected);
        if (existing == null) {
            JOptionPane.showMessageDialog(panel, "That file couldn't be read - fix or remove it before renaming.",
                    "Pedalboard ", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String newName = (String) JOptionPane.showInputDialog(panel, "Rename to:", "Rename Pedalboard",
                JOptionPane.PLAIN_MESSAGE, null, null, existing.name());
        if (newName == null || newName.isBlank() || newName.trim().equals(existing.name())) return;

        try {
            PedalboardStore.delete(selected);
            Path renamed = PedalboardStore.save(pedalboardDir, existing.withName(newName.trim()));
            if (currentlyLoadedBoard != null && currentlyLoadedBoard.equals(selected)) {
                currentlyLoadedBoard = renamed;
            }
            refreshPedalboardsUi();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(panel, "Could not rename board\n" + ex.getMessage(),
                    "Rename failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onDeletePedalboard() {
        Path selected = selectedPedalboardPath();
        if (selected == null) return;
        Pedalboard existing = pedalboardCache.get(selected);
        String label = (existing != null) ? existing.name() : selected.getFileName().toString();

        int confirm = JOptionPane.showConfirmDialog(panel, "Delete pedalboard \"" + label + "\"?\nThis cannot be undone.",
                "Confirm delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        try {
            PedalboardStore.delete(selected);
            if (currentlyLoadedBoard != null && currentlyLoadedBoard.equals(selected)) {
                currentlyLoadedBoard = null;
                saveButton.setEnabled(false);
            }
            refreshPedalboardsUi();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(panel, "Could not delete board:\n" + ex.getMessage(),
                    "Delete failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onChangePedalboardFolder() {
        Path startAt = AppSettings.isPedalboardsFolderConfigured() ? pedalboardDir : AppSettings.defaultPedalboardsFolder();
        JFileChooser chooser = new JFileChooser(startAt.toFile());
        chooser.setDialogTitle("Choose pedalboard folder");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        if (chooser.showOpenDialog(panel) != JFileChooser.APPROVE_OPTION) return;
        Path newDir = chooser.getSelectedFile().toPath();

        AppSettings.setPedalboardsFolder(newDir);
        pedalboardDir = newDir;
        refreshPedalboardsUi();
    }
}
