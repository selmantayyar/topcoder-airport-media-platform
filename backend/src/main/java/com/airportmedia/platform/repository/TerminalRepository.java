package com.airportmedia.platform.repository;

import com.airportmedia.platform.domain.entity.Terminal;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TerminalRepository implements PanacheRepository<Terminal> {
}
