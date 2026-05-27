package com.smartcity.backend;

import com.uber.h3core.util.LatLng;
import org.example.Graph.Element.Point;

public class GeoUtil {

    public static double[] toXYZ(double lat, double lon) {
        double latRad = Math.toRadians(lat);
        double lonRad = Math.toRadians(lon);

        double x = Math.cos(latRad) * Math.cos(lonRad);
        double y = Math.cos(latRad) * Math.sin(lonRad);
        double z = Math.sin(latRad);

        return new double[]{x, y, z};
    }

    // ----------------------------
    // XYZ → LAT/LON
    // ----------------------------
    public static LatLng toLatLon(double x, double y, double z) {
        double latRad = Math.asin(z);
        double lonRad = Math.atan2(y, x);

        return new LatLng(
                Math.toDegrees(latRad),
                Math.toDegrees(lonRad)
        );
    }


    public static double[] normalize(double x, double y, double z) {
        double length = Math.sqrt(x * x + y * y + z * z);

        return new double[]{
                x / length,
                y / length,
                z / length
        };
    }

    // ----------------------------
    // FINAL CENTROID CALCULATION
    // ----------------------------
    public static LatLng fromXYZToLatLng(double x , double y , double z) {
        double[] norm = normalize(x,y,z);
        return toLatLon(norm[0], norm[1], norm[2]);
    }

}
