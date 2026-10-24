package com.airportmedia.platform.common.util;

/** Small numeric helpers shared by the decision-pipeline scoring stages. */
public final class Numbers {

    private Numbers() {}

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public static double round4(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }
}
