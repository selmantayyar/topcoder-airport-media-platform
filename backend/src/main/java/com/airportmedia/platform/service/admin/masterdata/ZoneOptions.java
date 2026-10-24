package com.airportmedia.platform.service.admin.masterdata;

import com.airportmedia.platform.domain.entity.Zone;
import com.airportmedia.platform.domain.model.output.MasterDataSchema.Option;
import com.airportmedia.platform.repository.ZoneRepository;
import java.util.Comparator;
import java.util.List;

/** Zone pick-list shared by the types that place something in a location. */
final class ZoneOptions {

    static final Comparator<Zone> BY_TERMINAL_THEN_NAME =
            Comparator.comparing((Zone z) -> z.terminal.code).thenComparing(z -> z.name);

    private ZoneOptions() {}

    static List<Option> of(ZoneRepository zones) {
        return zones.listAll().stream()
                .sorted(BY_TERMINAL_THEN_NAME)
                .map(z -> new Option(String.valueOf(z.id), z.name + " (" + z.terminal.code + ")"))
                .toList();
    }
}
