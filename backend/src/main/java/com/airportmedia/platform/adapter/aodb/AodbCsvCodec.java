package com.airportmedia.platform.adapter.aodb;

import java.util.ArrayList;
import java.util.List;

/**
 * Encodes/decodes the 33-column AODB CSV export. Keeping the header and column order
 * in one place is what lets the mock feed emit bytes identical in shape to a real
 * vendor export, and lets a future Live adapter parse that same export.
 */
public final class AodbCsvCodec {

    public static final String[] HEADER = {
        "flight_number", "airline_iata", "airline_icao", "direction", "origin_iata",
        "destination_iata", "via_iata", "aircraft_icao_type", "aircraft_registration",
        "pax_count", "std", "etd", "atd", "sta", "eta", "ata", "tobt", "tsat", "aibt",
        "aobt", "gate", "stand", "terminal", "check_in_counters", "baggage_belt",
        "status", "delay_minutes", "delay_code_1", "delay_code_2", "seats_economy",
        "seats_premium", "seats_business", "haul"
    };

    private AodbCsvCodec() {}

    public static String encode(List<AodbRecord> rows) {
        StringBuilder sb = new StringBuilder(String.join(",", HEADER)).append('\n');
        for (AodbRecord row : rows) {
            sb.append(String.join(",", cells(row))).append('\n');
        }
        return sb.toString();
    }

    public static List<AodbRecord> decode(String csv) {
        List<AodbRecord> rows = new ArrayList<>();
        String[] lines = csv.strip().split("\n", -1);
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isBlank()) {
                continue;
            }
            String[] c = lines[i].split(",", -1);
            rows.add(new AodbRecord(
                    c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7], c[8], c[9], c[10],
                    c[11], c[12], c[13], c[14], c[15], c[16], c[17], c[18], c[19], c[20],
                    c[21], c[22], c[23], c[24], c[25], c[26], c[27], c[28], c[29], c[30],
                    c[31], c[32]));
        }
        return rows;
    }

    private static String[] cells(AodbRecord r) {
        return new String[] {
            r.flightNumber(), r.airlineIata(), r.airlineIcao(), r.direction(), r.originIata(),
            r.destinationIata(), r.viaIata(), r.aircraftIcaoType(), r.aircraftRegistration(),
            r.paxCount(), r.std(), r.etd(), r.atd(), r.sta(), r.eta(), r.ata(), r.tobt(),
            r.tsat(), r.aibt(), r.aobt(), r.gate(), r.stand(), r.terminal(), r.checkInCounters(),
            r.baggageBelt(), r.status(), r.delayMinutes(), r.delayCode1(), r.delayCode2(),
            r.seatsEconomy(), r.seatsPremium(), r.seatsBusiness(), r.haul()
        };
    }
}
