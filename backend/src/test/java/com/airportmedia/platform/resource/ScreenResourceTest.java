package com.airportmedia.platform.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/** Every /api/screens route resolves — guards against a resource shadowing another's sub-paths. */
@QuarkusTest
class ScreenResourceTest {

    @Test
    void screenRoutesAllResolve() {
        long id = given().when().get("/api/screens").then().statusCode(200)
                .extract().jsonPath().getLong("[0].id");

        given().when().get("/api/screens/" + id + "/playback").then().statusCode(200).body("screen", notNullValue());
        given().when().get("/api/screens/" + id + "/decision").then().statusCode(200).body("candidates", notNullValue());
        given().when().get("/api/decisions").then().statusCode(200);
    }
}
