package com.airportmedia.platform.resource;

import com.airportmedia.platform.domain.model.output.CampaignHistoryRow;
import com.airportmedia.platform.domain.model.output.CreativeSummary;
import com.airportmedia.platform.domain.model.output.DashboardMetrics;
import com.airportmedia.platform.domain.model.output.OutletSummary;
import com.airportmedia.platform.domain.model.output.PassengerFlow;
import com.airportmedia.platform.domain.model.output.RetailerSummary;
import com.airportmedia.platform.service.CampaignService;
import com.airportmedia.platform.service.RetailerDashboardService;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

/** The retailer workspace: company picker, live metrics, forward footfall, campaign history, creative library. */
@Path("/api/retailers")
@Produces(MediaType.APPLICATION_JSON)
public class RetailerResource {

    private final RetailerDashboardService dashboard;
    private final CampaignService campaigns;

    public RetailerResource(RetailerDashboardService dashboard, CampaignService campaigns) {
        this.dashboard = dashboard;
        this.campaigns = campaigns;
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

    @GET
    @Path("/{id}/campaigns")
    public List<CampaignHistoryRow> campaigns(@PathParam("id") long id) {
        return campaigns.history(id);
    }

    @GET
    @Path("/{id}/creatives")
    public List<CreativeSummary> creatives(@PathParam("id") long id) {
        return campaigns.library(id);
    }
}
