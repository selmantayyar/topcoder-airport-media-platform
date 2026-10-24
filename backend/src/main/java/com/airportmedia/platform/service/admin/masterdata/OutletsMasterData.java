package com.airportmedia.platform.service.admin.masterdata;

import com.airportmedia.platform.common.enums.AdvertiserKind;
import com.airportmedia.platform.common.enums.OutletType;
import com.airportmedia.platform.domain.entity.Outlet;
import com.airportmedia.platform.domain.model.output.MasterDataSchema;
import com.airportmedia.platform.domain.model.output.MasterDataSchema.Field;
import com.airportmedia.platform.domain.model.output.MasterDataSchema.Option;
import com.airportmedia.platform.repository.AdvertiserRepository;
import com.airportmedia.platform.repository.OutletRepository;
import com.airportmedia.platform.repository.ZoneRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Outlets: the shops and F&B units retailers run, each placed in a zone. */
@ApplicationScoped
public class OutletsMasterData implements MasterDataType {

    private final OutletRepository outlets;
    private final AdvertiserRepository advertisers;
    private final ZoneRepository zones;

    public OutletsMasterData(OutletRepository outlets, AdvertiserRepository advertisers, ZoneRepository zones) {
        this.outlets = outlets;
        this.advertisers = advertisers;
        this.zones = zones;
    }

    @Override
    public String type() {
        return "outlets";
    }

    @Override
    public MasterDataSchema schema() {
        List<Option> retailers = advertisers.listRetailers().stream()
                .map(a -> new Option(String.valueOf(a.id), a.name + (a.kind == AdvertiserKind.FNB ? " (F&B)" : "")))
                .toList();
        return new MasterDataSchema(type(), "Outlets & F&B", List.of(
                Field.text("name", "Name", true, 128),
                Field.ref("vendor", "Retailer", retailers),
                Field.choice("type", "Type", Rows.options(OutletType.class)),
                Field.ref("zone", "Location", ZoneOptions.of(zones))));
    }

    @Override
    public List<Map<String, Object>> list() {
        return outlets.listAll().stream()
                .sorted(Comparator.comparing((Outlet o) -> o.name))
                .map(OutletsMasterData::row)
                .toList();
    }

    @Override
    public Map<String, Object> create(Map<String, Object> values) {
        Outlet o = new Outlet();
        apply(o, values);
        outlets.persist(o);
        return row(o);
    }

    @Override
    public Map<String, Object> update(long id, Map<String, Object> values) {
        Outlet o = outlets.findByIdOptional(id).orElseThrow(() -> new NotFoundException("No outlet " + id));
        apply(o, values);
        return row(o);
    }

    private void apply(Outlet o, Map<String, Object> values) {
        o.name = Rows.text(values, "name");
        o.advertiser = advertisers.findById(Rows.id(values, "vendor"));
        o.type = OutletType.valueOf(Rows.text(values, "type"));
        o.zone = zones.findById(Rows.id(values, "zone"));
    }

    private static Map<String, Object> row(Outlet o) {
        return Rows.of("id", o.id, "name", o.name, "vendor", Rows.ref(o.advertiser.id), "type", o.type.name(),
                "zone", Rows.ref(o.zone.id));
    }
}
