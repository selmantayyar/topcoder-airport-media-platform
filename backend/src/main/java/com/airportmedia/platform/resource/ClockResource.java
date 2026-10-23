package com.airportmedia.platform.resource;

import com.airportmedia.platform.domain.model.output.ClockTick;
import com.airportmedia.platform.service.ClockService;
import io.smallrye.mutiny.Multi;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestStreamElementType;

@Path("/api/clock")
public class ClockResource {

    private final ClockService clockService;

    public ClockResource(ClockService clockService) {
        this.clockService = clockService;
    }

    @GET
    @Path("/stream")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    public Multi<ClockTick> stream() {
        return clockService.stream();
    }
}
