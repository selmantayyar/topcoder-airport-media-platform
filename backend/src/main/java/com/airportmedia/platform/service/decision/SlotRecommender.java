package com.airportmedia.platform.service.decision;

import com.airportmedia.platform.common.util.Numbers;
import com.airportmedia.platform.domain.model.output.SlotRecommendation;
import com.airportmedia.platform.domain.model.output.SlotSample;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a projected day of simulated pipeline decisions into the best slots for a campaign. A slot is one
 * zone for one clock hour; each sample already carries the plays the pipeline would grant it (frequency cap
 * applied, see {@link ScreenPlayLedger}):
 *
 * <pre>
 *   predictedImpressions = Σ plays over the slot's samples
 *   winningScreens       = screens with at least one play in the slot
 *   averageScore         = mean final score over the slot's eligible samples
 * </pre>
 *
 * Slots the campaign is never eligible in are not recommended. Ranking: predicted impressions, then
 * average score, then the earlier slot, then the lower zone id (fully deterministic).
 */
public final class SlotRecommender {

    private SlotRecommender() {}

    public static List<SlotRecommendation> recommend(List<SlotSample> samples, int limit) {
        Map<SlotKey, List<SlotSample>> bySlot = new LinkedHashMap<>();
        for (SlotSample s : samples) {
            bySlot.computeIfAbsent(new SlotKey(s.zoneId(), s.at().truncatedTo(ChronoUnit.HOURS)), k -> new ArrayList<>())
                    .add(s);
        }

        List<Candidate> candidates = new ArrayList<>();
        bySlot.forEach((key, slot) -> {
            List<SlotSample> eligible = slot.stream().filter(SlotSample::eligible).toList();
            if (eligible.isEmpty()) {
                return;
            }
            candidates.add(new Candidate(
                    key,
                    slot.get(0).zoneName(),
                    slot.stream().mapToLong(SlotSample::plays).sum(),
                    (int) slot.stream().filter(s -> s.plays() > 0).mapToLong(SlotSample::screenId).distinct().count(),
                    slot.stream().mapToInt(SlotSample::headcount).max().orElse(0),
                    Numbers.round2(eligible.stream().mapToDouble(SlotSample::finalScore).average().orElse(0))));
        });

        candidates.sort(Comparator.comparingLong(Candidate::impressions).reversed()
                .thenComparing(Comparator.comparingDouble(Candidate::averageScore).reversed())
                .thenComparing(c -> c.key().hour())
                .thenComparingLong(c -> c.key().zoneId()));

        List<SlotRecommendation> out = new ArrayList<>();
        for (Candidate c : candidates.subList(0, Math.min(limit, candidates.size()))) {
            out.add(new SlotRecommendation(out.size() + 1, c.key().zoneId(), c.zoneName(),
                    c.key().hour(), c.key().hour().plus(Duration.ofHours(1)),
                    c.impressions(), c.winningScreens(), c.peakHeadcount(), c.averageScore()));
        }
        return out;
    }

    private record SlotKey(long zoneId, Instant hour) {}

    private record Candidate(
            SlotKey key, String zoneName, long impressions, int winningScreens, int peakHeadcount, double averageScore) {}
}
