package com.team18.model;

public class Calendar {
    public final String id;
    public final String week;
    public final String startDate;
    public final String endDate;

    public Calendar(String id, String week, String startDate, String endDate) {
        this.id = id;
        this.week = week;
        this.startDate = startDate;
        this.endDate = endDate;
    }
}
