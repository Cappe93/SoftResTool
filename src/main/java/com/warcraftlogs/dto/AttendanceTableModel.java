package com.warcraftlogs.dto;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public class AttendanceTableModel extends AbstractTableModel {
    private final String[] columnNames = {"Report Code", "Zone", "Player Name", "Player Type", "Presence"};
    private final List<Object[]> data;

    public AttendanceTableModel(List<AttendanceEntry> attendanceEntries) {
        data = new ArrayList<>();
        for (AttendanceEntry entry : attendanceEntries) {
            for (Player player : entry.players) {
                data.add(new Object[]{
                        entry.code,
                        entry.zoneName,
                        player.name,
                        player.type,
                        player.presence == 1 ? "Present" : "Absent"
                });
            }
        }
    }

    @Override public int getRowCount() { return data.size(); }
    @Override public int getColumnCount() { return columnNames.length; }
    @Override public String getColumnName(int col) { return columnNames[col]; }
    @Override public Object getValueAt(int row, int col) { return data.get(row)[col]; }
}