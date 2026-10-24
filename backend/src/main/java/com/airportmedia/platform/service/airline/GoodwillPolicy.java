package com.airportmedia.platform.service.airline;

import com.airportmedia.platform.common.enums.AdvertiserKind;
import com.airportmedia.platform.common.enums.OutletType;
import com.airportmedia.platform.common.enums.VoucherKind;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Delay goodwill rules. A delay of at least the threshold earns a MEAL voucher; at least twice the threshold, a
 * LOUNGE pass. The voucher is co-funded by a retail partner chosen in order of preference:
 * <ol>
 *   <li>LOUNGE only: a lounge outlet in the flight's terminal, then a lounge outlet anywhere;</li>
 *   <li>an F&amp;B outlet in the flight's terminal, then an F&amp;B outlet anywhere.</li>
 * </ol>
 * Airlines never co-fund. Ties go to the lowest advertiser id, so the choice is reproducible.
 */
public final class GoodwillPolicy {

    /** A retail company that could co-fund a voucher, via one of its outlets. */
    public record PartnerCandidate(long advertiserId, String name, AdvertiserKind kind, OutletType outletType,
            boolean sameTerminal) {}

    private GoodwillPolicy() {}

    public static VoucherKind voucherFor(int delayMinutes, int thresholdMinutes) {
        if (delayMinutes >= 2 * thresholdMinutes) {
            return VoucherKind.LOUNGE;
        }
        return delayMinutes >= thresholdMinutes ? VoucherKind.MEAL : VoucherKind.NONE;
    }

    public static Optional<PartnerCandidate> choosePartner(VoucherKind kind, List<PartnerCandidate> candidates) {
        List<PartnerCandidate> eligible = candidates.stream().filter(c -> c.kind() != AdvertiserKind.AIRLINE).toList();
        if (kind == VoucherKind.LOUNGE) {
            Optional<PartnerCandidate> lounge = first(eligible, outlet(OutletType.LOUNGE));
            if (lounge.isPresent()) {
                return lounge;
            }
        }
        return kind == VoucherKind.NONE ? Optional.empty() : first(eligible, outlet(OutletType.FOOD_AND_BEVERAGE));
    }

    /** Same-terminal matches first, then anywhere; lowest advertiser id breaks ties. */
    private static Optional<PartnerCandidate> first(List<PartnerCandidate> candidates, Predicate<PartnerCandidate> match) {
        return candidates.stream()
                .filter(match)
                .min(Comparator.comparing((PartnerCandidate c) -> !c.sameTerminal())
                        .thenComparingLong(PartnerCandidate::advertiserId));
    }

    private static Predicate<PartnerCandidate> outlet(OutletType type) {
        return c -> c.outletType() == type;
    }
}
