package com.team18.model;

import java.time.LocalDate;
import java.util.Objects;


public class CalendarDates {
    public final String id;
    public final LocalDate date;

    public CalendarDates(String id, LocalDate date){
        this.id = id;
        this.date = date;
    }

    @Override
	public boolean equals(Object other) {
		if (this == other) return true;
        if (other == null) return false;

        if (this.getClass() != other.getClass()) return false;

        CalendarDates otherDate = (CalendarDates) other;
        return (otherDate.date.equals(this.date) && otherDate.id.equals(this.id));
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.date, this.id);
    }
}
