# 15 — i18n + white-label theming + responsive/mobile polish

**What to build:** Every role's UI and ad content works in English and Spanish, the product is white-labeled via a theme config (name/logo/colors) with no real airport/airline/brand marks, and the layout is verified responsive at 1440px/1920px with a working mobile layout for the promo claim page.

**Blocked by:** 07 — Passenger Screen simulator + QR claim flow, 08 — Retailer live dashboard + passenger flow, 09 — Retailer campaign builder + creative library + promo codes + history, 10 — Platform Admin console, 11 — Media Manager, 12 — Airline Representative

**Status:** done

- [x] A language switch toggles all UI strings and ad content between English and Spanish across every role
- [x] Theme config (product name, logo, colors) is applied consistently and no real airport/airline/brand names or marks appear anywhere
- [x] All dashboards are verified responsive and usable at both 1440px and 1920px widths
- [x] The promo claim page (ticket 07) has a working, usable mobile layout
- [x] Dark control-room theme is applied for Admin; light theme for the business roles

EN | ES switch in the header (and on the claim page), carried as `?lang=` and remembered per browser, so a kiosk URL or
a QR link can pin it. UI strings live in `frontend/src/i18n/messages.ts`; the Spanish catalog is typed against the
English keys, so a missing translation fails `tsc`. Server-written audit text (decision reasons, rule notes, incident
details, QA findings) is shown as logged and deliberately not translated.

Ad content: a creative keeps its primary copy plus translations (`creative_translation`, V7). Playback and the claim
page take `?lang=` and render the requested language when the creative has it, else the original — labelled with
the language actually shown; copy is never machine-translated. Every seeded creative, the airline's default sponsored
copy, delay vouchers and the GenAI assistant's pages carry Spanish; the campaign builder takes optional
second-language copy; library reuse and duplication carry translations; QA (incident monitor + approval queue)
checks every language. V7 backfills Spanish for creatives seeded before it.

White-label: `frontend/src/brand.ts` (product name, host-airport name, optional logo URL, accent and screen colours,
published as CSS variables) and a built-in mark; the Vite logo favicon/asset (a real brand mark) was replaced.
Fictional names throughout (Amberport International, invented airlines/retailers). Destination cities and their
IATA codes stay real — they are routes, not marks, and the Gate 42 worked example depends on them.

Verified with headless Chrome over CDP at 1440 and 1920 (every role, split-screen demo, kiosk) and 360/390 for the
claim page: no horizontal overflow anywhere; header fits one row at 1440; table headers now align with their columns.
