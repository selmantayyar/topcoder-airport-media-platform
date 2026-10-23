package com.airportmedia.platform.service;

import com.airportmedia.platform.domain.model.output.ServerEvent;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * The one live push channel. Any component emits {@link ServerEvent}s here; every
 * connected SSE client receives them, so an injected event reaches all role views at once.
 */
@ApplicationScoped
public class EventStream {

    private final BroadcastProcessor<ServerEvent> processor = BroadcastProcessor.create();

    public void emit(ServerEvent event) {
        processor.onNext(event);
    }

    public Multi<ServerEvent> multi() {
        return processor;
    }
}
