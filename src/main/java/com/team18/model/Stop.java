package com.team18.model;

import java.util.Objects;

public class Stop {
    public final String id;
    public final String name;
    public final double lat;
    public final double lon;

    public Stop(String id, String name, double lat, double lon) {
        this.id = id;
        this.name = name;
        this.lat = lat;
        this.lon = lon;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Stop stop = (Stop) o;
        return Objects.equals(id, stop.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}