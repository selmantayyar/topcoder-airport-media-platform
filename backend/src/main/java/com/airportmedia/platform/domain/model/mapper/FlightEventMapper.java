package com.airportmedia.platform.domain.model.mapper;

import com.airportmedia.platform.adapter.aidx.AidxFlightLegUpdate;
import com.airportmedia.platform.domain.entity.Flight;
import com.airportmedia.platform.domain.entity.FlightEvent;
import jakarta.enterprise.context.ApplicationScoped;

/** Builds a canonical {@link FlightEvent} from a translated AIDX update and its resolved flight. */
@ApplicationScoped
public class FlightEventMapper {

    public FlightEvent toFlightEvent(AidxFlightLegUpdate update, Flight flight) {
        FlightEvent event = new FlightEvent();
        event.flight = flight;
        event.type = update.type();
        event.occurredAt = update.occurredAt();
        event.source = "AIDX";
        event.messageRef = update.messageRef();
        event.newGate = update.newGate();
        event.newStatus = update.newStatus();
        event.delayMinutes = update.delayMinutes();
        return event;
    }
}
