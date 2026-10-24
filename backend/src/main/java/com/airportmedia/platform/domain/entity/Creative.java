package com.airportmedia.platform.domain.entity;

import com.airportmedia.platform.domain.model.output.LocalizedCopy;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Platform-owned: a reusable ad page an advertiser can attach to campaigns. */
@Entity
@Table(name = "creative")
public class Creative {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false)
    public Advertiser advertiser;

    @ManyToOne
    public Campaign campaign;

    @Column(nullable = false)
    public String name;

    @Column(nullable = false)
    public String templateKey;

    /** ISO language code of this creative's copy, e.g. "en" or "es". */
    @Column(nullable = false)
    public String language;

    @Column(nullable = false)
    public String headline;

    @Column(columnDefinition = "text")
    public String body;

    public String offerText;

    /** Copy in other languages, keyed by lower-case ISO language code. */
    @ElementCollection
    @CollectionTable(name = "creative_translation", joinColumns = @JoinColumn(name = "creative_id"))
    @MapKeyColumn(name = "language")
    public Map<String, CreativeTranslation> translations = new HashMap<>();

    public void translate(String language, String headline, String offerText, String body) {
        CreativeTranslation t = new CreativeTranslation();
        t.headline = headline;
        t.offerText = offerText;
        t.body = body;
        translations.put(language.toLowerCase(Locale.ROOT), t);
    }

    /**
     * The copy to render for {@code language}: the primary copy, a stored translation, or — when this creative
     * has no copy in that language — the primary copy labelled with its own language. Never machine-translated,
     * so a screen only ever shows copy someone wrote or approved.
     */
    public LocalizedCopy copyIn(String language) {
        String wanted = language == null ? "" : language.trim().toLowerCase(Locale.ROOT);
        CreativeTranslation t = wanted.equals(this.language) ? null : translations.get(wanted);
        return t == null
                ? new LocalizedCopy(this.language, headline, offerText, body)
                : new LocalizedCopy(wanted, t.headline, t.offerText, t.body);
    }

    /** Every language this creative can render in, primary first. */
    public List<String> languages() {
        List<String> all = new ArrayList<>();
        all.add(language);
        translations.keySet().stream().sorted().filter(l -> !l.equals(language)).forEach(all::add);
        return all;
    }

    /** Copies {@code source}'s translations onto this creative (library reuse, campaign duplication). */
    public void copyTranslationsFrom(Creative source) {
        source.translations.forEach((lang, t) -> translate(lang, t.headline, t.offerText, t.body));
    }
}
