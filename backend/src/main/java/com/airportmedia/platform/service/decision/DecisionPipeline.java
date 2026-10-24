package com.airportmedia.platform.service.decision;

import com.airportmedia.platform.domain.model.input.CampaignView;
import com.airportmedia.platform.domain.model.input.DecisionInputs;
import com.airportmedia.platform.domain.model.output.CandidateScore;
import com.airportmedia.platform.domain.model.output.DecisionResult;
import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The per-screen, per-slot decision pipeline: profile → filter → score → rules → choose & log.
 * Pure and deterministic — the same {@link DecisionInputs} always produce the same
 * {@link DecisionResult} — so every logged decision is reproducible and auditable. If the cited
 * inputs fail the freshness/trust check the dynamic score is discarded and the winner is taken
 * from the rule-based default (static filters + remaining budget) instead of executing.
 */
@ApplicationScoped
public class DecisionPipeline {

    private final HardFilters hardFilters;
    private final DecisionScorer scorer;
    private final BusinessRules businessRules;
    private final FreshnessCheck freshnessCheck;

    public DecisionPipeline(
            HardFilters hardFilters,
            DecisionScorer scorer,
            BusinessRules businessRules,
            FreshnessCheck freshnessCheck) {
        this.hardFilters = hardFilters;
        this.scorer = scorer;
        this.businessRules = businessRules;
        this.freshnessCheck = freshnessCheck;
    }

    public DecisionResult decide(DecisionInputs inputs) {
        String untrustReason = freshnessCheck.untrustReason(inputs);

        List<CandidateScore> candidates = new ArrayList<>();
        for (CampaignView campaign : inputs.campaigns()) {
            String dropReason = hardFilters.firstFailure(campaign, inputs);
            if (dropReason != null) {
                candidates.add(ineligible(campaign, dropReason));
            } else {
                candidates.add(businessRules.apply(scorer.score(campaign, inputs), campaign, inputs));
            }
        }
        candidates.sort(Comparator.comparingDouble(
                (CandidateScore c) -> c.eligible() ? c.finalScore() : -1.0).reversed());

        if (untrustReason != null) {
            return fallback(inputs, candidates, untrustReason);
        }

        Optional<CandidateScore> winner = candidates.stream()
                .filter(CandidateScore::eligible)
                .filter(c -> c.finalScore() > 0)
                .max(Comparator.comparingDouble(CandidateScore::finalScore)
                        .thenComparing(c -> -c.campaignId()));

        if (winner.isEmpty()) {
            return new DecisionResult(inputs.screenId(), inputs.zoneId(), inputs.now(), inputs.headcount(),
                    inputs.profile(), candidates, null, null, "no eligible campaign for this screen", false);
        }

        CandidateScore w = winner.get();
        String reason = String.format("highest score: %s (%.2f)", w.campaignName(), w.finalScore());
        return new DecisionResult(inputs.screenId(), inputs.zoneId(), inputs.now(), inputs.headcount(),
                inputs.profile(), candidates, w.campaignId(), w.campaignName(), reason, false);
    }

    /** Stale/untrusted dynamic inputs: choose by static eligibility and remaining budget only. */
    private DecisionResult fallback(DecisionInputs inputs, List<CandidateScore> candidates, String untrustReason) {
        Optional<CampaignView> chosen = inputs.campaigns().stream()
                .filter(c -> hardFilters.firstStaticFailure(c, inputs) == null)
                .max(Comparator.comparingDouble(DecisionPipeline::remainingBudget)
                        .thenComparing(c -> -c.campaignId()));

        if (chosen.isEmpty()) {
            return new DecisionResult(inputs.screenId(), inputs.zoneId(), inputs.now(), inputs.headcount(),
                    inputs.profile(), candidates, null, null,
                    "stale inputs and no statically-eligible default: " + untrustReason, true);
        }

        CampaignView d = chosen.get();
        String reason = "stale inputs → rule-based default (" + d.campaignName() + "): " + untrustReason;
        return new DecisionResult(inputs.screenId(), inputs.zoneId(), inputs.now(), inputs.headcount(),
                inputs.profile(), candidates, d.campaignId(), d.campaignName(), reason, true);
    }

    private static double remainingBudget(CampaignView c) {
        BigDecimal budget = c.budget() != null ? c.budget() : BigDecimal.ZERO;
        BigDecimal spent = c.spentToDate() != null ? c.spentToDate() : BigDecimal.ZERO;
        return budget.subtract(spent).doubleValue();
    }

    private static CandidateScore ineligible(CampaignView campaign, String reason) {
        return new CandidateScore(
                campaign.campaignId(), campaign.campaignName(), campaign.advertiserName(), campaign.advertiserKind(),
                false, reason, 0, 0, 0, 0, 0, 0.0, List.of(), 0.0, 0.0);
    }
}
