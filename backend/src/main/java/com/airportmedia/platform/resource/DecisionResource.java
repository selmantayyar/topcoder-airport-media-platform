package com.airportmedia.platform.resource;

import com.airportmedia.platform.domain.entity.DecisionLog;
import com.airportmedia.platform.domain.model.output.DecisionLogView;
import com.airportmedia.platform.domain.model.output.DecisionResult;
import com.airportmedia.platform.repository.DecisionLogRepository;
import com.airportmedia.platform.service.decision.DecisionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

/** Query the decision evidence trail, and compute or run a screen's current decision. */
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
public class DecisionResource {

    private final DecisionLogRepository decisionLogs;
    private final DecisionService decisions;
    private final ObjectMapper mapper;

    public DecisionResource(DecisionLogRepository decisionLogs, DecisionService decisions, ObjectMapper mapper) {
        this.decisionLogs = decisionLogs;
        this.decisions = decisions;
        this.mapper = mapper;
    }

    @GET
    @Path("/decisions")
    @Transactional
    public List<DecisionLogView> decisions(@QueryParam("screenId") Long screenId,
            @QueryParam("limit") Integer limit) {
        int cap = limit != null ? Math.min(limit, 500) : 50;
        List<DecisionLog> logs = screenId != null
                ? decisionLogs.recentForScreen(screenId, cap)
                : decisionLogs.recent(cap);
        return logs.stream().map(this::toView).toList();
    }

    @GET
    @Path("/decisions/{id}")
    @Transactional
    public DecisionLogView decision(@PathParam("id") long id) {
        DecisionLog log = decisionLogs.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("No decision " + id));
        return toView(log);
    }

    @GET
    @Path("/screens/{id}/decision")
    public DecisionResult currentDecision(@PathParam("id") long screenId) {
        return decisions.preview(screenId);
    }

    @POST
    @Path("/screens/{id}/decision/run")
    public DecisionResult runDecision(@PathParam("id") long screenId) {
        return decisions.decideAndExecute(screenId);
    }

    private DecisionLogView toView(DecisionLog log) {
        return new DecisionLogView(
                log.id,
                log.screen.id,
                log.screen.code,
                log.zone != null ? log.zone.id : null,
                log.decidedAt,
                log.headcount,
                log.winnerCampaign != null ? log.winnerCampaign.id : null,
                log.winnerName,
                log.reason,
                log.fallback,
                parse(log.inputsJson),
                parse(log.profileJson),
                parse(log.candidatesJson));
    }

    private JsonNode parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }
}
