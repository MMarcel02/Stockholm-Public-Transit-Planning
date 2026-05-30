package com.team18.model;

public enum RouteStepType {
    WALK_TO_STOP,    // Starting coordinates -> Initial Stop
    TRANSFER,        // Stop -> Stop
    WALK_TO_DEST,    // Final Stop -> Destination coordinates
    DIRECT_WALK,     // Starting coordinates -> Destination coordinates
    TRANSIT          // Riding a vehicle
}
