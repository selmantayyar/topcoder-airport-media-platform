package com.airportmedia.platform.service.decision;

import static org.assertj.core.api.Assertions.assertThat;

import com.airportmedia.platform.domain.model.output.SlotRecommendation;
import com.airportmedia.platform.domain.model.output.SlotSample;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SlotRecommenderTest {

    private static final Instant H9 = Instant.parse("2026-10-24T09:00:00Z");
    private static final Instant H14 = Instant.parse("2026-10-24T14:00:00Z");
    private static final Instant H18 = Instant.parse("2026-10-24T18:00:00Z");

    @Test
    void ranksZoneHoursByThePlaysThePipelineWouldGrant() {
        List<SlotSample> samples = new ArrayList<>();
        // Pier D 14:00 — two screens, 3 of 4 samples won, 2 plays each → 12 plays
        for (long screen : new long[] {1, 2}) {
            for (int m = 0; m < 60; m += 15) {
                samples.add(sample(10, "Pier D", screen, H14.plusSeconds(m * 60L), 380, true, m < 45 ? 2 : 0, 70.0));
            }
        }
        // Pier A 09:00 — one screen, every sample won, 2 plays each → 8 plays
        for (int m = 0; m < 60; m += 15) {
            samples.add(sample(20, "Pier A", 3, H9.plusSeconds(m * 60L), 200, true, 2, 60.0));
        }
        // Food court 18:00 — eligible but never wins
        samples.add(sample(30, "Food Court", 4, H18, 500, true, 0, 40.0));
        // Pier B 18:00 — filtered off entirely: never recommended
        samples.add(sample(40, "Pier B", 5, H18, 900, false, 0, 0.0));

        List<SlotRecommendation> slots = SlotRecommender.recommend(samples, 3);

        assertThat(slots).extracting(SlotRecommendation::zoneName).containsExactly("Pier D", "Pier A", "Food Court");
        SlotRecommendation best = slots.get(0);
        assertThat(best.rank()).isEqualTo(1);
        assertThat(best.start()).isEqualTo(H14);
        assertThat(best.end()).isEqualTo(H14.plusSeconds(3600));
        assertThat(best.predictedImpressions()).isEqualTo(12);
        assertThat(best.winningScreens()).isEqualTo(2);
        assertThat(best.peakHeadcount()).isEqualTo(380);
        assertThat(best.averageScore()).isEqualTo(70.0);
        assertThat(slots.get(1).predictedImpressions()).isEqualTo(8);
        assertThat(slots.get(2).predictedImpressions()).isZero();
    }

    @Test
    void equalImpressionsAreBrokenByScoreThenByTheEarlierSlot() {
        List<SlotSample> samples = List.of(
                sample(1, "Pier A", 1, H18, 100, true, 6, 50.0),
                sample(2, "Pier B", 2, H9, 100, true, 6, 50.0),
                sample(3, "Pier C", 3, H14, 100, true, 6, 65.0));

        List<SlotRecommendation> slots = SlotRecommender.recommend(samples, 3);

        assertThat(slots).extracting(SlotRecommendation::zoneName).containsExactly("Pier C", "Pier B", "Pier A");
        assertThat(slots).extracting(SlotRecommendation::rank).containsExactly(1, 2, 3);
    }

    @Test
    void averagesScoreOnlyOverEligibleSamplesAndCapsTheList() {
        List<SlotSample> samples = List.of(
                sample(1, "Pier A", 1, H9, 100, true, 6, 80.0),
                sample(1, "Pier A", 2, H9, 100, false, 0, 0.0),
                sample(2, "Pier B", 3, H14, 100, true, 6, 50.0));

        List<SlotRecommendation> slots = SlotRecommender.recommend(samples, 1);

        assertThat(slots).hasSize(1);
        assertThat(slots.get(0).averageScore()).isEqualTo(80.0);
    }

    private static SlotSample sample(
            long zoneId, String zone, long screenId, Instant at, int headcount, boolean eligible, long plays, double score) {
        return new SlotSample(zoneId, zone, screenId, at, headcount, eligible, plays, score);
    }
}
