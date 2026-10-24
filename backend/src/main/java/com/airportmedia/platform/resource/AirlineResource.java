package com.airportmedia.platform.resource;

import com.airportmedia.platform.domain.model.input.SponsoredContentRequest;
import com.airportmedia.platform.domain.model.output.AirlineContentRow;
import com.airportmedia.platform.domain.model.output.AirlineFlightRow;
import com.airportmedia.platform.domain.model.output.AirlineSummary;
import com.airportmedia.platform.service.airline.AirlineService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

/** The Airline Representative: my flights today, my gate content, and scheduling sponsored content. */
@Path("/api/airlines")
@Produces(MediaType.APPLICATION_JSON)
public class AirlineResource {

    private final AirlineService airlines;

    public AirlineResource(AirlineService airlines) {
        this.airlines = airlines;
    }

    @GET
    public List<AirlineSummary> airlines() {
        return airlines.listAirlines();
    }

    @GET
    @Path("/{id}/flights")
    public List<AirlineFlightRow> flights(@PathParam("id") long id) {
        return airlines.flights(id);
    }

    @GET
    @Path("/{id}/content")
    public List<AirlineContentRow> content(@PathParam("id") long id) {
        return airlines.content(id);
    }

    @POST
    @Path("/{id}/content")
    @Consumes(MediaType.APPLICATION_JSON)
    public AirlineContentRow schedule(@PathParam("id") long id, @Valid @NotNull SponsoredContentRequest request) {
        return airlines.schedule(id, request);
    }
}
