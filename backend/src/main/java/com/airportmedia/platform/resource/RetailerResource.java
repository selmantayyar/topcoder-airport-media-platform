package com.airportmedia.platform.resource;

import com.airportmedia.platform.domain.model.input.CreativeBrief;
import com.airportmedia.platform.domain.model.output.CampaignHistoryRow;
import com.airportmedia.platform.domain.model.output.CreativeSummary;
import com.airportmedia.platform.domain.model.output.DashboardMetrics;
import com.airportmedia.platform.domain.model.output.GeneratedCreative;
import com.airportmedia.platform.domain.model.output.OutletSummary;
import com.airportmedia.platform.domain.model.output.PassengerFlow;
import com.airportmedia.platform.domain.model.output.RetailerSummary;
import com.airportmedia.platform.service.CampaignService;
import com.airportmedia.platform.service.RetailerDashboardService;
import com.airportmedia.platform.service.assistant.CreativeAssistantService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

/**
 * The retailer workspace: company picker, live metrics, forward footfall, campaign history, creative library
 * and its GenAI creative assistant.
 */
@Path("/api/retailers")
@Produces(MediaType.APPLICATION_JSON)
public class RetailerResource {

    private final RetailerDashboardService dashboard;
    private final CampaignService campaigns;
    private final CreativeAssistantService assistant;

    public RetailerResource(
            RetailerDashboardService dashboard, CampaignService campaigns, CreativeAssistantService assistant) {
        this.dashboard = dashboard;
        this.campaigns = campaigns;
        this.assistant = assistant;
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

    /** Brief in, ad page out: English and Spanish pages written by the creative assistant, saved to the library. */
    @POST
    @Path("/{id}/creatives/assistant")
    @Consumes(MediaType.APPLICATION_JSON)
    public GeneratedCreative generateCreative(@PathParam("id") long id, @Valid @NotNull CreativeBrief brief) {
        return assistant.generate(id, brief.brief());
    }
}
