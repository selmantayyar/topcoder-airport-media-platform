package com.airportmedia.platform.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

/**
 * Boots the app (which auto-seeds a small airport under the %test profile) and checks
 * that every feed translated into canonical entities and the generated day is plausible
 * and internally consistent.
 */
@QuarkusTest
@TestTransaction
class SeedServiceTest {

    @Inject
    EntityManager em;

    private long count(String entity) {
        return em.createQuery("select count(x) from " + entity + " x", Long.class).getSingleResult();
    }

    @Test
    void acrisTopologyIsPopulated() {
        assertThat(count("Terminal")).isEqualTo(2);
        assertThat(count("Zone")).isGreaterThan(0);
        assertThat(count("Checkpoint")).isGreaterThan(0);
        assertThat(count("QueueMeasurement")).isGreaterThan(0);
    }

    @Test
    void everyFlightHasAGateAssignmentAirlineAndAircraft() {
        long flights = count("Flight");
        assertThat(flights).isEqualTo(40);
        assertThat(count("GateAssignment")).isEqualTo(flights);
        assertThat(em.createQuery(
                "select count(f) from Flight f where f.airline is null or f.aircraftType is null", Long.class)
                .getSingleResult()).isZero();
        assertThat(em.createQuery(
                "select count(g) from GateAssignment g where g.zone is null", Long.class)
                .getSingleResult()).isZero();
    }

    @Test
    void workedExampleFlightIsReproducible() {
        Long delayed = em.createQuery(
                "select count(f) from Flight f where f.flightNumber = 'XY501' "
                        + "and f.destination = 'DXB' and f.delayMinutes = 45", Long.class)
                .getSingleResult();
        assertThat(delayed).isEqualTo(1);
    }

    @Test
    void aidxDelaysProducedFlightEvents() {
        assertThat(count("FlightEvent")).isGreaterThan(0);
    }

    @Test
    void platformInventoryIsSeeded() {
        // 20 random screens (%test config) plus the deterministic gate-42 worked-example screen.
        assertThat(count("Screen")).isEqualTo(21);
        assertThat(count("Outlet")).isEqualTo(8);
        assertThat(count("Advertiser")).isGreaterThan(0);
        // 6 random campaigns (%test config) plus the 3 Gate 42 worked-example campaigns.
        assertThat(count("Campaign")).isEqualTo(9);
        assertThat(count("Creative")).isGreaterThan(0);
        assertThat(count("Impression")).isGreaterThan(0);
    }

    @Test
    void gate42WorkedExampleCampaignsAreSeededAndRunning() {
        Long running = em.createQuery(
                "select count(c) from Campaign c where c.status = 'RUNNING' and "
                        + "(c.name like 'Azure Duty-Free%' or c.name like 'Bean & Brew Coffee%' "
                        + "or c.name like 'Luxe Watches%')", Long.class)
                .getSingleResult();
        assertThat(running).isEqualTo(3);

        Long zq88 = em.createQuery(
                "select count(f) from Flight f where f.flightNumber = 'ZQ88' and f.destination = 'MLE'", Long.class)
                .getSingleResult();
        assertThat(zq88).isEqualTo(1);

        Long gate42Screen = em.createQuery(
                "select count(s) from Screen s where s.code = 'SCR-GATE42' and s.status = 'ONLINE' "
                        + "and s.nearestGates like '%42%'", Long.class)
                .getSingleResult();
        assertThat(gate42Screen).isEqualTo(1);
    }
}
