# 05 — ZonePresence + Crowd Profile computation

**What to build:** A live, continuously-updating estimate of how many passengers (and what kind) are near each zone over the next few minutes, computed from flight and queue data, viewable as it changes while the simulation clock advances.

**Blocked by:** 04 — Simulation clock & control bar

**Status:** done

- [x] `ZonePresence` is computed per zone per 5-minute window from nearby flights (pax count × time-to-boarding) and ACRIS queue mocks
- [x] Each flight has a plausible Crowd Profile (trip purpose, party type, age band, gender split, cabin/spend tier, nationality/language, dwell/delay context, crowd size) generated per the spec's mock-source rules
- [x] Profiles are plausible by the spec's own examples (e.g. a Monday 07:00 domestic flight skews business 25-44; a Friday evening holiday route skews families/couples)
- [x] A simple read view (debug page or API response) shows live ZonePresence and Crowd Profile numbers changing as the simulation clock advances
