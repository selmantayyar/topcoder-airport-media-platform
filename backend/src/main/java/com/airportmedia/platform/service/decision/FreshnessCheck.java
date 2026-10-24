package com.airportmedia.platform.service.decision;

import com.airportmedia.platform.domain.model.input.DecisionInputs;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;

/**
 * Guards the pipeline against acting on hallucinated or stale facts: if the cited flight/crowd
 * data is missing or older than {@link #MAX_STALENESS} of simulation time, the decision is not
 * trusted and the caller falls back to the rule-based default.
 */
@ApplicationScoped
public class FreshnessCheck {

    public static final Duration MAX_STALENESS = Duration.ofMinutes(10);

    /** @return why the inputs can't be trusted, or {@code null} if they're fresh. */
    public String untrustReason(DecisionInputs inputs) {
        if (inputs.profile() == null) {
            return "no crowd/flight evidence for this zone — cannot score on absent facts";
        }
        if (inputs.dataAsOf() == null) {
            return "no data as-of timestamp — cannot verify freshness";
        }
        Duration age = Duration.between(inputs.dataAsOf(), inputs.now());
        if (age.compareTo(MAX_STALENESS) > 0) {
            return "cited flight/crowd data is stale (" + age.toMinutes() + "m old > "
                    + MAX_STALENESS.toMinutes() + "m)";
        }
        return null;
    }
}
