package com.airportmedia.platform.service.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.airportmedia.platform.common.enums.IncidentSeverity;
import com.airportmedia.platform.common.enums.IncidentType;
import com.airportmedia.platform.service.admin.IncidentReconciler.ActiveIncident;
import com.airportmedia.platform.service.admin.IncidentReconciler.Plan;
import com.airportmedia.platform.service.admin.IncidentRules.DesiredIncident;
import java.util.List;
import org.junit.jupiter.api.Test;

class IncidentReconcilerTest {

    private static DesiredIncident desired(String key, IncidentSeverity severity) {
        return new DesiredIncident(key, IncidentType.SCREEN_OFFLINE, severity, "Screen operations", key, "");
    }

    @Test
    void raisesNewProblemsAndAutoResolvesRecoveredOnes() {
        Plan plan = IncidentReconciler.plan(
                List.of(desired("screen:1", IncidentSeverity.HIGH)),
                List.of(new ActiveIncident(9, "screen:2", IncidentSeverity.HIGH, false)));

        assertThat(plan.raise()).extracting(DesiredIncident::subjectKey).containsExactly("screen:1");
        assertThat(plan.resolve()).containsExactly(9L);
        assertThat(plan.update()).isEmpty();
    }

    @Test
    void leavesAnUnchangedProblemAlone() {
        Plan plan = IncidentReconciler.plan(
                List.of(desired("screen:1", IncidentSeverity.HIGH)),
                List.of(new ActiveIncident(9, "screen:1", IncidentSeverity.HIGH, false)));

        assertThat(plan.raise()).isEmpty();
        assertThat(plan.resolve()).isEmpty();
        assertThat(plan.update()).isEmpty();
    }

    @Test
    void updatesAnActiveIncidentWhoseSeverityChanged() {
        Plan plan = IncidentReconciler.plan(
                List.of(desired("screen:1", IncidentSeverity.CRITICAL)),
                List.of(new ActiveIncident(9, "screen:1", IncidentSeverity.HIGH, false)));

        assertThat(plan.update()).singleElement().satisfies(e -> {
            assertThat(e.incidentId()).isEqualTo(9L);
            assertThat(e.desired().severity()).isEqualTo(IncidentSeverity.CRITICAL);
        });
    }

    @Test
    void anOperatorResolvedIncidentIsHeldWhileItsConditionPersists() {
        // Resolved by an operator but the screen is still offline: no new incident, no change.
        Plan plan = IncidentReconciler.plan(
                List.of(desired("screen:1", IncidentSeverity.CRITICAL)),
                List.of(new ActiveIncident(9, "screen:1", IncidentSeverity.HIGH, true)));

        assertThat(plan.raise()).isEmpty();
        assertThat(plan.update()).isEmpty();
        assertThat(plan.resolve()).isEmpty();
        assertThat(plan.release()).isEmpty();
    }

    @Test
    void aHeldIncidentIsReleasedOnceItsConditionClears() {
        Plan plan = IncidentReconciler.plan(
                List.of(),
                List.of(new ActiveIncident(9, "screen:1", IncidentSeverity.HIGH, true)));

        assertThat(plan.release()).containsExactly(9L);
        assertThat(plan.resolve()).isEmpty();
    }
}
