package com.airportmedia.platform.resource;

import com.airportmedia.platform.domain.model.output.ClockSnapshot;
import com.airportmedia.platform.domain.model.output.ServerEvent;
import com.airportmedia.platform.service.SimulationClock;
import com.airportmedia.platform.service.SimulationService;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.time.Instant;
import java.util.Set;

/** Control bar backend: play/pause/speed/jump the clock and inject synthetic events. */
@Path("/api/sim")
@Produces(MediaType.APPLICATION_JSON)
public class SimulationControlResource {

    private static final Set<Integer> ALLOWED_SPEEDS = Set.of(1, 10, 60);

    private final SimulationClock clock;
    private final SimulationService simulation;

    public SimulationControlResource(SimulationClock clock, SimulationService simulation) {
        this.clock = clock;
        this.simulation = simulation;
    }

    @POST
    @Path("/play")
    public ClockSnapshot play() {
        clock.play();
        return clock.snapshot();
    }

    @POST
    @Path("/pause")
    public ClockSnapshot pause() {
        clock.pause();
        return clock.snapshot();
    }

    @POST
    @Path("/speed/{value}")
    public ClockSnapshot speed(@PathParam("value") int value) {
        if (!ALLOWED_SPEEDS.contains(value)) {
            throw new BadRequestException("Speed must be one of " + ALLOWED_SPEEDS);
        }
        clock.setSpeed(value);
        return clock.snapshot();
    }

    @POST
    @Path("/jump")
    public ClockSnapshot jump(@QueryParam("time") String time) {
        if (time == null || time.isBlank()) {
            throw new BadRequestException("Provide an ISO-8601 instant in the 'time' query param");
        }
        clock.jumpTo(Instant.parse(time));
        return clock.snapshot();
    }

    @POST
    @Path("/inject/delay")
    public ServerEvent injectDelay(
            @QueryParam("flightId") Long flightId,
            @QueryParam("minutes") Integer minutes,
            @QueryParam("screenId") Long screenId) {
        return simulation.injectDelay(flightId, minutes, screenId);
    }

    @POST
    @Path("/inject/gate-change")
    public ServerEvent injectGateChange(
            @QueryParam("flightId") Long flightId,
            @QueryParam("gate") String gate,
            @QueryParam("screenId") Long screenId) {
        return simulation.injectGateChange(flightId, gate, screenId);
    }

    @POST
    @Path("/inject/screen-offline")
    public ServerEvent injectScreenOffline(@QueryParam("screenId") Long screenId) {
        return simulation.injectScreenOffline(screenId);
    }

    @POST
    @Path("/inject/screen-online")
    public ServerEvent injectScreenOnline(@QueryParam("screenId") Long screenId) {
        return simulation.injectScreenOnline(screenId);
    }
}
