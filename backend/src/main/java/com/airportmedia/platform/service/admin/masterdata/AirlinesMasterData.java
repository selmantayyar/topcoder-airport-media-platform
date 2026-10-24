package com.airportmedia.platform.service.admin.masterdata;

import com.airportmedia.platform.domain.entity.Airline;
import com.airportmedia.platform.domain.model.output.MasterDataSchema;
import com.airportmedia.platform.domain.model.output.MasterDataSchema.Field;
import com.airportmedia.platform.repository.AirlineRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Airlines operating at the airport, keyed by their unique IATA code. */
@ApplicationScoped
public class AirlinesMasterData implements MasterDataType {

    private final AirlineRepository airlines;

    public AirlinesMasterData(AirlineRepository airlines) {
        this.airlines = airlines;
    }

    @Override
    public String type() {
        return "airlines";
    }

    @Override
    public MasterDataSchema schema() {
        return new MasterDataSchema(type(), "Airlines", List.of(
                Field.text("iataCode", "IATA code", true, 3),
                Field.text("icaoCode", "ICAO code", true, 4),
                Field.text("name", "Name", true, 128),
                Field.bool("lowCost", "Low-cost carrier")));
    }

    @Override
    public List<Map<String, Object>> list() {
        return airlines.listAll().stream()
                .sorted(Comparator.comparing((Airline a) -> a.iataCode))
                .map(AirlinesMasterData::row)
                .toList();
    }

    @Override
    public Map<String, Object> create(Map<String, Object> values) {
        Airline a = new Airline();
        apply(a, values);
        airlines.persist(a);
        return row(a);
    }

    @Override
    public Map<String, Object> update(long id, Map<String, Object> values) {
        Airline a = airlines.findByIdOptional(id).orElseThrow(() -> new NotFoundException("No airline " + id));
        apply(a, values);
        return row(a);
    }

    private void apply(Airline a, Map<String, Object> values) {
        String iata = Rows.text(values, "iataCode").toUpperCase();
        Rows.requireUnique(airlines.find("iataCode", iata).firstResultOptional(), a.id, other -> other.id,
                "iataCode", "IATA code " + iata + " is already in use");
        a.iataCode = iata;
        a.icaoCode = Rows.text(values, "icaoCode").toUpperCase();
        a.name = Rows.text(values, "name");
        a.lowCost = Boolean.TRUE.equals(values.get("lowCost"));
    }

    private static Map<String, Object> row(Airline a) {
        return Rows.of("id", a.id, "iataCode", a.iataCode, "icaoCode", a.icaoCode, "name", a.name, "lowCost", a.lowCost);
    }
}
