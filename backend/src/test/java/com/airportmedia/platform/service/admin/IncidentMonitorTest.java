package com.airportmedia.platform.service.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.airportmedia.platform.common.enums.IncidentSeverity;
import com.airportmedia.platform.common.enums.IncidentStatus;
import com.airportmedia.platform.common.enums.ScreenStatus;
import com.airportmedia.platform.domain.entity.Campaign;
import com.airportmedia.platform.domain.entity.Creative;
import com.airportmedia.platform.domain.entity.Incident;
import com.airportmedia.platform.domain.entity.Screen;
import com.airportmedia.platform.domain.model.input.IncidentUpdate;
import com.airportmedia.platform.repository.IncidentRepository;
import com.airportmedia.platform.service.SimulationClock;
import com.airportmedia.platform.service.SimulationService;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestTransaction
class IncidentMonitorTest {

    private static final Instant NOW = Instant.parse("2026-10-23T14:30:00Z");

    @Inject
    IncidentMonitor monitor;

    @Inject
    SimulationClock clock;

    @Inject
    SimulationService simulation;

    @Inject
    MasterDataService masterData;

    @Inject
    IncidentRepository incidents;

    @Inject
    AdminOverviewService overview;

    @Inject
    EntityManager em;

    private Optional<Incident> active(String key) {
        return incidents.listActive().stream().filter(i -> i.subjectKey.equals(key)).findFirst();
    }

    @Test
    void aScreenOfflineForFiveMinutesIsRaisedAndAutoResolvesWhenItComesBack() {
        clock.jumpTo(NOW);
        Screen screen = em.createQuery("from Screen where code = 'SCR-GATE42'", Screen.class).getSingleResult();
        simulation.injectScreenOffline(screen.id);
        em.flush();

        monitor.scan();
        assertThat(active("screen:" + screen.id)).isEmpty();

        clock.jumpTo(NOW.plusSeconds(6 * 60));
        monitor.scan();
        assertThat(active("screen:" + screen.id)).hasValueSatisfying(i -> {
            assertThat(i.severity).isEqualTo(IncidentSeverity.HIGH);
            assertThat(i.status).isEqualTo(IncidentStatus.OPEN);
            assertThat(i.owner).isNotBlank();
        });

        Map<String, Object> row = new HashMap<>(masterData.list("screens").stream()
                .filter(r -> r.get("id").equals(screen.id)).findFirst().orElseThrow());
        row.put("status", ScreenStatus.ONLINE.name());
        masterData.update("screens", screen.id, row);
        em.flush();
        monitor.scan();

        assertThat(active("screen:" + screen.id)).isEmpty();
    }

    @Test
    void aLaggingAcrisFeedRaisesAFeedIncident() {
        // The latest ACRIS queue reading is 12:00, so at 14:30 the feed has been silent for 150 minutes.
        clock.jumpTo(NOW);

        monitor.scan();

        assertThat(active("feed:ACRIS")).hasValueSatisfying(i -> assertThat(i.detail).contains("12:00"));
    }

    @Test
    void aRunningCampaignsCreativeThatFailsQaRaisesAnIncident() {
        clock.jumpTo(NOW);
        Campaign running = em.createQuery("from Campaign where name like 'Azure Duty-Free%'", Campaign.class)
                .setMaxResults(1).getSingleResult();
        Creative broken = new Creative();
        broken.advertiser = running.advertiser;
        broken.campaign = running;
        broken.name = "Broken";
        broken.templateKey = "promo-basic";
        broken.language = "en";
        broken.headline = "x".repeat(90);
        em.persist(broken);
        em.flush();

        monitor.scan();

        assertThat(active("creative:" + broken.id)).hasValueSatisfying(i -> assertThat(i.detail).contains("too long"));
    }

    @Test
    void resolvingAStillFailingIncidentDoesNotRaiseItAgainUntilItRecoversAndFailsAgain() {
        clock.jumpTo(NOW);
        monitor.scan();
        Incident acris = active("feed:ACRIS").orElseThrow();

        overview.updateIncident(acris.id, new IncidentUpdate(IncidentStatus.RESOLVED, null));
        em.flush();
        monitor.scan();

        assertThat(active("feed:ACRIS")).isEmpty();
        assertThat(incidents.count("subjectKey", "feed:ACRIS")).isEqualTo(1);
    }
}
