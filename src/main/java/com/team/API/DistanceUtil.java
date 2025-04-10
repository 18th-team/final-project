package com.team.API;
//거리계산로직
//Haversine 공식: 두 지점 간 거리 계산 (단위: km).
public class DistanceUtil {
    private static final
    double EARTH_RADIUS_KM = 6378.137;

    public static double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)+Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))*Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c =  2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return EARTH_RADIUS_KM * c;
    }
}
