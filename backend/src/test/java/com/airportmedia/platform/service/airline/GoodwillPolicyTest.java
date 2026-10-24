package com.airportmedia.platform.service.airline;

import static org.assertj.core.api.Assertions.assertThat;

import com.airportmedia.platform.common.enums.AdvertiserKind;
import com.airportmedia.platform.common.enums.OutletType;
import com.airportmedia.platform.common.enums.VoucherKind;
import com.airportmedia.platform.service.airline.GoodwillPolicy.PartnerCandidate;
import java.util.List;
import org.junit.jupiter.api.Test;

class GoodwillPolicyTest {

    @Test
    void delaysEarnAMealAtTheThresholdAndALoungePassAtTwiceIt() {
        assertThat(GoodwillPolicy.voucherFor(59, 60)).isEqualTo(VoucherKind.NONE);
        assertThat(GoodwillPolicy.voucherFor(60, 60)).isEqualTo(VoucherKind.MEAL);
        assertThat(GoodwillPolicy.voucherFor(119, 60)).isEqualTo(VoucherKind.MEAL);
        assertThat(GoodwillPolicy.voucherFor(120, 60)).isEqualTo(VoucherKind.LOUNGE);
    }

    private static PartnerCandidate candidate(long id, AdvertiserKind kind, OutletType type, boolean sameTerminal) {
        return new PartnerCandidate(id, "Partner " + id, kind, type, sameTerminal);
    }

    @Test
    void aMealIsCoFundedByAnFnbOutletInTheSameTerminalFirst() {
        var chosen = GoodwillPolicy.choosePartner(VoucherKind.MEAL, List.of(
                candidate(1, AdvertiserKind.RETAILER, OutletType.RETAIL, true),
                candidate(2, AdvertiserKind.FNB, OutletType.FOOD_AND_BEVERAGE, false),
                candidate(3, AdvertiserKind.FNB, OutletType.FOOD_AND_BEVERAGE, true)));

        assertThat(chosen).hasValueSatisfying(p -> assertThat(p.advertiserId()).isEqualTo(3));
    }

    @Test
    void aMealFallsBackToAnFnbOutletElsewhere() {
        var chosen = GoodwillPolicy.choosePartner(VoucherKind.MEAL, List.of(
                candidate(1, AdvertiserKind.RETAILER, OutletType.RETAIL, true),
                candidate(2, AdvertiserKind.FNB, OutletType.FOOD_AND_BEVERAGE, false)));

        assertThat(chosen).hasValueSatisfying(p -> assertThat(p.advertiserId()).isEqualTo(2));
    }

    @Test
    void aLoungePassPrefersALoungeOutletAndOtherwiseFallsBackToAMealPartner() {
        var lounge = GoodwillPolicy.choosePartner(VoucherKind.LOUNGE, List.of(
                candidate(2, AdvertiserKind.FNB, OutletType.FOOD_AND_BEVERAGE, true),
                candidate(5, AdvertiserKind.RETAILER, OutletType.LOUNGE, true)));
        var fallback = GoodwillPolicy.choosePartner(VoucherKind.LOUNGE, List.of(
                candidate(2, AdvertiserKind.FNB, OutletType.FOOD_AND_BEVERAGE, true)));

        assertThat(lounge).hasValueSatisfying(p -> assertThat(p.advertiserId()).isEqualTo(5));
        assertThat(fallback).hasValueSatisfying(p -> assertThat(p.advertiserId()).isEqualTo(2));
    }

    @Test
    void airlinesNeverCoFundAndNoCandidatesMeansNoPartner() {
        assertThat(GoodwillPolicy.choosePartner(VoucherKind.MEAL, List.of(
                candidate(9, AdvertiserKind.AIRLINE, OutletType.FOOD_AND_BEVERAGE, true)))).isEmpty();
        assertThat(GoodwillPolicy.choosePartner(VoucherKind.MEAL, List.of())).isEmpty();
    }
}
