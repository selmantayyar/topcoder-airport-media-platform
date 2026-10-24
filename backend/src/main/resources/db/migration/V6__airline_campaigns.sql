-- Airline Representative (ticket 12): campaigns an airline sponsors for one of its flights, and the delay
-- goodwill vouchers created automatically for a delayed flight, co-funded with a retail partner.

ALTER TABLE campaign ADD COLUMN purpose VARCHAR(16);
ALTER TABLE campaign ADD COLUMN linked_flight_id BIGINT REFERENCES flight(id);
ALTER TABLE campaign ADD COLUMN co_funder_id BIGINT REFERENCES advertiser(id);

-- One goodwill voucher per flight.
CREATE UNIQUE INDEX uq_campaign_goodwill_flight ON campaign (linked_flight_id) WHERE purpose = 'GOODWILL';
