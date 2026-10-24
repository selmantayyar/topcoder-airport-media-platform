package com.airportmedia.platform.domain.model.output;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The Media Manager's inventory calendar and yield view for one day: every screen × operating hour as
 * booked or available, flagged premium when predicted footfall is high, with a suggested price per slot,
 * overall occupancy, and a 7-day revenue forecast.
 */
public record InventoryView(
        LocalDate day,
        List<Integer> hours,
        List<ScreenRow> screens,
        double occupancyPct,
        BigDecimal basePricePerHour,
        double premiumFootfallThreshold,
        List<ForecastDay> forecast,
        BigDecimal forecastTotal) {

    public record ScreenRow(long screenId, String code, Long zoneId, String zoneName, List<Slot> slots) {}

    /** One screen-hour: sold to a live campaign or not, premium or not, predicted footfall and price. */
    public record Slot(int hour, boolean booked, boolean premium, int footfall, BigDecimal price) {}

    public record ForecastDay(LocalDate date, double occupancyPct, BigDecimal revenue) {}
}
