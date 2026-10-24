package com.airportmedia.platform.resource;

import com.airportmedia.platform.domain.model.output.DecisionResult;
import com.airportmedia.platform.domain.model.output.PlaybackView;
import com.airportmedia.platform.domain.model.output.ScreenSummary;
import com.airportmedia.platform.service.PlaybackService;
import com.airportmedia.platform.service.decision.DecisionService;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

/**
 * Everything under {@code /api/screens}: the chooser, the passenger-screen playback read model, and a screen's
 * live decision. Kept in one class because JAX-RS routes on the most specific class path first — a second
 * resource declaring {@code /api/screens/...} sub-paths would be shadowed and 404.
 */
@Path("/api/screens")
@Produces(MediaType.APPLICATION_JSON)
public class ScreenResource {

    private final PlaybackService playback;
    private final DecisionService decisions;

    public ScreenResource(PlaybackService playback, DecisionService decisions) {
        this.playback = playback;
        this.decisions = decisions;
    }

    @GET
    public List<ScreenSummary> screens(@QueryParam("gate") String gate, @QueryParam("zone") String zone) {
        return playback.listScreens(gate, zone);
    }

    @GET
    @Path("/{id}/playback")
    public PlaybackView playback(@PathParam("id") long id, @QueryParam("lang") String lang) {
        return playback.playback(id, lang);
    }

    /** The screen's current decision, side-effect free (the "why this ad?" evidence). */
    @GET
    @Path("/{id}/decision")
    public DecisionResult currentDecision(@PathParam("id") long id) {
        return decisions.preview(id);
    }

    /** Decide now and execute: persist the evidence trail and record the winner's impression. */
    @POST
    @Path("/{id}/decision/run")
    public DecisionResult runDecision(@PathParam("id") long id) {
        return decisions.decideAndExecute(id);
    }
}
