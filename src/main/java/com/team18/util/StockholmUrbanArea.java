package com.team18.util;

public class StockholmUrbanArea {
    
    // Large bounding box for greater stockholm urban area 
    // Actual trips will need to be routed inside a smaller bounding box to avoid edge cases
    // Visualise here: https://bboxfinder.com/#59.207929,17.744295,59.447169,18.393180
    // Remove unnecessary data in sl dataset that falls outside of this region 


    // Changed to be the same as inner coordinates because of the edge cases with routes
    public static final double OUTER_MIN_LAT = 59.223832;
    public static final double OUTER_MAX_LAT = 59.432507;
    public static final double OUTER_MIN_LON = 17.779999;
    public static final double OUTER_MAX_LON = 18.358842;

    // Bounding box for inner urban area on which we will calculate heatmap and optimizer
    // Visualise here: https://bboxfinder.com/#59.223832,17.779999,59.432507,18.358842
    // To see both go to the above link, click on 'Enter coordinates' button and paste this:
    // 17.655029,59.159045,18.483104,59.510634
    // Then click add

    public static final double INNER_MIN_LAT = 59.223832;
    public static final double INNER_MAX_LAT = 59.432507;
    public static final double INNER_MIN_LON = 17.779999;
    public static final double INNER_MAX_LON = 18.358842;

}