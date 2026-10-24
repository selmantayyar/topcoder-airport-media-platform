package com.airportmedia.platform.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Writing small JSON column values (e.g. a campaign's targeting) where failure is a programming error. */
public final class Jsons {

    private Jsons() {}

    public static String write(ObjectMapper mapper, Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise " + value, e);
        }
    }
}
