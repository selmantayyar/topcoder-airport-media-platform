package com.airportmedia.platform.common.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Helpers for turning grouped JPQL tuple results into maps. */
public final class Aggregates {

    private Aggregates() {}

    /** Collect {@code (Long key, Long count)} tuple rows into a map. */
    public static Map<Long, Long> toLongCountMap(List<Object[]> rows) {
        Map<Long, Long> out = new HashMap<>();
        for (Object[] row : rows) {
            out.put((Long) row[0], (Long) row[1]);
        }
        return out;
    }
}
