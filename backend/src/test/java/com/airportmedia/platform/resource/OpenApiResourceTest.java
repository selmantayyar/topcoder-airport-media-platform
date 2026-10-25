package com.airportmedia.platform.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/** The REST API's OpenAPI spec and its browsable UI are served under /api, so nginx exposes them with the app. */
@QuarkusTest
class OpenApiResourceTest {

    @Test
    void theSpecDescribesTheRestApi() {
        given().when().get("/api/openapi?format=json").then().statusCode(200)
                .body(containsString("Airport Retail Media Platform API"))
                .body(containsString("/api/screens/{id}/playback"))
                .body(containsString("/api/promo/{code}/claim"));
    }

    @Test
    void theSwaggerUiIsServed() {
        given().when().get("/api/docs/").then().statusCode(200).body(containsString("swagger"));
    }
}
