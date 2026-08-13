package com.electrosparkles.presetpony.ui.components;

import com.electrosparkles.presetpony.Pedalboard;
import javax.swing.table.AbstractTableModel;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Table model for pedalboard list (Name, Modified columns).
 * Extracted from inline anonymous class in PresetPony.
 */
public class PedalboardTableModel extends AbstractTableModel {
    private final List<Path> pedalboardRows;
    private final Map<Path, Pedalboard> pedalboardCache;
    private final String[] columns = {"Name", "Modified"};

    public PedalboardTableModel(List<Path> pedalboardRows, Map<Path, Pedalboard> pedalboardCache) {
        this.pedalboardRows = pedalboardRows;
        this.pedalboardCache = pedalboardCache;
    }

    @Override
    public int getRowCount() {
        return pedalboardRows.size();
    }

    @Override
    public int getColumnCount() {
        return columns.length;
    }

    @Override
    public String getColumnName(int column) {
        return columns[column];
    }

    @Override
    public Object getValueAt(int row, int column) {
        Path path = pedalboardRows.get(row);
        Pedalboard pedalboard = pedalboardCache.get(path);
        if (column == 0) {
            return (pedalboard != null) ? pedalboard.name() : ("\u26A0 " + path.getFileName() + " (couldn't be read)");
        }
        return (pedalboard != null) ? formatPedalboardTimestamp(pedalboard.modified()) : "";
    }

    private static String formatPedalboardTimestamp(Instant instant) {
        String s = instant.truncatedTo(ChronoUnit.SECONDS).toString();
        return s.replace('T', ' ').replace("Z", "");
    }
}
