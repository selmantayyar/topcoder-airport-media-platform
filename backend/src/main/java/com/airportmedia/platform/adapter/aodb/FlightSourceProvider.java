package com.airportmedia.platform.adapter.aodb;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Selects the active {@link FlightSource} from the {@code app.flight.source} config value. */
@ApplicationScoped
public class FlightSourceProvider {

    private final MockFlightSource mock;
    private final LiveFlightSource live;
    private final String mode;

    public FlightSourceProvider(
            MockFlightSource mock,
            LiveFlightSource live,
            @ConfigProperty(name = "app.flight.source", defaultValue = "mock") String mode) {
        this.mock = mock;
        this.live = live;
        this.mode = mode;
    }

    public FlightSource active() {
        return "live".equals(mode) ? live : mock;
    }
}
