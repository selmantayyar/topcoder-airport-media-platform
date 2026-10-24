package com.airportmedia.platform.service.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.airportmedia.platform.common.enums.IncidentSeverity;
import com.airportmedia.platform.common.enums.IncidentType;
import com.airportmedia.platform.common.enums.ScreenStatus;
import com.airportmedia.platform.service.admin.IncidentRules.CreativeFact;
import com.airportmedia.platform.service.admin.IncidentRules.DesiredIncident;
import com.airportmedia.platform.service.admin.IncidentRules.FeedFact;
import com.airportmedia.platform.service.admin.IncidentRules.ScreenFact;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class IncidentRulesTest {

    private static final Instant NOW = Instant.parse("2026-10-23T14:30:00Z");
    private static final long LAG = 60;

    private static Instant minutesAgo(long m) {
        return NOW.minusSeconds(m * 60);
    }

    private static List<DesiredIncident> screens(ScreenFact... facts) {
        return IncidentRules.evaluate(NOW, List.of(facts), List.of(), List.of(), LAG);
    }

    @Test
    void aScreenOfflineForMoreThanFiveMinutesRaisesAHighIncident() {
        assertThat(screens(new ScreenFact(1, "SCR-001", ScreenStatus.OFFLINE, minutesAgo(4)))).isEmpty();

        List<DesiredIncident> raised = screens(new ScreenFact(1, "SCR-001", ScreenStatus.OFFLINE, minutesAgo(6)));

        assertThat(raised).singleElement().satisfies(i -> {
            assertThat(i.subjectKey()).isEqualTo("screen:1");
            assertThat(i.type()).isEqualTo(IncidentType.SCREEN_OFFLINE);
            assertThat(i.severity()).isEqualTo(IncidentSeverity.HIGH);
            assertThat(i.owner()).isNotBlank();
            assertThat(i.title()).contains("SCR-001");
            assertThat(i.detail()).isEqualTo("Offline since 14:24 UTC");
        });
    }

    @Test
    void aLongOutageEscalatesToCritical() {
        assertThat(screens(new ScreenFact(1, "SCR-001", ScreenStatus.OFFLINE, minutesAgo(31))))
                .singleElement().extracting(DesiredIncident::severity).isEqualTo(IncidentSeverity.CRITICAL);
    }

    @Test
    void onlineAndDegradedScreensRaiseNothing() {
        assertThat(screens(
                new ScreenFact(1, "SCR-001", ScreenStatus.ONLINE, minutesAgo(90)),
                new ScreenFact(2, "SCR-002", ScreenStatus.DEGRADED, minutesAgo(90)))).isEmpty();
    }

    @Test
    void aFeedSilentPastItsThresholdRaisesALagIncidentThatEscalates() {
        List<FeedFact> fresh = List.of(new FeedFact("ACRIS", minutesAgo(30)));
        List<FeedFact> lagging = List.of(new FeedFact("ACRIS", minutesAgo(90)));
        List<FeedFact> stalled = List.of(new FeedFact("ACRIS", minutesAgo(200)));

        assertThat(IncidentRules.evaluate(NOW, List.of(), fresh, List.of(), LAG)).isEmpty();
        assertThat(IncidentRules.evaluate(NOW, List.of(), lagging, List.of(), LAG)).singleElement().satisfies(i -> {
            assertThat(i.subjectKey()).isEqualTo("feed:ACRIS");
            assertThat(i.type()).isEqualTo(IncidentType.FEED_LAG);
            assertThat(i.severity()).isEqualTo(IncidentSeverity.MEDIUM);
        });
        assertThat(IncidentRules.evaluate(NOW, List.of(), stalled, List.of(), LAG))
                .singleElement().extracting(DesiredIncident::severity).isEqualTo(IncidentSeverity.HIGH);
    }

    @Test
    void aFeedThatNeverDeliveredIsHigh() {
        assertThat(IncidentRules.evaluate(NOW, List.of(), List.of(new FeedFact("AIDX", null)), List.of(), LAG))
                .singleElement().extracting(DesiredIncident::severity).isEqualTo(IncidentSeverity.HIGH);
    }

    @Test
    void aCreativeFailingQaRaisesAnIncidentWithItsProblems() {
        List<DesiredIncident> raised = IncidentRules.evaluate(NOW, List.of(), List.of(),
                List.of(new CreativeFact(5, "Spring sale", List.of("missing headline")),
                        new CreativeFact(6, "Fine", List.of())), LAG);

        assertThat(raised).singleElement().satisfies(i -> {
            assertThat(i.subjectKey()).isEqualTo("creative:5");
            assertThat(i.type()).isEqualTo(IncidentType.CREATIVE_FAILED);
            assertThat(i.detail()).contains("missing headline");
        });
    }
}
