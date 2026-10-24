package com.airportmedia.platform.repository;

import com.airportmedia.platform.common.enums.ScreenStatus;
import com.airportmedia.platform.domain.entity.Screen;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ScreenRepository implements PanacheRepository<Screen> {

    public Optional<Screen> firstOnline() {
        return find("status", ScreenStatus.ONLINE).firstResultOptional();
    }

    public Optional<Screen> firstOffline() {
        return find("status", ScreenStatus.OFFLINE).firstResultOptional();
    }

    public List<Screen> listOnline() {
        return list("status", ScreenStatus.ONLINE);
    }
}
