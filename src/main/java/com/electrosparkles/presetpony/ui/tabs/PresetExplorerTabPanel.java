package com.electrosparkles.presetpony.ui.tabs;

import com.electrosparkles.presetpony.*;
import com.electrosparkles.presetpony.PresetExplorerValidator.ValidationResult;
import com.electrosparkles.presetpony.PresetExplorerValidator.ValidationStatus;
import com.electrosparkles.presetpony.ui.ControlStateDelegate;
import com.electrosparkles.presetpony.ui.StatusUpdater;
import com.electrosparkles.presetpony.ui.TabPanel;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Preset Explorer tab: folder browser for .fuse files.
 *
 * <p>Lets the user point at any folder, scans it for .fuse files, validates each one
 * via {@link PresetExplorerValidator}, and shows the results in a table with status indicators.
 * Clicking a valid row re-validates from disk and then loads the preset.</p>
 *
 * <p>Validation tiers are defined by {@link ValidationStatus}:
 * VALID (green), WARNING/ProductId mismatch (amber), INVALID (grey/unclickable).</p>
 *
 * <p>Amp-write is wired via {@code applyPresetCallback} set by the frame coordinator.</p>
 */
public class PresetExplorerTabPanel extends TabPanel {

    // ── Row model ─────────────────────────────────────────────────────────────

    private static final class ExplorerRow {
        final Path path;
        String presetName = "";
        ValidationStatus status;
        String detail = "";
        CurrentPreset preset;      // non-null only when VALID

        ExplorerRow(Path path, ValidationStatus status) {
            this.path   = path;
            this.status = status;
        }
    }

    // ── UI fields ─────────────────────────────────────────────────────────────

    private final JPanel panel;
    private final JLabel folderLabel;
    private final JLabel detailLabel;
    private final JLabel summaryLabel;
    private final JTable table;
    private final ExplorerTableModel tableModel;
    private final JCheckBox bypassWarningsCheck;

    // ── State ─────────────────────────────────────────────────────────────────

    private final List<ExplorerRow> rows = new ArrayList<>();
    private Path explorerDir;
    private SwingWorker<?, ?> scanWorker;

    /**
     * Set by the frame coordinator after construction.
     * Called when the user clicks a valid row; coordinator sets {@code current}
     * and writes to the amp when connected.
     */
    private Consumer<CurrentPreset> applyPresetCallback;

    // ── Construction ──────────────────────────────────────────────────────────

    public PresetExplorerTabPanel(StatusUpdater statusUpdater, ControlStateDelegate controlDelegate) {
        super(statusUpdater, controlDelegate);
        folderLabel  = new JLabel("(no folder selected)");
        detailLabel  = new JLabel(" ");
        summaryLabel = new JLabel(" ");
        tableModel   = new ExplorerTableModel(rows);
        table        = buildTable();
        bypassWarningsCheck = new JCheckBox("Ignore model warnings");
        panel        = buildPanel();
    }

    public void setApplyPresetCallback(Consumer<CurrentPreset> cb) {
        this.applyPresetCallback = cb;
    }

    // ── TabPanel contract ─────────────────────────────────────────────────────

    @Override public JPanel getPanel() { return panel; }

    @Override
    public void refresh(CurrentPreset preset) {
        this.current = preset;
        // Explorer state is independent of the live preset; nothing to redraw.
    }

    @Override
    public void setConnectionState(MustangConnection connection, boolean connected) {
        this.conn = connection;
    }

    // ── Panel construction ────────────────────────────────────────────────────

    private JPanel buildPanel() {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel north = new JPanel(new BorderLayout());
        north.add(buildHintLabel(), BorderLayout.NORTH);
        north.add(buildFolderRow(), BorderLayout.CENTER);
        p.add(north, BorderLayout.NORTH);

        p.add(new JScrollPane(table), BorderLayout.CENTER);
        p.add(buildBottomBar(), BorderLayout.SOUTH);
        return p;
    }

    private JLabel buildHintLabel() {
        JLabel hint = new JLabel("Point at a folder of .fuse files to browse and audition presets live. Click a preset to load it to the amp.");
        hint.setFont(hint.getFont().deriveFont(10f));
        hint.setForeground(Color.GRAY);
        hint.setBorder(BorderFactory.createEmptyBorder(0, 2, 6, 2));
        return hint;
    }

    private JPanel buildFolderRow() {
        JPanel row = new JPanel(new BorderLayout(6, 4));
        row.setBorder(BorderFactory.createEmptyBorder(4, 0, 8, 0));

        JPanel left = new JPanel(new BorderLayout(6, 0));
        left.add(new JLabel("Folder:"), BorderLayout.WEST);
        folderLabel.setFont(folderLabel.getFont().deriveFont(10f));
        folderLabel.setVerticalAlignment(SwingConstants.TOP);
        left.add(folderLabel, BorderLayout.CENTER);

        JButton chooseBtn = new JButton("Choose folder...");
        chooseBtn.addActionListener(e -> onChooseFolder());

        row.add(left, BorderLayout.CENTER);
        row.add(chooseBtn, BorderLayout.EAST);
        return row;
    }

    private JPanel buildBottomBar() {
        detailLabel.setFont(detailLabel.getFont().deriveFont(10f));
        detailLabel.setForeground(Color.DARK_GRAY);
        detailLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(210, 210, 210)),
                BorderFactory.createEmptyBorder(4, 2, 2, 2)));

        summaryLabel.setFont(summaryLabel.getFont().deriveFont(10f));
        summaryLabel.setForeground(Color.GRAY);
        summaryLabel.setBorder(BorderFactory.createEmptyBorder(2, 2, 0, 2));

        bypassWarningsCheck.setFont(bypassWarningsCheck.getFont().deriveFont(10f));
        bypassWarningsCheck.setForeground(Color.GRAY);
        bypassWarningsCheck.setBorder(BorderFactory.createEmptyBorder(2, 2, 4, 2));

        JPanel bar = new JPanel(new BorderLayout(2, 2));
        bar.add(detailLabel, BorderLayout.CENTER);
        
        JPanel south = new JPanel(new BorderLayout(2, 2));
        south.add(bypassWarningsCheck, BorderLayout.NORTH);
        south.add(summaryLabel, BorderLayout.SOUTH);
        bar.add(south, BorderLayout.SOUTH);
        
        return bar;
    }

    private JTable buildTable() {
        JTable t = new JTable(tableModel) {
            // Grey out filename and preset-name columns for INVALID rows.
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                Component c = super.prepareRenderer(renderer, row, column);
                if (!isRowSelected(row) && column < 2 && row < rows.size()) {
                    c.setForeground(rows.get(row).status == ValidationStatus.INVALID
                            ? Color.LIGHT_GRAY : getForeground());
                }
                return c;
            }
        };

        t.setFillsViewportHeight(true);
        t.setRowSelectionAllowed(true);
        t.setColumnSelectionAllowed(false);
        t.setShowVerticalLines(false);
        t.setGridColor(new Color(230, 230, 230));
        t.setAutoCreateRowSorter(true);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        t.getColumnModel().getColumn(0).setPreferredWidth(220);
        t.getColumnModel().getColumn(1).setPreferredWidth(180);
        t.getColumnModel().getColumn(2).setPreferredWidth(110);
        t.getColumnModel().getColumn(2).setMaxWidth(130);
        t.getColumnModel().getColumn(2).setMinWidth(90);

        t.getColumnModel().getColumn(2).setCellRenderer(new StatusCellRenderer());

        // Selection → populate detail bar (convert view→model index)
        t.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int viewSel = t.getSelectedRow();
            if (viewSel < 0) { detailLabel.setText(" "); return; }
            int modelSel = t.convertRowIndexToModel(viewSel);
            if (modelSel < 0 || modelSel >= rows.size()) { detailLabel.setText(" "); return; }
            String d = rows.get(modelSel).detail;
            detailLabel.setText(d == null || d.isBlank() ? " " : d);
        });

        // Click → apply (convert view→model index)
        t.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int viewRow = t.rowAtPoint(e.getPoint());
                if (viewRow < 0) return;
                int modelRow = t.convertRowIndexToModel(viewRow);
                if (modelRow >= 0 && modelRow < rows.size()) {
                    onRowClicked(rows.get(modelRow));
                }
            }
        });

        return t;
    }

    // ── Folder selection ──────────────────────────────────────────────────────

    private void onChooseFolder() {
        Path startAt = (explorerDir != null)
                ? explorerDir
                : Paths.get(System.getProperty("user.home"), "Documents", "Fender", "FUSE", "Presets");

        JFileChooser chooser = new JFileChooser(startAt.toFile());
        chooser.setDialogTitle("Choose folder containing .fuse presets");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);

        if (chooser.showOpenDialog(panel) != JFileChooser.APPROVE_OPTION) return;

        explorerDir = chooser.getSelectedFile().toPath();
        folderLabel.setText("<html><div style='width:350px'>" + explorerDir + "</div></html>");
        scanFolder();
    }

    // ── Folder scan ───────────────────────────────────────────────────────────

    private void scanFolder() {
        if (explorerDir == null) return;

        if (scanWorker != null && !scanWorker.isDone()) {
            scanWorker.cancel(true);
        }

        rows.clear();
        tableModel.fireTableDataChanged();
        detailLabel.setText(" ");
        summaryLabel.setText("Scanning\u2026");

        List<Path> fuseFiles;
        try {
            fuseFiles = PresetExplorerValidator.discoverFuseFiles(explorerDir);
        } catch (IOException ex) {
            summaryLabel.setText("Could not read folder: " + ex.getMessage());
            return;
        }

        if (fuseFiles.isEmpty()) {
            summaryLabel.setText("No .fuse files found in this folder.");
            return;
        }

        summaryLabel.setText("Found " + fuseFiles.size()
                + (fuseFiles.size() == 1 ? " file" : " files") + " \u2014 validating\u2026");

        scanWorker = new SwingWorker<Void, ExplorerRow>() {
            @Override
            protected Void doInBackground() {
                for (Path path : fuseFiles) {
                    if (isCancelled()) break;
                    publish(toRow(path, PresetExplorerValidator.validate(path)));
                }
                return null;
            }

            @Override
            protected void process(List<ExplorerRow> chunks) {
                for (ExplorerRow row : chunks) {
                    rows.add(row);
                    tableModel.fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
                }
                updateSummary();
            }

            @Override
            protected void done() {
                if (!isCancelled()) updateSummary();
            }
        };
        scanWorker.execute();
    }

    /** Converts a {@link ValidationResult} into an {@link ExplorerRow} for the table model. */
    private static ExplorerRow toRow(Path path, ValidationResult result) {
        ExplorerRow row = new ExplorerRow(path, result.status);
        row.presetName = result.presetName;
        row.detail     = result.detail;
        row.preset     = result.preset;
        return row;
    }

    private void updateSummary() {
        long valid   = rows.stream().filter(r -> r.status == ValidationStatus.VALID).count();
        long warning = rows.stream().filter(r -> r.status == ValidationStatus.WARNING).count();
        long invalid = rows.stream().filter(r -> r.status == ValidationStatus.INVALID).count();

        StringBuilder sb = new StringBuilder();
        sb.append(rows.size()).append(rows.size() == 1 ? " file" : " files").append(" found");
        if (valid   > 0) sb.append(" \u00b7 ").append(valid).append(" valid");
        if (warning > 0) sb.append(" \u00b7 ").append(warning).append(warning == 1 ? " warning" : " warnings");
        if (invalid > 0) sb.append(" \u00b7 ").append(invalid).append(" invalid");
        summaryLabel.setText(sb.toString());
    }

    // ── Helper methods ────────────────────────────────────────────────────────

    private String extractProductId(String detail) {
        // Extract ProductId from messages like: Unsupported ProductId="13" - this app only supports...
        int start = detail.indexOf("ProductId=\"");
        if (start >= 0) {
            start += 11; // length of 'ProductId="'
            int end = detail.indexOf('"', start);
            if (end > start) {
                return detail.substring(start, end);
            }
        }
        return "unknown";
    }

    // ── Row click ─────────────────────────────────────────────────────────────

    private void onRowClicked(ExplorerRow row) {
        switch (row.status) {
            case INVALID:
                return; // Unclickable — user reads the detail bar.

            case WARNING:
                // ProductId mismatch — check bypass flag.
                if (!bypassWarningsCheck.isSelected()) {
                    JOptionPane.showMessageDialog(panel,
                            "<html><b>Cannot load this preset.</b><br><br>"
                            + "It was created for a different Mustang model (ProductId mismatch).<br>"
                            + "Loading it would likely produce incorrect amp and effect settings.<br><br>"
                            + "Check \"Ignore model warnings\" below to override this check.<br><br>"
                            + "<i>" + row.detail + "</i></html>",
                            "Wrong model", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                // Bypass enabled — extract ProductId and show simplified warning, then attempt to load.
                String productId = extractProductId(row.detail);
                detailLabel.setText("Unsupported ProductId of '" + productId + "'. Unexpected results may occur.");
                
                ValidationResult bypassRecheck = PresetExplorerValidator.validateIgnoringWarnings(row.path);
                if (bypassRecheck.status == ValidationStatus.INVALID) {
                    int modelIdx = rows.indexOf(row);
                    row.status     = bypassRecheck.status;
                    row.detail     = bypassRecheck.detail;
                    row.presetName = bypassRecheck.presetName;
                    row.preset     = null;
                    if (modelIdx >= 0) tableModel.fireTableRowsUpdated(modelIdx, modelIdx);
                    detailLabel.setText(row.detail);
                    JOptionPane.showMessageDialog(panel,
                            "<html><b>Could not load preset.</b><br><br>"
                            + "File validation failed (may have been corrupted or moved).<br><br>"
                            + "<i>" + bypassRecheck.detail + "</i></html>",
                            "Load failed", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                // Use the revalidated preset with warning bypassed.
                if (bypassRecheck.preset != null && applyPresetCallback != null) {
                    applyPresetCallback.accept(bypassRecheck.preset);
                }
                if (conn == null) {
                    updateStatus("Loaded: " + row.path.getFileName()
                            + " (warning bypassed - preview only; connect to send to amp)");
                }
                return;

            case VALID:
                // Re-validate from disk — file may have been deleted or changed since the scan.
                ValidationResult recheck = PresetExplorerValidator.validate(row.path);
                if (recheck.status != ValidationStatus.VALID) {
                    int modelIdx = rows.indexOf(row);
                    row.status     = recheck.status;
                    row.detail     = recheck.detail;
                    row.presetName = recheck.presetName;
                    row.preset     = null;
                    if (modelIdx >= 0) tableModel.fireTableRowsUpdated(modelIdx, modelIdx);
                    detailLabel.setText(row.detail);
                    JOptionPane.showMessageDialog(panel,
                            "<html><b>Could not load preset.</b><br><br>"
                            + "The file may have been moved or changed since the folder was scanned.<br><br>"
                            + "<i>" + recheck.detail + "</i></html>",
                            "Load failed", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                if (applyPresetCallback != null) {
                    applyPresetCallback.accept(recheck.preset);
                }
                // When connected, the SwingWorker in PresetPony updates status to
                // "Applying preset…" then "Connected" — don't overwrite that here.
                if (conn == null) {
                    updateStatus("Loaded: " + row.path.getFileName()
                            + " (preview \u2014 connect to send to amp)");
                }
        }
    }

    // ── Table model ───────────────────────────────────────────────────────────

    private static final class ExplorerTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = { "Filename", "Preset Name", "Status" };
        private final List<ExplorerRow> rows;

        ExplorerTableModel(List<ExplorerRow> rows) { this.rows = rows; }

        @Override public int getRowCount()    { return rows.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }
        @Override public boolean isCellEditable(int row, int col) { return false; }

        @Override
        public Class<?> getColumnClass(int col) {
            return switch (col) {
                case 0, 1 -> String.class;
                case 2    -> ValidationStatus.class;
                default   -> Object.class;
            };
        }

        @Override
        public Object getValueAt(int row, int col) {
            ExplorerRow r = rows.get(row);
            return switch (col) {
                case 0 -> r.path.getFileName().toString();
                case 1 -> r.presetName;
                case 2 -> r.status;
                default -> "";
            };
        }
    }

    // ── Status cell renderer ──────────────────────────────────────────────────

    private static final class StatusCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (value instanceof ValidationStatus status) {
                if (isSelected) {
                    switch (status) {
                        case VALID   -> setText("\u2713 Valid");
                        case WARNING -> setText("\u26a0 Wrong model");
                        case INVALID -> setText("\u2717 Invalid");
                    }
                } else {
                    switch (status) {
                        case VALID   -> { setText("\u2713 Valid");       setForeground(new Color(0, 140, 0)); }
                        case WARNING -> { setText("\u26a0 Wrong model"); setForeground(new Color(185, 110, 0)); }
                        case INVALID -> { setText("\u2717 Invalid");     setForeground(Color.LIGHT_GRAY); }
                    }
                }
            }
            return this;
        }
    }
}
