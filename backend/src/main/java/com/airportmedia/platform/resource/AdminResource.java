package com.airportmedia.platform.resource;

import com.airportmedia.platform.domain.model.input.IncidentUpdate;
import com.airportmedia.platform.domain.model.output.AdminOverview;
import com.airportmedia.platform.domain.model.output.AdminOverview.IncidentView;
import com.airportmedia.platform.domain.model.output.MasterDataSchema;
import com.airportmedia.platform.service.admin.AdminOverviewService;
import com.airportmedia.platform.service.admin.MasterDataService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

/** The Platform Admin control room: live overview, incident actions, and generic master-data CRUD. */
@Path("/api/admin")
@Produces(MediaType.APPLICATION_JSON)
public class AdminResource {

    private final AdminOverviewService overview;
    private final MasterDataService masterData;

    public AdminResource(AdminOverviewService overview, MasterDataService masterData) {
        this.overview = overview;
        this.masterData = masterData;
    }

    @GET
    @Path("/overview")
    public AdminOverview overview() {
        return overview.overview();
    }

    @PATCH
    @Path("/incidents/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public IncidentView updateIncident(@PathParam("id") long id, @Valid @NotNull IncidentUpdate update) {
        return overview.updateIncident(id, update);
    }

    @GET
    @Path("/master")
    public List<MasterDataSchema> schemas() {
        return masterData.schemas();
    }

    @GET
    @Path("/master/{type}")
    public List<Map<String, Object>> list(@PathParam("type") String type) {
        return masterData.list(type);
    }

    @POST
    @Path("/master/{type}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Map<String, Object> create(@PathParam("type") String type, @NotNull Map<String, Object> payload) {
        return masterData.create(type, payload);
    }

    @PUT
    @Path("/master/{type}/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Map<String, Object> update(
            @PathParam("type") String type, @PathParam("id") long id, @NotNull Map<String, Object> payload) {
        return masterData.update(type, id, payload);
    }
}
