package com.airportmedia.platform.service.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.airportmedia.platform.common.enums.CampaignStatus;
import com.airportmedia.platform.domain.entity.Campaign;
import com.airportmedia.platform.domain.entity.Screen;
import com.airportmedia.platform.domain.model.output.AdvertiserOverviewRow;
import com.airportmedia.platform.domain.model.output.InventoryView.Slot;
import com.airportmedia.platform.service.SimulationClock;
import com.airportmedia.platform.service.decision.DecisionService;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.WebApplicationException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestTransaction
class MediaManagerServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-23T14:30:00Z");

    @Inject
    MediaManagerService media;

    @Inject
    DecisionService decisions;

    @Inject
    SimulationClock clock;

    @Inject
    EntityManager em;

    @Test
    void inventoryCoversEveryScreenAndHourWithBookingPremiumAndPrice() {
        clock.jumpTo(NOW);

        var inventory = media.inventory();

        assertThat(inventory.hours()).hasSize(24).startsWith(0).endsWith(23);
        assertThat(inventory.screens()).hasSize(21).allSatisfy(r -> assertThat(r.slots()).hasSize(24));
        List<Slot> slots = inventory.screens().stream().flatMap(r -> r.slots().stream()).toList();
        assertThat(slots).anyMatch(Slot::booked).anyMatch(s -> !s.booked()).anyMatch(Slot::premium);
        assertThat(slots).allSatisfy(s -> assertThat(s.price()).isGreaterThan(BigDecimal.ZERO));
        // The gate-42 screen is covered by the worked-example campaigns, so its afternoon is sold.
        assertThat(inventory.screens().stream().filter(r -> r.code().equals("SCR-GATE42")).findFirst().orElseThrow()
                .slots().stream().filter(s -> s.hour() == 14).findFirst().orElseThrow().booked()).isTrue();
        assertThat(inventory.occupancyPct()).isBetween(0.1, 100.0);
        assertThat(inventory.forecast()).hasSize(7);
        assertThat(inventory.forecastTotal()).isPositive();
    }

    @Test
    void approvingAPendingCampaignLetsThePipelineConsiderItAndRejectingKeepsItOut() {
        clock.jumpTo(NOW);
        var queue = media.approvalQueue();
        assertThat(queue).isNotEmpty();
        long pendingId = queue.get(0).campaignId();

        var approved = media.approve(pendingId);

        assertThat(em.find(Campaign.class, approved.campaignId()).status).isEqualTo(CampaignStatus.RUNNING);
        assertThat(media.approvalQueue()).noneMatch(i -> i.campaignId() == pendingId);
        assertThatThrownBy(() -> media.approve(pendingId)).isInstanceOf(WebApplicationException.class);
    }

    @Test
    void aRejectedCampaignIsExcludedByThePipelinesHardFilter() {
        clock.jumpTo(NOW);
        Screen gate42 = em.createQuery("from Screen where code = 'SCR-GATE42'", Screen.class).getSingleResult();
        Campaign dutyFree = em.createQuery("from Campaign where name like 'Azure Duty-Free%'", Campaign.class)
                .setMaxResults(1).getSingleResult();
        dutyFree.status = CampaignStatus.PENDING_APPROVAL;
        em.flush();

        media.reject(dutyFree.id);

        assertThat(decisions.preview(gate42.id).candidates())
                .anyMatch(c -> c.campaignId() == dutyFree.id && !c.eligible() && c.dropReason().contains("REJECTED"));
    }

    @Test
    void advertisersAreRankedBySpendWithRunningCountsAndUnderDeliveryFlags() {
        clock.jumpTo(NOW);

        List<AdvertiserOverviewRow> rows = media.advertiserOverview();

        assertThat(rows).isSortedAccordingTo(Comparator.comparing(AdvertiserOverviewRow::spend).reversed());
        assertThat(rows).anyMatch(r -> r.runningCampaigns() > 0);
        assertThat(rows).flatExtracting(AdvertiserOverviewRow::underDelivering)
                .allSatisfy(u -> assertThat(((AdvertiserOverviewRow.UnderDelivery) u).pacePct()).isLessThan(50));
    }
}
