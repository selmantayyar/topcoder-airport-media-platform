package com.airportmedia.platform.repository;

import com.airportmedia.platform.domain.entity.Airline;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AirlineRepository implements PanacheRepository<Airline> {
}
