package com.airportmedia.platform.adapter.acris;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Selects the active {@link AcrisSource} from the {@code app.acris.source} config value. */
@ApplicationScoped
public class AcrisSourceProvider {

    private final MockAcrisSource mock;
    private final LiveAcrisSource live;
    private final String mode;

    public AcrisSourceProvider(
            MockAcrisSource mock,
            LiveAcrisSource live,
            @ConfigProperty(name = "app.acris.source", defaultValue = "mock") String mode) {
        this.mock = mock;
        this.live = live;
        this.mode = mode;
    }

    public AcrisSource active() {
        return "live".equals(mode) ? live : mock;
    }
}
