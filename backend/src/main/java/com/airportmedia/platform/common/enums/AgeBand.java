package com.airportmedia.platform.common.enums;

/** Coarse age brackets used for crowd-profile estimation. */
public enum AgeBand {
    A18_24("18-24"),
    A25_44("25-44"),
    A45_64("45-64"),
    A65_PLUS("65+");

    public final String label;

    AgeBand(String label) {
        this.label = label;
    }
}
