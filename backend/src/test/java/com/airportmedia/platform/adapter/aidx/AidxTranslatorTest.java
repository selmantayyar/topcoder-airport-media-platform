package com.airportmedia.platform.adapter.aidx;

import static org.assertj.core.api.Assertions.assertThat;

import com.airportmedia.platform.common.enums.FlightEventType;
import com.airportmedia.platform.common.enums.FlightStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AidxTranslatorTest {

    private final AidxTranslator translator = new AidxTranslator();

    private static final String DELAY_MESSAGE = """
        <IATA_AIDX_FlightLegNotifRQ TimeStamp="2026-10-23T14:15:00Z" TransactionIdentifier="AMP-AIDX-00017" Version="16.1">
          <Originator CompanyShortName="AMP-AODB"/>
          <FlightLeg>
            <LegIdentifier>
              <Airline>XY</Airline>
              <FlightNumber>501</FlightNumber>
              <OriginDate>2026-10-23</OriginDate>
              <DepartureAirport>AMP</DepartureAirport>
              <ArrivalAirport>DXB</ArrivalAirport>
            </LegIdentifier>
            <LegData>
              <OperationalStatus>DL</OperationalStatus>
              <IrregularityDelay DepartureOrArrival="Departure">
                <ReasonCode>93</ReasonCode>
                <Duration>PT45M</Duration>
              </IrregularityDelay>
              <AirportResources DepartureOrArrival="Departure">
                <Resource>
                  <PassengerGate>42</PassengerGate>
                </Resource>
              </AirportResources>
              <OperationTime OperationQualifier="ETD">2026-10-23T14:55:00Z</OperationTime>
            </LegData>
          </FlightLeg>
        </IATA_AIDX_FlightLegNotifRQ>
        """;

    private static final String GATE_CHANGE_MESSAGE = """
        <IATA_AIDX_FlightLegNotifRQ TimeStamp="2026-10-23T13:00:00Z" TransactionIdentifier="AMP-AIDX-00009" Version="16.1">
          <FlightLeg>
            <LegIdentifier>
              <Airline>ZQ</Airline>
              <FlightNumber>88</FlightNumber>
              <OriginDate>2026-10-23</OriginDate>
            </LegIdentifier>
            <LegData>
              <OperationalStatus>SH</OperationalStatus>
              <AirportResources DepartureOrArrival="Departure">
                <Resource>
                  <PassengerGate>31</PassengerGate>
                </Resource>
              </AirportResources>
            </LegData>
          </FlightLeg>
        </IATA_AIDX_FlightLegNotifRQ>
        """;

    @Test
    void translatesADelayMessage() {
        AidxFlightLegUpdate update = translator.parse(DELAY_MESSAGE);

        assertThat(update.airlineCode()).isEqualTo("XY");
        assertThat(update.flightNumber()).isEqualTo("501");
        assertThat(update.fullFlightNumber()).isEqualTo("XY501");
        assertThat(update.originDate()).isEqualTo("2026-10-23");
        assertThat(update.type()).isEqualTo(FlightEventType.DELAY);
        assertThat(update.delayMinutes()).isEqualTo(45);
        assertThat(update.newGate()).isEqualTo("42");
        assertThat(update.newStatus()).isEqualTo(FlightStatus.DELAYED);
        assertThat(update.occurredAt()).isEqualTo(Instant.parse("2026-10-23T14:15:00Z"));
        assertThat(update.messageRef()).isEqualTo("AMP-AIDX-00017");
    }

    @Test
    void classifiesAGateChangeWhenNoDelayIsPresent() {
        AidxFlightLegUpdate update = translator.parse(GATE_CHANGE_MESSAGE);

        assertThat(update.type()).isEqualTo(FlightEventType.GATE_CHANGE);
        assertThat(update.newGate()).isEqualTo("31");
        assertThat(update.delayMinutes()).isNull();
        assertThat(update.fullFlightNumber()).isEqualTo("ZQ88");
    }
}
