package com.airportmedia.platform.common.enums;

/** The kinds of field the generic master-data form can render and validate. */
public enum FieldType {
    TEXT,
    NUMBER,
    BOOLEAN,
    /** A fixed set of values (e.g. a status). */
    ENUM,
    /** A reference to a row of another master-data type, by id. */
    REF
}
