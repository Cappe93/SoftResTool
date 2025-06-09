package com.warcraftlogs.dto;

import java.util.ArrayList;
import java.util.List;

public class AttendanceEntry {
    public String code;
    public String zoneName;
    public int zoneId;
    public List<Player> players = new ArrayList<>();
    public long timestamp;
}
