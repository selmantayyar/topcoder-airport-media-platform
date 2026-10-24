package com.airportmedia.platform.domain.model.output;

import java.math.BigDecimal;
import java.util.List;

/**
 * A retail company's live performance: the impressions → scans → redemptions funnel, the conversion
 * rate (redemptions ÷ impressions), attributed revenue, and a per-campaign breakdown.
 */
public record DashboardMetrics(
        long advertiserId,
        String advertiserName,
        long impressions,
        long scans,
        long redemptions,
        double conversionRate,
        BigDecimal attributedRevenue,
        List<CampaignMetric> campaigns) {

    /** One campaign's slice of the company's totals. */
    public record CampaignMetric(
            long campaignId,
            String name,
            String status,
            long impressions,
            long scans,
            long redemptions,
            BigDecimal revenue) {}
}
