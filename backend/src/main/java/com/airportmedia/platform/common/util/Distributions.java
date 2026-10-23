package com.airportmedia.platform.common.util;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Turns raw per-key weights into integer percentages that sum to exactly 100. */
public final class Distributions {

    private Distributions() {}

    /** Clamp negatives to zero and scale to integer percentages summing to exactly 100. */
    public static <E extends Enum<E>> Map<E, Integer> normalize(EnumMap<E, Double> raw) {
        EnumMap<E, Double> clamped = new EnumMap<>(raw);
        double sum = 0;
        for (Map.Entry<E, Double> e : clamped.entrySet()) {
            double v = Math.max(0, e.getValue());
            e.setValue(v);
            sum += v;
        }
        @SuppressWarnings("unchecked")
        Class<E> type = (Class<E>) clamped.keySet().iterator().next().getDeclaringClass();
        EnumMap<E, Integer> result = new EnumMap<>(type);
        for (E key : clamped.keySet()) {
            result.put(key, 0);
        }
        if (sum <= 0) {
            result.put(clamped.keySet().iterator().next(), 100);
            return result;
        }
        EnumMap<E, Double> remainder = new EnumMap<>(raw);
        int assigned = 0;
        for (Map.Entry<E, Double> e : clamped.entrySet()) {
            double scaled = e.getValue() / sum * 100.0;
            int floor = (int) Math.floor(scaled);
            result.put(e.getKey(), floor);
            remainder.put(e.getKey(), scaled - floor);
            assigned += floor;
        }
        List<E> order = new ArrayList<>(clamped.keySet());
        order.sort((a, b) -> {
            int c = Double.compare(remainder.get(b), remainder.get(a));
            return c != 0 ? c : Integer.compare(a.ordinal(), b.ordinal());
        });
        int left = 100 - assigned;
        for (int i = 0; i < left; i++) {
            result.merge(order.get(i % order.size()), 1, Integer::sum);
        }
        return result;
    }
}
