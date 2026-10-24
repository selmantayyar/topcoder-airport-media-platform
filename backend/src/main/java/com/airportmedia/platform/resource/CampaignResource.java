package com.airportmedia.platform.resource;

import com.airportmedia.platform.domain.model.input.CampaignDraft;
import com.airportmedia.platform.domain.model.input.PromoRequest;
import com.airportmedia.platform.domain.model.output.AudienceMatch;
import com.airportmedia.platform.domain.model.output.BuilderOptions;
import com.airportmedia.platform.domain.model.output.CampaignHistoryRow;
import com.airportmedia.platform.domain.model.output.PromoIssued;
import com.airportmedia.platform.domain.model.output.SlotRecommendations;
import com.airportmedia.platform.service.CampaignService;
import com.airportmedia.platform.service.decision.SlotRecommendationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * The retailer campaign builder: options, live audience-match preview, save, duplicate-as-draft, re-run, promo
 * codes, and the smart "best slots tomorrow" recommendation.
 */
@Path("/api/campaigns")
@Produces(MediaType.APPLICATION_JSON)
public class CampaignResource {

    private final CampaignService campaigns;
    private final SlotRecommendationService slots;

    public CampaignResource(CampaignService campaigns, SlotRecommendationService slots) {
        this.campaigns = campaigns;
        this.slots = slots;
    }

    @GET
    @Path("/options")
    public BuilderOptions options() {
        return campaigns.options();
    }

    @POST
    @Path("/preview")
    @Consumes(MediaType.APPLICATION_JSON)
    public AudienceMatch preview(@Valid @NotNull CampaignDraft draft) {
        return campaigns.preview(draft);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public CampaignHistoryRow create(@Valid @NotNull CampaignDraft draft) {
        return campaigns.create(draft);
    }

    /** A past campaign as a pre-filled builder draft (one-click duplicate). */
    @GET
    @Path("/{id}/draft")
    public CampaignDraft draft(@PathParam("id") long id) {
        return campaigns.draftFrom(id);
    }

    @POST
    @Path("/{id}/run-again")
    public CampaignHistoryRow runAgain(@PathParam("id") long id) {
        return campaigns.runAgain(id);
    }

    @POST
    @Path("/{id}/promo-codes")
    public PromoIssued issuePromo(@PathParam("id") long id, @Valid PromoRequest request) {
        return campaigns.issuePromo(id, request != null ? request : new PromoRequest(null, null));
    }

    /** The best 3 zone-hours for this campaign tomorrow, with predicted impressions. */
    @GET
    @Path("/{id}/recommended-slots")
    public SlotRecommendations recommendedSlots(@PathParam("id") long id) {
        return slots.recommend(id);
    }
}
