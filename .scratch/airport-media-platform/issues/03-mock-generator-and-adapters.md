# 03 — Mock data generator + AODB/AIDX/ACRIS adapters

**What to build:** Running the data generator seeds one fictional airport-day of data (flights, screens, outlets, airlines, campaigns), produced through source-shaped adapters (AODB, AIDX, ACRIS) that translate into the canonical internal model, with each adapter swappable to a stub "Live" implementation via a single config value.

**Blocked by:** 01 — App skeleton + one-command start

**Status:** done

- [x] One generator command seeds the database with a fictional airport's flights, screens, outlets, airlines, and campaigns (runs on startup when the DB is empty)
- [x] `FlightSource`, `AIDXSource` (or equivalent), and `ACRISSource` adapters exist behind a shared interface, each with a mock implementation
- [x] Each adapter also has a stub "Live" implementation, and switching between mock and Live is a single config value per source
- [x] Mock AODB output matches the real 33-column-style AODB export shape (A-CDM milestones included); mock AIDX output matches the real `IATA_AIDX_FlightLegNotifRQ` structure; mock ACRIS output matches the real public FIWARE `dataModel.ACRIS` entities (TerminalFacility, ZoneAreaLocation, CheckpointFacility, PassengerQueue, QueueMeasurement)
- [x] Canonical entities (Flight, GateAssignment, AircraftType, FlightEvent, Terminal, Zone, Checkpoint, QueueMeasurement) are populated correctly from the adapters
