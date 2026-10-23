package com.airportmedia.platform.adapter.acris;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

/** Stub for a real ACRIS context broker. Implement this one class to go live. */
@ApplicationScoped
public class LiveAcrisSource implements AcrisSource {

    @Override
    public String mode() {
        return "live";
    }

    @Override
    public List<JsonNode> entities() {
        throw new UnsupportedOperationException(
                "Live ACRIS feed not wired in this POC. Set app.acris.source=mock.");
    }
}
