package com.airportmedia.platform.domain.model.output;

/** A creative assistant's answer to a brief: the same ad in English and Spanish. */
public record AdCopySet(AdCopy en, AdCopy es) {}
