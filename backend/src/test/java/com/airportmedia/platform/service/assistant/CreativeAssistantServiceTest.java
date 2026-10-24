package com.airportmedia.platform.service.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import com.airportmedia.platform.adapter.genai.CreativeAssistantProvider;
import com.airportmedia.platform.adapter.genai.LlmCreativeAssistant;
import com.airportmedia.platform.adapter.genai.MockCreativeAssistant;
import com.airportmedia.platform.domain.entity.Advertiser;
import com.airportmedia.platform.domain.model.input.BriefFacts;
import com.airportmedia.platform.domain.model.output.AdCopy;
import com.airportmedia.platform.domain.model.output.AdCopySet;
import com.airportmedia.platform.domain.model.output.AdPage;
import com.airportmedia.platform.domain.model.output.CreativeSummary;
import com.airportmedia.platform.domain.model.output.GeneratedCreative;
import com.airportmedia.platform.service.CampaignService;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestTransaction
class CreativeAssistantServiceTest {

    private static final String BRIEF = "Festive sweets, 20% off, families flying domestic";

    @Inject
    CreativeAssistantService assistant;

    @Inject
    CampaignService campaigns;

    @Inject
    EntityManager em;

    private long retailerId() {
        return em.createQuery("from Advertiser where kind = 'RETAILER'", Advertiser.class)
                .setMaxResults(1).getSingleResult().id;
    }

    @Test
    void theOfflineDefaultTurnsABriefIntoEnglishAndSpanishPagesInTheLibrary() {
        long retailer = retailerId();

        GeneratedCreative result = assistant.generate(retailer, BRIEF);

        assertThat(result.provider()).isEqualTo("mock");
        assertThat(result.notes()).isEmpty();
        assertThat(result.pages()).extracting(AdPage::language).containsExactly("en", "es");
        assertThat(result.pages()).allMatch(p -> p.templateKey().equals("promo-basic") && p.headline().contains("20%"));

        List<CreativeSummary> library = campaigns.library(retailer);
        assertThat(library).extracting(CreativeSummary::id)
                .contains(result.pages().get(0).creativeId(), result.pages().get(1).creativeId());
    }

    @Test
    void llmCopyThatInventsAFactNeverReachesTheLibrary() {
        QuarkusMock.installMockForType(new CreativeAssistantProvider(
                new MockCreativeAssistant(), new HallucinatingAssistant(), "llm"), CreativeAssistantProvider.class);

        GeneratedCreative result = assistant.generate(retailerId(), BRIEF);

        assertThat(result.provider()).isEqualTo("mock");
        assertThat(result.notes()).singleElement().asString().startsWith("llm copy rejected").contains("cites 50");
        assertThat(result.pages()).noneMatch(p -> p.headline().contains("50"));
    }

    @Test
    void anUnreachableLlmFallsBackToTheOfflineAssistant() {
        QuarkusMock.installMockForType(new CreativeAssistantProvider(
                new MockCreativeAssistant(), new UnreachableAssistant(), "llm"), CreativeAssistantProvider.class);

        GeneratedCreative result = assistant.generate(retailerId(), BRIEF);

        assertThat(result.provider()).isEqualTo("mock");
        assertThat(result.notes()).singleElement().asString().contains("assistant unavailable");
        assertThat(result.pages()).hasSize(2);
    }

    private static final class HallucinatingAssistant extends LlmCreativeAssistant {
        HallucinatingAssistant() {
            super(null);
        }

        @Override
        public AdCopySet write(String brief, BriefFacts facts) {
            AdCopy copy = new AdCopy("Festive Sweets — 50% Off", "50% off festive sweets", "Today only.");
            return new AdCopySet(copy, copy);
        }
    }

    private static final class UnreachableAssistant extends LlmCreativeAssistant {
        UnreachableAssistant() {
            super(null);
        }

        @Override
        public AdCopySet write(String brief, BriefFacts facts) {
            throw new IllegalStateException("connection refused");
        }
    }
}
