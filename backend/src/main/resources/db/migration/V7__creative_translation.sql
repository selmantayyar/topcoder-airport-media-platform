-- i18n (ticket 15): a creative keeps its primary-language copy and may carry translations, so one ad
-- renders in English or Spanish on the same screen without a second campaign.

CREATE TABLE creative_translation (
    creative_id  BIGINT NOT NULL REFERENCES creative(id) ON DELETE CASCADE,
    language     VARCHAR(8) NOT NULL,
    headline     VARCHAR(256) NOT NULL,
    offer_text   VARCHAR(256),
    body         TEXT,
    PRIMARY KEY (creative_id, language)
);

-- Backfill for databases seeded before this migration (a fresh database seeds translations itself). Only the
-- platform's own seeded copy is translated, and only when every slot matches it, so no retailer-written creative
-- gets words it didn't approve. Seeded creatives once labelled 'es' carried English copy; they are relabelled.
UPDATE creative c SET language = 'en'
FROM (VALUES ('15% off perfume'), ('Buy 2 get 1 free'), ('Lounge day pass'), ('Kids meal deal'),
             ('Premium watch sale'), ('Festive sweets 20% off'), ('Free coffee upgrade'), ('Duty-free spirits')) AS o(en)
WHERE c.language = 'es' AND c.headline = o.en;

INSERT INTO creative_translation (creative_id, language, headline, offer_text, body)
SELECT c.id, 'es', o.es, CASE WHEN c.offer_text IS NULL THEN NULL ELSE o.es END,
       CASE WHEN c.body IS NULL THEN NULL ELSE 'Oferta por tiempo limitado para viajeros en Amberport International.' END
FROM creative c
JOIN (VALUES ('15% off perfume', '15% de descuento en perfumes'),
             ('Buy 2 get 1 free', 'Compra 2 y llévate 1 gratis'),
             ('Lounge day pass', 'Pase de día a la sala VIP'),
             ('Kids meal deal', 'Menú infantil en oferta'),
             ('Premium watch sale', 'Rebajas en relojes de lujo'),
             ('Festive sweets 20% off', 'Dulces festivos con 20% de descuento'),
             ('Free coffee upgrade', 'Mejora de café gratis'),
             ('Duty-free spirits', 'Licores libres de impuestos')) AS o(en, es) ON c.headline = o.en
WHERE c.language = 'en'
  AND (c.offer_text IS NULL OR c.offer_text = o.en)
  AND (c.body IS NULL OR c.body = 'Limited-time offer for travellers at Amberport International.')
ON CONFLICT DO NOTHING;
