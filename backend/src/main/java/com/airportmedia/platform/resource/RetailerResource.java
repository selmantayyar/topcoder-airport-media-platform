package com.airportmedia.platform.resource;

import com.airportmedia.platform.domain.model.output.DashboardMetrics;
import com.airportmedia.platform.domain.model.output.OutletSummary;
import com.airportmedia.platform.domain.model.output.PassengerFlow;
import com.airportmedia.platform.domain.model.output.RetailerSummary;
import com.airportmedia.platform.service.RetailerDashboardService;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

/** The retailer live dashboard: company picker, performance metrics, and forward footfall. */
@Path("/api/retailers")
@Produces(MediaType.APPLICATION_JSON)
public class RetailerResource {

    private final RetailerDashboardService dashboard;

    public RetailerResource(RetailerDashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GET
    public List<RetailerSummary> retailers() {
        return dashboard.listRetailers();
    }

    @GET
    @Path("/{id}/metrics")
    public DashboardMetrics metrics(@PathParam("id") long id) {
        return dashboard.metrics(id);
    }

    @GET
    @Path("/{id}/outlets")
    public List<OutletSummary> outlets(@PathParam("id") long id) {
        return dashboard.listOutlets(id);
    }

    @GET
    @Path("/{id}/flow")
    public PassengerFlow flow(@PathParam("id") long id, @QueryParam("outletId") Long outletId) {
        return dashboard.passengerFlow(id, outletId);
    }
}
