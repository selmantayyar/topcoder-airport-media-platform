package com.airportmedia.platform.resource;

import com.airportmedia.platform.domain.entity.Flight;
import com.airportmedia.platform.domain.model.output.CrowdProfile;
import com.airportmedia.platform.domain.model.output.ZonePresence;
import com.airportmedia.platform.repository.FlightRepository;
import com.airportmedia.platform.service.CrowdProfiler;
import com.airportmedia.platform.service.ZonePresenceService;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.time.Instant;
import java.util.List;

/**
 * Read view over live ZonePresence and Crowd Profile. With no params it reports the current
 * simulation instant, so repeated calls while the clock runs show the numbers moving; an
 * explicit {@code at} jumps the estimate to any instant.
 */
@Path("/api/presence")
@Produces(MediaType.APPLICATION_JSON)
public class PresenceResource {

    private final ZonePresenceService presence;
    private final CrowdProfiler profiler;
    private final FlightRepository flights;

    public PresenceResource(ZonePresenceService presence, CrowdProfiler profiler, FlightRepository flights) {
        this.presence = presence;
        this.profiler = profiler;
        this.flights = flights;
    }

    @GET
    public List<ZonePresence> zones(@QueryParam("at") String at) {
        return at == null || at.isBlank()
                ? presence.snapshot()
                : presence.snapshotAt(Instant.parse(at));
    }

    @GET
    @Path("/flights/{flightId}/profile")
    public CrowdProfile flightProfile(@PathParam("flightId") Long flightId) {
        Flight flight = flights.findByIdOptional(flightId)
                .orElseThrow(() -> new NotFoundException("No flight " + flightId));
        return profiler.profile(flight);
    }
}
