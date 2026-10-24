package com.airportmedia.platform.domain.model.input;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** The retailer's own copy for a creative in a second language, e.g. Spanish next to the English original. */
public record CopyTranslationDraft(
        @NotBlank @Pattern(regexp = "[a-zA-Z]{2}", message = "must be a two-letter language code") String language,
        @NotBlank @Size(max = 200) String headline,
        @Size(max = 200) String offerText,
        @Size(max = 1000) String body) {}
