package com.airportmedia.platform.adapter.aidx;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Serialises leg changes as IATA_AIDX_FlightLegNotifRQ XML, structurally faithful to
 * the real message (LegIdentifier + LegData with IrregularityDelay, AirportResources,
 * OperationalStatus). Only the elements the platform consumes are populated.
 */
@ApplicationScoped
public class MockFlightEventSource implements FlightEventSource {

    @Override
    public String mode() {
        return "mock";
    }

    @Override
    public String buildFlightLegNotif(AidxLegChange c) {
        StringBuilder sb = new StringBuilder();
        sb.append("<IATA_AIDX_FlightLegNotifRQ TimeStamp=\"").append(c.occurredAt())
                .append("\" TransactionIdentifier=\"").append(c.messageRef())
                .append("\" Version=\"16.1\">\n");
        sb.append("  <Originator CompanyShortName=\"AMP-AODB\"/>\n");
        sb.append("  <FlightLeg>\n");
        sb.append("    <LegIdentifier>\n");
        sb.append("      <Airline>").append(c.airlineIata()).append("</Airline>\n");
        sb.append("      <FlightNumber>").append(c.flightNumber()).append("</FlightNumber>\n");
        sb.append("      <OriginDate>").append(c.originDate()).append("</OriginDate>\n");
        sb.append("    </LegIdentifier>\n");
        sb.append("    <LegData>\n");
        if (c.statusCode() != null) {
            sb.append("      <OperationalStatus>").append(c.statusCode()).append("</OperationalStatus>\n");
        }
        if (c.delayMinutes() != null) {
            sb.append("      <IrregularityDelay DepartureOrArrival=\"Departure\">\n");
            sb.append("        <ReasonCode>93</ReasonCode>\n");
            sb.append("        <Duration>PT").append(c.delayMinutes()).append("M</Duration>\n");
            sb.append("      </IrregularityDelay>\n");
        }
        if (c.newGate() != null) {
            sb.append("      <AirportResources DepartureOrArrival=\"Departure\">\n");
            sb.append("        <Resource>\n");
            sb.append("          <PassengerGate>").append(c.newGate()).append("</PassengerGate>\n");
            sb.append("        </Resource>\n");
            sb.append("      </AirportResources>\n");
        }
        sb.append("    </LegData>\n");
        sb.append("  </FlightLeg>\n");
        sb.append("</IATA_AIDX_FlightLegNotifRQ>\n");
        return sb.toString();
    }
}
