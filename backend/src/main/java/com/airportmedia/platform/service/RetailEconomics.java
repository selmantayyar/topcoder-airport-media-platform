package com.airportmedia.platform.service;

import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * The money model, in one place:
 * <ul>
 *   <li>spend = impressions × {@code app.decision.impression-cost} (what a campaign has burnt of its budget)</li>
 *   <li>attributed revenue = redemptions × {@code app.retail.avg-basket}</li>
 *   <li>ROI = (revenue − spend) ÷ spend, or 0 when nothing has been spent</li>
 * </ul>
 */
@ApplicationScoped
public class RetailEconomics {

    private final BigDecimal impressionCost;
    private final BigDecimal averageBasket;

    public RetailEconomics(
            @ConfigProperty(name = "app.decision.impression-cost", defaultValue = "0.5") double impressionCost,
            @ConfigProperty(name = "app.retail.avg-basket", defaultValue = "45.0") double averageBasket) {
        this.impressionCost = BigDecimal.valueOf(impressionCost);
        this.averageBasket = BigDecimal.valueOf(averageBasket);
    }

    public BigDecimal spend(long impressions) {
        return BigDecimal.valueOf(impressions).multiply(impressionCost).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal revenue(long redemptions) {
        return BigDecimal.valueOf(redemptions).multiply(averageBasket).setScale(2, RoundingMode.HALF_UP);
    }

    public double roi(BigDecimal revenue, BigDecimal spend) {
        if (spend.signum() <= 0) {
            return 0.0;
        }
        return revenue.subtract(spend).divide(spend, 4, RoundingMode.HALF_UP).doubleValue();
    }
}
