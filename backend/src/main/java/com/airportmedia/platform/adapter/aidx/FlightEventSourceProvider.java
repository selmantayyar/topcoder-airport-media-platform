package com.airportmedia.platform.adapter.aidx;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Selects the active {@link FlightEventSource} from the {@code app.aidx.source} config value. */
@ApplicationScoped
public class FlightEventSourceProvider {

    private final MockFlightEventSource mock;
    private final LiveFlightEventSource live;
    private final String mode;

    public FlightEventSourceProvider(
            MockFlightEventSource mock,
            LiveFlightEventSource live,
            @ConfigProperty(name = "app.aidx.source", defaultValue = "mock") String mode) {
        this.mock = mock;
        this.live = live;
        this.mode = mode;
    }

    public FlightEventSource active() {
        return "live".equals(mode) ? live : mock;
    }
}
