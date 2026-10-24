package com.airportmedia.platform.service.airline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.airportmedia.platform.common.enums.SponsoredContentType;
import com.airportmedia.platform.common.exception.FieldValidationException;
import com.airportmedia.platform.domain.entity.Airline;
import com.airportmedia.platform.domain.entity.Flight;
import com.airportmedia.platform.domain.entity.Screen;
import com.airportmedia.platform.domain.model.input.SponsoredContentRequest;
import com.airportmedia.platform.domain.model.output.AirlineFlightRow;
import com.airportmedia.platform.service.SimulationClock;
import com.airportmedia.platform.service.decision.DecisionService;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.WebApplicationException;
import java.time.Instant;
import com.airportmedia.platform.domain.entity.Creative;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestTransaction
class AirlineServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-23T14:30:00Z");

    @Inject
    AirlineService airlines;

    @Inject
    DecisionService decisions;

    @Inject
    SimulationClock clock;

    @Inject
    EntityManager em;

    private Airline airline(String iata) {
        return em.createQuery("from Airline where iataCode = :c", Airline.class).setParameter("c", iata).getSingleResult();
    }

    private Flight flight(String number) {
        return em.createQuery("from Flight where flightNumber = :n", Flight.class).setParameter("n", number).getSingleResult();
    }

    @Test
    void myFlightsAreTheSelectedAirlinesDeparturesWithGateDelayAndLoadFactor() {
        clock.jumpTo(NOW);

        var zephyr = airlines.flights(airline("XY").id);
        var quokka = airlines.flights(airline("ZQ").id);

        assertThat(zephyr).allMatch(f -> f.flightNumber().startsWith("XY"));
        assertThat(zephyr).filteredOn(f -> f.flightNumber().equals("XY501")).singleElement().satisfies(f -> {
            assertThat(f.gate()).isEqualTo("42");
            assertThat(f.delayMinutes()).isEqualTo(45);
            assertThat(f.status()).isEqualTo("DELAYED");
            assertThat(f.loadFactorPct()).isEqualTo(66.7);   // 240 pax on a 360-seat widebody
        });
        assertThat(quokka).extracting(AirlineFlightRow::flightNumber).contains("ZQ88").doesNotContain("XY501");
    }

    @Test
    void sponsoredContentRunsOnTheFlightsGateAndWinsAirlinePriorityWhileItBoards() {
        clock.jumpTo(NOW);
        Flight xy501 = flight("XY501");

        var content = airlines.schedule(airline("XY").id,
                new SponsoredContentRequest(xy501.id, SponsoredContentType.UPGRADE_OFFER, null, null));

        assertThat(content.purpose()).isEqualTo("SPONSORED");
        assertThat(content.status()).isEqualTo("RUNNING");
        assertThat(content.gate()).isEqualTo("42");
        assertThat(content.scheduleEnd()).isEqualTo(Instant.parse("2026-10-23T14:55:00Z"));
        assertThat(content.headline()).contains("DXB");

        Screen gate42 = em.createQuery("from Screen where code = 'SCR-GATE42'", Screen.class).getSingleResult();
        assertThat(decisions.preview(gate42.id).candidates())
                .filteredOn(c -> c.campaignId() == content.campaignId()).singleElement().satisfies(c -> {
                    assertThat(c.eligible()).isTrue();
                    assertThat(c.ruleMultiplier()).isEqualTo(1.5);
                    assertThat(c.ruleNotes()).anyMatch(n -> n.contains("airline priority"));
                });
        assertThat(airlines.content(airline("XY").id)).anyMatch(r -> r.campaignId() == content.campaignId());
    }

    @Test
    void anAirlineCannotScheduleContentForAnotherAirlinesFlight() {
        clock.jumpTo(NOW);

        assertThatThrownBy(() -> airlines.schedule(airline("ZQ").id,
                new SponsoredContentRequest(flight("XY501").id, SponsoredContentType.LOUNGE_PASS, null, null)))
                .isInstanceOf(FieldValidationException.class);
    }

    @Test
    void contentCannotBeScheduledForADepartedFlight() {
        clock.jumpTo(Instant.parse("2026-10-23T16:00:00Z"));

        assertThatThrownBy(() -> airlines.schedule(airline("XY").id,
                new SponsoredContentRequest(flight("XY501").id, SponsoredContentType.LOUNGE_PASS, null, null)))
                .isInstanceOf(WebApplicationException.class);
    }

    @Test
    void defaultSponsoredCopyIsWrittenInEnglishAndSpanish() {
        clock.jumpTo(NOW);
        Flight xy501 = flight("XY501");

        var content = airlines.schedule(airline("XY").id,
                new SponsoredContentRequest(xy501.id, SponsoredContentType.LOUNGE_PASS, null, null));

        Creative creative = em.createQuery("from Creative where campaign.id = :c", Creative.class)
                .setParameter("c", content.campaignId()).getSingleResult();
        assertThat(creative.copyIn("es").language()).isEqualTo("es");
        assertThat(creative.copyIn("es").headline()).contains("XY501").isNotEqualTo(creative.headline);
        assertThat(creative.copyIn("es").offerText()).contains("$39");
    }

    @Test
    void customSponsoredCopyIsNotTranslatedForTheAirline() {
        clock.jumpTo(NOW);
        Flight xy501 = flight("XY501");

        var content = airlines.schedule(airline("XY").id,
                new SponsoredContentRequest(xy501.id, SponsoredContentType.LOUNGE_PASS, "Our own words", "Our offer"));

        Creative creative = em.createQuery("from Creative where campaign.id = :c", Creative.class)
                .setParameter("c", content.campaignId()).getSingleResult();
        assertThat(creative.copyIn("es").language()).isEqualTo("en");
    }
}
