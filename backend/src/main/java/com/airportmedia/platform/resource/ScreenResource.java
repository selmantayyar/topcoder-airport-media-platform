package com.airportmedia.platform.resource;

import com.airportmedia.platform.domain.model.output.PlaybackView;
import com.airportmedia.platform.domain.model.output.ScreenSummary;
import com.airportmedia.platform.service.PlaybackService;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

/** Screen chooser and the per-screen playback read model for the passenger-screen simulator. */
@Path("/api/screens")
@Produces(MediaType.APPLICATION_JSON)
public class ScreenResource {

    private final PlaybackService playback;

    public ScreenResource(PlaybackService playback) {
        this.playback = playback;
    }

    @GET
    public List<ScreenSummary> screens(@QueryParam("gate") String gate, @QueryParam("zone") String zone) {
        return playback.listScreens(gate, zone);
    }

    @GET
    @Path("/{id}/playback")
    public PlaybackView playback(@PathParam("id") long id) {
        return playback.playback(id);
    }
}
