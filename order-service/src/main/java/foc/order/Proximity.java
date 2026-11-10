/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-11-04, Order F8.1.
 * Scope: straight-line (Haversine) distance between two pickups and the
 * 1 km limit F8.1 sets. Earth radius 6,371 km, as supplier-service's
 * distance sort and the web's distance.ts use.
 * Author review: Ko-Khan, pending pull request review.
 */
package foc.order;

import foc.order.SupplierLocations.Coordinates;

final class Proximity {

    // F8.1: a courier's held pickups may be at most this far apart
    static final double MAX_APART_KM = 1.0;

    private static final double EARTH_RADIUS_KM = 6371.0;

    private Proximity() {
    }

    static double kmBetween(Coordinates a, Coordinates b) {
        double dLat = Math.toRadians(b.latitude() - a.latitude());
        double dLng = Math.toRadians(b.longitude() - a.longitude());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(a.latitude())) * Math.cos(Math.toRadians(b.latitude()))
                        * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_KM * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }

    static boolean tooFarApart(Coordinates a, Coordinates b) {
        return kmBetween(a, b) > MAX_APART_KM;
    }
}
