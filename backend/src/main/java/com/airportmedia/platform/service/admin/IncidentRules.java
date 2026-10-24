package com.airportmedia.platform.service.admin;

import com.airportmedia.platform.common.enums.IncidentSeverity;
import com.airportmedia.platform.common.enums.IncidentType;
import com.airportmedia.platform.common.enums.ScreenStatus;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Which incidents <em>should</em> be open right now, given the facts. Pure, so every auto-raised incident
 * is reproducible from its inputs:
 * <ul>
 *   <li><b>Screen offline</b> for more than {@value #OFFLINE_GRACE_MINUTES} min → HIGH; more than
 *       {@value #OFFLINE_CRITICAL_MINUTES} min → CRITICAL. Owner: screen operations.</li>
 *   <li><b>Feed lag</b>: a feed silent longer than its threshold → MEDIUM; more than 3× the threshold, or
 *       never heard from → HIGH. Owner: data feeds.</li>
 *   <li><b>Creative failed</b>: a running campaign's creative failing {@link CreativeQa} → MEDIUM. Owner:
 *       creative QA.</li>
 * </ul>
 */
public final class IncidentRules {

    static final long OFFLINE_GRACE_MINUTES = 5;
    static final long OFFLINE_CRITICAL_MINUTES = 30;
    static final String SCREEN_OWNER = "Screen operations";
    static final String FEED_OWNER = "Data feeds";
    static final String CREATIVE_OWNER = "Creative QA";
    /** Teams an incident can be assigned to: the auto-assigned owners plus the duty manager. */
    public static final List<String> OWNERS = List.of(SCREEN_OWNER, FEED_OWNER, CREATIVE_OWNER, "Duty manager");
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneOffset.UTC);

    public record ScreenFact(long screenId, String code, ScreenStatus status, Instant statusSince) {}

    public record FeedFact(String source, Instant lastSeen) {}

    public record CreativeFact(long creativeId, String campaignName, List<String> problems) {}

    public record DesiredIncident(
            String subjectKey, IncidentType type, IncidentSeverity severity, String owner, String title, String detail) {}

    private IncidentRules() {}

    public static List<DesiredIncident> evaluate(
            Instant now, List<ScreenFact> screens, List<FeedFact> feeds, List<CreativeFact> creatives,
            long feedLagMinutes) {
        List<DesiredIncident> out = new ArrayList<>();

        for (ScreenFact s : screens) {
            if (s.status() != ScreenStatus.OFFLINE || s.statusSince() == null) {
                continue;
            }
            long offline = minutesBetween(s.statusSince(), now);
            if (offline > OFFLINE_GRACE_MINUTES) {
                out.add(new DesiredIncident("screen:" + s.screenId(), IncidentType.SCREEN_OFFLINE,
                        offline > OFFLINE_CRITICAL_MINUTES ? IncidentSeverity.CRITICAL : IncidentSeverity.HIGH,
                        SCREEN_OWNER, s.code() + " offline", "Offline since " + clockTime(s.statusSince())));
            }
        }

        for (FeedFact f : feeds) {
            String key = "feed:" + f.source();
            String title = f.source() + " feed lagging";
            if (f.lastSeen() == null) {
                out.add(new DesiredIncident(key, IncidentType.FEED_LAG, IncidentSeverity.HIGH, FEED_OWNER,
                        title, "No data received yet"));
                continue;
            }
            long silent = minutesBetween(f.lastSeen(), now);
            if (silent > feedLagMinutes) {
                out.add(new DesiredIncident(key, IncidentType.FEED_LAG,
                        silent > 3 * feedLagMinutes ? IncidentSeverity.HIGH : IncidentSeverity.MEDIUM,
                        FEED_OWNER, title, "Last data at " + clockTime(f.lastSeen())
                                + " (threshold " + feedLagMinutes + " min)"));
            }
        }

        for (CreativeFact c : creatives) {
            if (!c.problems().isEmpty()) {
                out.add(new DesiredIncident("creative:" + c.creativeId(), IncidentType.CREATIVE_FAILED,
                        IncidentSeverity.MEDIUM, CREATIVE_OWNER, "Creative failed: " + c.campaignName(),
                        String.join("; ", c.problems())));
            }
        }
        return out;
    }

    /**
     * Details state <em>when</em> things happened rather than for how long, so they never go stale between
     * severity changes; the console shows the running age.
     */
    private static String clockTime(Instant at) {
        return CLOCK.format(at) + " UTC";
    }

    private static long minutesBetween(Instant from, Instant to) {
        return Duration.between(from, to).toMinutes();
    }
}
