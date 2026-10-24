package com.airportmedia.platform.service.admin;

import com.airportmedia.platform.common.enums.IncidentSeverity;
import com.airportmedia.platform.service.admin.IncidentRules.DesiredIncident;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Diffs the incidents that should be open against those that are: raise what's new, auto-resolve what has
 * recovered, and update what changed severity. One incident per subject key. An incident an operator
 * resolved while its condition still held is <em>held</em>: it counts as present (so nothing re-raises
 * every scan) and is released once the condition clears, after which a fresh failure raises anew.
 */
public final class IncidentReconciler {

    /** An open or acknowledged incident, or one an operator resolved that is still {@code held}. */
    public record ActiveIncident(long id, String subjectKey, IncidentSeverity severity, boolean held) {}

    /** An active incident whose severity moved, up or down. */
    public record SeverityChange(long incidentId, DesiredIncident desired) {}

    /**
     * @param resolve  active incidents whose condition cleared — auto-resolve them
     * @param release  held incidents whose condition cleared — stop holding them
     */
    public record Plan(
            List<DesiredIncident> raise, List<Long> resolve, List<SeverityChange> update, List<Long> release) {}

    private IncidentReconciler() {}

    public static Plan plan(List<DesiredIncident> desired, List<ActiveIncident> active) {
        Map<String, ActiveIncident> activeByKey = active.stream()
                .collect(Collectors.toMap(ActiveIncident::subjectKey, Function.identity(), (a, b) -> a));
        Set<String> desiredKeys = desired.stream().map(DesiredIncident::subjectKey).collect(Collectors.toSet());

        List<DesiredIncident> raise = new ArrayList<>();
        List<SeverityChange> update = new ArrayList<>();
        for (DesiredIncident d : desired) {
            ActiveIncident current = activeByKey.get(d.subjectKey());
            if (current == null) {
                raise.add(d);
            } else if (!current.held() && current.severity() != d.severity()) {
                update.add(new SeverityChange(current.id(), d));
            }
        }
        List<ActiveIncident> cleared = active.stream().filter(a -> !desiredKeys.contains(a.subjectKey())).toList();
        List<Long> resolve = cleared.stream().filter(a -> !a.held()).map(ActiveIncident::id).toList();
        List<Long> release = cleared.stream().filter(ActiveIncident::held).map(ActiveIncident::id).toList();
        return new Plan(raise, resolve, update, release);
    }
}
