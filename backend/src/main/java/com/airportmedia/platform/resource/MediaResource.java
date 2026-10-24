package com.airportmedia.platform.resource;

import com.airportmedia.platform.domain.model.output.AdvertiserOverviewRow;
import com.airportmedia.platform.domain.model.output.ApprovalItem;
import com.airportmedia.platform.domain.model.output.InventoryView;
import com.airportmedia.platform.service.media.MediaManagerService;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

/** The Media Manager: inventory calendar + yield, the approval queue, and the advertiser overview. */
@Path("/api/media")
@Produces(MediaType.APPLICATION_JSON)
public class MediaResource {

    private final MediaManagerService media;

    public MediaResource(MediaManagerService media) {
        this.media = media;
    }

    @GET
    @Path("/inventory")
    public InventoryView inventory() {
        return media.inventory();
    }

    @GET
    @Path("/approvals")
    public List<ApprovalItem> approvals() {
        return media.approvalQueue();
    }

    @POST
    @Path("/approvals/{id}/approve")
    public ApprovalItem approve(@PathParam("id") long id) {
        return media.approve(id);
    }

    @POST
    @Path("/approvals/{id}/reject")
    public ApprovalItem reject(@PathParam("id") long id) {
        return media.reject(id);
    }

    @GET
    @Path("/advertisers")
    public List<AdvertiserOverviewRow> advertisers() {
        return media.advertiserOverview();
    }
}
