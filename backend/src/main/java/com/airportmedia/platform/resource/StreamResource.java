package com.airportmedia.platform.resource;

import com.airportmedia.platform.domain.model.output.ServerEvent;
import com.airportmedia.platform.service.EventStream;
import com.airportmedia.platform.service.SimulationClock;
import io.smallrye.mutiny.Multi;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.time.Duration;
import org.jboss.resteasy.reactive.RestStreamElementType;

/**
 * The single live SSE channel. Each client receives periodic clock ticks (so every
 * view shares the simulation instant) merged with the broadcast of injected events.
 */
@Path("/api/stream")
public class StreamResource {

    private final SimulationClock clock;
    private final EventStream events;

    public StreamResource(SimulationClock clock, EventStream events) {
        this.clock = clock;
        this.events = events;
    }

    @GET
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    public Multi<ServerEvent> stream() {
        Multi<ServerEvent> clockTicks = Multi.createFrom()
                .ticks().every(Duration.ofMillis(500))
                .map(tick -> ServerEvent.of("clock", clock.snapshot()));
        return Multi.createBy().merging().streams(clockTicks, events.multi());
    }
}
