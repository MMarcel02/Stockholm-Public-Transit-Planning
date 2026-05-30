package com.team18.routing;

import java.util.List;
import com.team18.model.RouteStep;

public interface Router {

    List<RouteStep> getFastestTrip(double latFrom, double lonFrom, double latTo, double lonTo, int startTimeSecondsAfterMidnight);
}