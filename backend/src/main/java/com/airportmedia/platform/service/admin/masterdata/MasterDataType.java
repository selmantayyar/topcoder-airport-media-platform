package com.airportmedia.platform.service.admin.masterdata;

import com.airportmedia.platform.domain.model.output.MasterDataSchema;
import java.util.List;
import java.util.Map;

/**
 * One editable master-data type. Rows are flat maps of {@code id} plus each schema field (ENUM values as
 * their name, REF values as the referenced id string), so one generic list + form renders every type.
 * {@code create}/{@code update} receive payloads already validated and coerced by {@code MasterDataValidator}.
 */
public interface MasterDataType {

    /** URL-safe name, e.g. {@code screens}. */
    String type();

    /** The form schema, with ENUM/REF options resolved from current data. */
    MasterDataSchema schema();

    List<Map<String, Object>> list();

    Map<String, Object> create(Map<String, Object> values);

    Map<String, Object> update(long id, Map<String, Object> values);
}
