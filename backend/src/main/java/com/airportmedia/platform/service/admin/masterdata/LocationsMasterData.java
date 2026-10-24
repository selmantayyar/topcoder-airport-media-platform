package com.airportmedia.platform.service.admin.masterdata;

import com.airportmedia.platform.common.enums.ZoneKind;
import com.airportmedia.platform.domain.entity.Terminal;
import com.airportmedia.platform.domain.entity.Zone;
import com.airportmedia.platform.domain.model.output.MasterDataSchema;
import com.airportmedia.platform.domain.model.output.MasterDataSchema.Field;
import com.airportmedia.platform.domain.model.output.MasterDataSchema.Option;
import com.airportmedia.platform.repository.TerminalRepository;
import com.airportmedia.platform.repository.ZoneRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Locations: the zones of each terminal that screens, gates and outlets sit in. */
@ApplicationScoped
public class LocationsMasterData implements MasterDataType {

    private final ZoneRepository zones;
    private final TerminalRepository terminals;

    public LocationsMasterData(ZoneRepository zones, TerminalRepository terminals) {
        this.zones = zones;
        this.terminals = terminals;
    }

    @Override
    public String type() {
        return "locations";
    }

    @Override
    public MasterDataSchema schema() {
        List<Option> terminalOptions = terminals.listAll().stream()
                .sorted(Comparator.comparing((Terminal t) -> t.code))
                .map(t -> new Option(String.valueOf(t.id), t.name + " (" + t.code + ")"))
                .toList();
        return new MasterDataSchema(type(), "Locations", List.of(
                Field.text("name", "Name", true, 128),
                Field.choice("kind", "Kind", Rows.options(ZoneKind.class)),
                Field.ref("terminal", "Terminal", terminalOptions),
                Field.number("latitude", "Latitude", true, -90.0, 90.0),
                Field.number("longitude", "Longitude", true, -180.0, 180.0)));
    }

    @Override
    public List<Map<String, Object>> list() {
        return zones.listAll().stream()
                .sorted(ZoneOptions.BY_TERMINAL_THEN_NAME)
                .map(LocationsMasterData::row)
                .toList();
    }

    @Override
    public Map<String, Object> create(Map<String, Object> values) {
        Zone z = new Zone();
        // Provenance key for a zone created in the console rather than ingested from ACRIS.
        z.acrisId = "local:zone:" + UUID.randomUUID();
        apply(z, values);
        zones.persist(z);
        return row(z);
    }

    @Override
    public Map<String, Object> update(long id, Map<String, Object> values) {
        Zone z = zones.findByIdOptional(id).orElseThrow(() -> new NotFoundException("No location " + id));
        apply(z, values);
        return row(z);
    }

    private void apply(Zone z, Map<String, Object> values) {
        z.name = Rows.text(values, "name");
        z.kind = ZoneKind.valueOf(Rows.text(values, "kind"));
        z.terminal = terminals.findById(Rows.id(values, "terminal"));
        z.latitude = Rows.number(values, "latitude");
        z.longitude = Rows.number(values, "longitude");
    }

    private static Map<String, Object> row(Zone z) {
        return Rows.of("id", z.id, "name", z.name, "kind", z.kind.name(), "terminal", Rows.ref(z.terminal.id),
                "latitude", z.latitude, "longitude", z.longitude);
    }
}
