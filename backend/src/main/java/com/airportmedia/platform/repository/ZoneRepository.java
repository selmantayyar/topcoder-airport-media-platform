package com.airportmedia.platform.repository;

import com.airportmedia.platform.domain.entity.Zone;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ZoneRepository implements PanacheRepository<Zone> {
}
