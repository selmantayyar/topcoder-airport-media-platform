package com.airportmedia.platform.repository;

import com.airportmedia.platform.domain.entity.Outlet;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class OutletRepository implements PanacheRepository<Outlet> {

    public List<Outlet> findByAdvertiser(long advertiserId) {
        return list("advertiser.id", advertiserId);
    }
}
