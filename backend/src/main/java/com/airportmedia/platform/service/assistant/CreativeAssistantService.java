package com.airportmedia.platform.service.assistant;

import com.airportmedia.platform.adapter.genai.CreativeAssistant;
import com.airportmedia.platform.adapter.genai.CreativeAssistantProvider;
import com.airportmedia.platform.common.exception.FieldValidationException;
import com.airportmedia.platform.domain.entity.Advertiser;
import com.airportmedia.platform.domain.entity.Creative;
import com.airportmedia.platform.domain.model.input.BriefFacts;
import com.airportmedia.platform.domain.model.output.AdCopy;
import com.airportmedia.platform.domain.model.output.AdCopySet;
import com.airportmedia.platform.domain.model.output.AdPage;
import com.airportmedia.platform.domain.model.output.GeneratedCreative;
import com.airportmedia.platform.repository.AdvertiserRepository;
import com.airportmedia.platform.repository.CreativeRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jboss.logging.Logger;

/**
 * Brief in, ad page out: the active creative assistant writes English and Spanish copy, every page is held
 * to the {@link GroundingCheck}, and the result is saved to the retailer's creative library in the standard
 * template, ready to pick in the campaign builder. Copy that fails grounding (or an assistant that fails
 * outright) is replaced by the offline fallback's copy, with a note saying why — never saved as is.
 */
@ApplicationScoped
public class CreativeAssistantService {

    static final String TEMPLATE = "promo-basic";
    /** creative.name column length. */
    private static final int MAX_NAME = 128;
    private static final Logger LOG = Logger.getLogger(CreativeAssistantService.class);

    private final CreativeAssistantProvider provider;
    private final AdvertiserRepository advertisers;
    private final CreativeRepository creatives;

    public CreativeAssistantService(
            CreativeAssistantProvider provider, AdvertiserRepository advertisers, CreativeRepository creatives) {
        this.provider = provider;
        this.advertisers = advertisers;
        this.creatives = creatives;
    }

    /**
     * Not one transaction end to end: the assistant (possibly a slow remote model) runs outside any database
     * transaction, and only the accepted pages are saved in one.
     */
    public GeneratedCreative generate(long advertiserId, String brief) {
        if (advertisers.findByIdOptional(advertiserId).isEmpty()) {
            throw new NotFoundException("No advertiser " + advertiserId);
        }
        BriefFacts facts = BriefParser.parse(brief);
        List<String> notes = new ArrayList<>();

        Set<CreativeAssistant> assistants = new LinkedHashSet<>(List.of(provider.active(), provider.fallback()));
        for (CreativeAssistant assistant : assistants) {
            Attempt attempt = attempt(assistant, brief, facts);
            if (attempt.problems().isEmpty()) {
                List<AdPage> pages = QuarkusTransaction.joiningExisting().call(() -> {
                    Advertiser advertiser = advertisers.findById(advertiserId);
                    return List.of(
                            save(advertiser, facts, "en", attempt.copy().en(), "es", attempt.copy().es()),
                            save(advertiser, facts, "es", attempt.copy().es(), "en", attempt.copy().en()));
                });
                return new GeneratedCreative(brief, assistant.name(), pages, List.copyOf(notes));
            }
            String note = assistant.name() + " copy rejected: " + String.join("; ", attempt.problems());
            LOG.infof("Creative assistant brief '%s': %s", brief, note);
            notes.add(note);
        }
        throw FieldValidationException.of("brief", "Could not write a screen-ready ad from this brief: "
                + String.join(" | ", notes));
    }

    private record Attempt(AdCopySet copy, List<String> problems) {}

    private static Attempt attempt(CreativeAssistant assistant, String brief, BriefFacts facts) {
        AdCopySet copy;
        try {
            copy = assistant.write(brief, facts);
        } catch (RuntimeException e) {
            LOG.warnf(e, "Creative assistant '%s' failed", assistant.name());
            return new Attempt(null, List.of("assistant unavailable (" + e.getClass().getSimpleName() + ")"));
        }
        if (copy == null || copy.en() == null || copy.es() == null) {
            return new Attempt(copy, List.of("missing an English or Spanish version"));
        }
        List<String> problems = new ArrayList<>();
        GroundingCheck.problems(copy.en(), "en", facts).forEach(p -> problems.add("en: " + p));
        GroundingCheck.problems(copy.es(), "es", facts).forEach(p -> problems.add("es: " + p));
        return new Attempt(copy, problems);
    }

    /** One library page per language; each carries the other as its translation, so either renders both. */
    private AdPage save(Advertiser advertiser, BriefFacts facts, String language, AdCopy copy,
            String otherLanguage, AdCopy other) {
        Creative c = new Creative();
        c.advertiser = advertiser;
        String name = "Assistant: " + facts.product() + " (" + language.toUpperCase() + ")";
        c.name = name.length() > MAX_NAME ? name.substring(0, MAX_NAME) : name;
        c.templateKey = TEMPLATE;
        c.language = language;
        c.headline = copy.headline();
        c.offerText = copy.offerText();
        c.body = copy.body();
        c.translate(otherLanguage, other.headline(), other.offerText(), other.body());
        creatives.persist(c);
        return new AdPage(c.id, language, TEMPLATE, c.headline, c.offerText, c.body);
    }
}
