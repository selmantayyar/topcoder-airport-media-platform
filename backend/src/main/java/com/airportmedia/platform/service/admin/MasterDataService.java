package com.airportmedia.platform.service.admin;

import com.airportmedia.platform.domain.model.output.MasterDataSchema;
import com.airportmedia.platform.service.admin.masterdata.MasterDataType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generic master-data CRUD: every {@link MasterDataType} bean is reachable by its type name, and every
 * create/update is validated against that type's schema first, so one API serves all of them.
 */
@ApplicationScoped
public class MasterDataService {

    /** Display order of the types in the admin console. */
    private static final List<String> ORDER = List.of("vendors", "outlets", "airlines", "locations", "screens");

    private final Map<String, MasterDataType> types = new LinkedHashMap<>();

    public MasterDataService(Instance<MasterDataType> beans) {
        beans.stream()
                .sorted((a, b) -> Integer.compare(rank(a.type()), rank(b.type())))
                .forEach(t -> types.put(t.type(), t));
    }

    @Transactional
    public List<MasterDataSchema> schemas() {
        return types.values().stream().map(MasterDataType::schema).toList();
    }

    @Transactional
    public List<Map<String, Object>> list(String type) {
        return require(type).list();
    }

    @Transactional
    public Map<String, Object> create(String type, Map<String, Object> payload) {
        MasterDataType t = require(type);
        return t.create(MasterDataValidator.validate(t.schema(), payload));
    }

    @Transactional
    public Map<String, Object> update(String type, long id, Map<String, Object> payload) {
        MasterDataType t = require(type);
        return t.update(id, MasterDataValidator.validate(t.schema(), payload));
    }

    private MasterDataType require(String type) {
        MasterDataType t = types.get(type);
        if (t == null) {
            throw new NotFoundException("No master-data type '" + type + "' (known: " + types.keySet() + ")");
        }
        return t;
    }

    private static int rank(String type) {
        int i = ORDER.indexOf(type);
        return i < 0 ? ORDER.size() : i;
    }
}
