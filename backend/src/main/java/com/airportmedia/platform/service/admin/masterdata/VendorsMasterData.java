package com.airportmedia.platform.service.admin.masterdata;

import com.airportmedia.platform.common.enums.AdvertiserKind;
import com.airportmedia.platform.domain.entity.Advertiser;
import com.airportmedia.platform.domain.model.output.MasterDataSchema;
import com.airportmedia.platform.domain.model.output.MasterDataSchema.Field;
import com.airportmedia.platform.repository.AdvertiserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Vendors: the companies that buy inventory — retailers, F&B brands and airlines. */
@ApplicationScoped
public class VendorsMasterData implements MasterDataType {

    private final AdvertiserRepository advertisers;

    public VendorsMasterData(AdvertiserRepository advertisers) {
        this.advertisers = advertisers;
    }

    @Override
    public String type() {
        return "vendors";
    }

    @Override
    public MasterDataSchema schema() {
        return new MasterDataSchema(type(), "Vendors", List.of(
                Field.text("name", "Name", true, 128),
                Field.choice("kind", "Kind", Rows.options(AdvertiserKind.class))));
    }

    @Override
    public List<Map<String, Object>> list() {
        return advertisers.listAll().stream()
                .sorted(Comparator.comparing((Advertiser a) -> a.name))
                .map(VendorsMasterData::row)
                .toList();
    }

    @Override
    public Map<String, Object> create(Map<String, Object> values) {
        Advertiser a = new Advertiser();
        apply(a, values);
        advertisers.persist(a);
        return row(a);
    }

    @Override
    public Map<String, Object> update(long id, Map<String, Object> values) {
        Advertiser a = advertisers.findByIdOptional(id).orElseThrow(() -> new NotFoundException("No vendor " + id));
        apply(a, values);
        return row(a);
    }

    private static void apply(Advertiser a, Map<String, Object> values) {
        a.name = Rows.text(values, "name");
        a.kind = AdvertiserKind.valueOf(Rows.text(values, "kind"));
    }

    private static Map<String, Object> row(Advertiser a) {
        return Rows.of("id", a.id, "name", a.name, "kind", a.kind.name());
    }
}
