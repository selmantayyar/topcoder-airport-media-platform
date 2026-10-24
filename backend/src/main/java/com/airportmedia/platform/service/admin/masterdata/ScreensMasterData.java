package com.airportmedia.platform.service.admin.masterdata;

import com.airportmedia.platform.common.enums.ScreenOrientation;
import com.airportmedia.platform.common.enums.ScreenStatus;
import com.airportmedia.platform.domain.entity.Screen;
import com.airportmedia.platform.domain.model.output.MasterDataSchema;
import com.airportmedia.platform.domain.model.output.MasterDataSchema.Field;
import com.airportmedia.platform.repository.ScreenRepository;
import com.airportmedia.platform.repository.ZoneRepository;
import com.airportmedia.platform.service.SimulationClock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Screens: the physical displays. Changing a screen's status stamps {@code statusChangedAt} with the
 * simulation time, which the incident monitor uses (e.g. bringing it back ONLINE resolves its outage).
 */
@ApplicationScoped
public class ScreensMasterData implements MasterDataType {

    private final ScreenRepository screens;
    private final ZoneRepository zones;
    private final SimulationClock clock;

    public ScreensMasterData(ScreenRepository screens, ZoneRepository zones, SimulationClock clock) {
        this.screens = screens;
        this.zones = zones;
        this.clock = clock;
    }

    @Override
    public String type() {
        return "screens";
    }

    @Override
    public MasterDataSchema schema() {
        return new MasterDataSchema(type(), "Screens", List.of(
                Field.text("code", "Code", true, 32),
                Field.text("name", "Name", true, 128),
                Field.ref("zone", "Location", ZoneOptions.of(zones)),
                Field.text("nearestGates", "Nearest gates", false, 128),
                Field.choice("orientation", "Orientation", Rows.options(ScreenOrientation.class)),
                Field.choice("status", "Status", Rows.options(ScreenStatus.class)),
                Field.number("latencyMs", "Latency (ms)", true, 0.0, 10_000.0)));
    }

    @Override
    public List<Map<String, Object>> list() {
        return screens.listAll().stream()
                .sorted(Comparator.comparing((Screen s) -> s.code))
                .map(ScreensMasterData::row)
                .toList();
    }

    @Override
    public Map<String, Object> create(Map<String, Object> values) {
        Screen s = new Screen();
        apply(s, values);
        screens.persist(s);
        return row(s);
    }

    @Override
    public Map<String, Object> update(long id, Map<String, Object> values) {
        Screen s = screens.findByIdOptional(id).orElseThrow(() -> new NotFoundException("No screen " + id));
        apply(s, values);
        return row(s);
    }

    private void apply(Screen s, Map<String, Object> values) {
        String code = Rows.text(values, "code");
        Rows.requireUnique(screens.find("code", code).firstResultOptional(), s.id, other -> other.id,
                "code", "Screen code " + code + " is already in use");
        ScreenStatus status = ScreenStatus.valueOf(Rows.text(values, "status"));
        if (status != s.status) {
            s.statusChangedAt = clock.snapshot().simTime();
        }
        ScreenOrientation orientation = ScreenOrientation.valueOf(Rows.text(values, "orientation"));
        boolean portrait = orientation == ScreenOrientation.PORTRAIT;

        s.code = code;
        s.name = Rows.text(values, "name");
        s.zone = zones.findById(Rows.id(values, "zone"));
        s.nearestGates = Rows.text(values, "nearestGates");
        s.orientation = orientation;
        s.widthPx = portrait ? 1080 : 1920;
        s.heightPx = portrait ? 1920 : 1080;
        s.status = status;
        s.latencyMs = (int) Math.round(Rows.number(values, "latencyMs"));
    }

    private static Map<String, Object> row(Screen s) {
        return Rows.of("id", s.id, "code", s.code, "name", s.name, "zone", Rows.ref(s.zone.id),
                "nearestGates", s.nearestGates, "orientation", s.orientation.name(), "status", s.status.name(),
                "latencyMs", s.latencyMs);
    }
}
