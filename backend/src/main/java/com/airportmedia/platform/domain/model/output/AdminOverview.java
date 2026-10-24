package com.airportmedia.platform.domain.model.output;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Everything the Platform Admin control room shows, in one live payload: KPIs, the terminal map, incidents. */
public record AdminOverview(
        Kpis kpis, List<TerminalMap> terminals, List<IncidentView> incidents, List<String> incidentOwners) {

    /**
     * @param adPlaysToday             ads played since the start of the simulated day
     * @param audienceImpressionsToday people who saw them: Σ crowd headcount of each executed decision
     * @param revenueToday             ad plays × impression price, converted into {@code currency}
     */
    public record Kpis(
            double screensOnlinePct,
            int screensTotal,
            long adPlaysToday,
            long audienceImpressionsToday,
            BigDecimal revenueToday,
            String currency,
            long activeCampaigns,
            double averageLatencyMs,
            long openIncidents,
            long delayedDepartures) {}

    public record TerminalMap(long id, String code, String name, List<ZoneMap> zones) {}

    /** A zone on the floor plan; {@code heat} is its live headcount relative to the busiest zone (0..1). */
    public record ZoneMap(long id, String name, String kind, int headcount, double heat, List<ScreenDot> screens) {}

    public record ScreenDot(long id, String code, String status, int latencyMs, Instant statusSince) {}

    public record IncidentView(
            long id,
            String type,
            String severity,
            String owner,
            String status,
            String title,
            String detail,
            Instant raisedAt,
            Instant updatedAt,
            Instant resolvedAt) {}
}
