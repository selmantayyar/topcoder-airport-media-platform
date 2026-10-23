package com.airportmedia.platform.adapter.acris;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * A source of ACRIS (FIWARE dataModel.ACRIS) topology as NGSI-LD entities. The mock
 * generates the fictional airport's terminals, zones, checkpoints and queue readings;
 * a Live implementation would pull them from an ACRIS context broker.
 */
public interface AcrisSource {

    String mode();

    /** NGSI-LD entities in dependency order: terminals, then zones, checkpoints, measurements. */
    List<JsonNode> entities();
}
