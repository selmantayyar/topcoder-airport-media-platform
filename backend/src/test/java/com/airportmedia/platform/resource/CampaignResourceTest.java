package com.airportmedia.platform.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

/** The shared error contract, end to end: field-level 400s for bad drafts and a 404 shape for unknowns. */
@QuarkusTest
class CampaignResourceTest {

    @Test
    void anInvalidDraftIsRejectedWithFieldLevelErrors() {
        given().contentType(ContentType.JSON)
                .body("""
                        {"advertiserId": 1, "name": "", "budget": -5,
                         "scheduleStart": "2026-10-24T00:00:00Z", "scheduleEnd": "2026-10-23T00:00:00Z",
                         "frequencyCapPerScreen": 0}
                        """)
                .when().post("/api/campaigns")
                .then().statusCode(400)
                .body("status", equalTo(400))
                .body("fieldErrors.field",
                        hasItems("name", "budget", "frequencyCapPerScreen", "scheduleOrdered", "creativeProvided"));
    }

    @Test
    void malformedJsonIsA400InTheSharedShape() {
        given().contentType(ContentType.JSON).body("{\"advertiserId\": ")
                .when().post("/api/campaigns")
                .then().statusCode(400)
                .body("status", equalTo(400));
    }

    @Test
    void anUnknownCampaignIsA404InTheSharedShape() {
        given().when().post("/api/campaigns/999999/run-again")
                .then().statusCode(404)
                .body("status", equalTo(404))
                .body("error", equalTo("Not Found"));
    }

    @Test
    void recommendedSlotsAndTheCreativeAssistantAreRoutedAndValidated() {
        given().when().get("/api/campaigns/999999/recommended-slots")
                .then().statusCode(404)
                .body("status", equalTo(404));

        given().contentType(ContentType.JSON).body("{\"brief\": \"  \"}")
                .when().post("/api/retailers/1/creatives/assistant")
                .then().statusCode(400)
                .body("fieldErrors.field", hasItems("brief"));
    }
}
