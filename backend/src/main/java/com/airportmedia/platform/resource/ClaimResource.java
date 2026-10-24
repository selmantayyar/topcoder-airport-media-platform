package com.airportmedia.platform.resource;

import com.airportmedia.platform.domain.model.output.ClaimResult;
import com.airportmedia.platform.domain.model.output.PromoInfo;
import com.airportmedia.platform.service.ClaimService;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/** The LAN-served promo claim flow a passenger's phone hits after scanning the QR. */
@Path("/api/promo")
@Produces(MediaType.APPLICATION_JSON)
public class ClaimResource {

    private final ClaimService claims;

    public ClaimResource(ClaimService claims) {
        this.claims = claims;
    }

    @GET
    @Path("/{code}")
    public PromoInfo info(@PathParam("code") String code) {
        return claims.info(code);
    }

    @POST
    @Path("/{code}/claim")
    public ClaimResult claim(@PathParam("code") String code) {
        return claims.claim(code);
    }
}
