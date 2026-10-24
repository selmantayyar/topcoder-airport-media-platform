package com.airportmedia.platform.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

/** The admin API contract: overview shape, schema-driven master data, and its shared error responses. */
@QuarkusTest
class AdminResourceTest {

    @Test
    void overviewAndSchemasResolve() {
        given().when().get("/api/admin/overview").then().statusCode(200)
                .body("kpis.currency", equalTo("USD"))
                .body("terminals", notNullValue());
        given().when().get("/api/admin/master").then().statusCode(200)
                .body("type", hasItems("vendors", "outlets", "airlines", "locations", "screens"));
    }

    @Test
    void anInvalidMasterDataPayloadReturnsFieldErrors() {
        given().contentType(ContentType.JSON).body("{\"iataCode\": \"\", \"name\": \"\"}")
                .when().post("/api/admin/master/airlines")
                .then().statusCode(400)
                .body("fieldErrors.field", hasItems("iataCode", "icaoCode", "name"));
    }

    @Test
    void anUnknownTypeIsA404() {
        given().when().get("/api/admin/master/spaceships").then().statusCode(404).body("status", equalTo(404));
    }
}
