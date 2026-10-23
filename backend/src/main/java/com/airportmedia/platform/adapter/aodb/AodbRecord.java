package com.airportmedia.platform.adapter.aodb;

/**
 * One AODB flight-movement row exactly as a vendor export delivers it: 33 string
 * columns, A-CDM milestones included. This is the wire shape; {@link AodbTranslator}
 * turns it into canonical entities. Field order matches {@link AodbCsvCodec#HEADER}.
 */
public record AodbRecord(
        String flightNumber,
        String airlineIata,
        String airlineIcao,
        String direction,
        String originIata,
        String destinationIata,
        String viaIata,
        String aircraftIcaoType,
        String aircraftRegistration,
        String paxCount,
        String std,
        String etd,
        String atd,
        String sta,
        String eta,
        String ata,
        String tobt,
        String tsat,
        String aibt,
        String aobt,
        String gate,
        String stand,
        String terminal,
        String checkInCounters,
        String baggageBelt,
        String status,
        String delayMinutes,
        String delayCode1,
        String delayCode2,
        String seatsEconomy,
        String seatsPremium,
        String seatsBusiness,
        String haul) {}
