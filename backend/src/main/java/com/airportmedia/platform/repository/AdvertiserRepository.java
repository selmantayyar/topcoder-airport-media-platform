package com.airportmedia.platform.repository;

import com.airportmedia.platform.common.enums.AdvertiserKind;
import com.airportmedia.platform.domain.entity.Advertiser;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class AdvertiserRepository implements PanacheRepository<Advertiser> {

    /** The advertiser record an airline buys inventory through (it carries the airline's name). */
    public Optional<Advertiser> findAirline(String airlineName) {
        return find("kind = ?1 and name = ?2", AdvertiserKind.AIRLINE, airlineName).firstResultOptional();
    }

    /** Retail companies (shops and F&B) — the dashboard's selectable outlets, airlines excluded. */
    public List<Advertiser> listRetailers() {
        return list("kind in ?1 order by name",
                List.of(AdvertiserKind.RETAILER, AdvertiserKind.FNB));
    }
}
