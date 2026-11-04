package lab.e2e;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import java.util.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

class OrderFlowE2ETest {
    final String orders = System.getenv().getOrDefault("ORDER_URL", "http://localhost:8080");
    final String inventory = System.getenv().getOrDefault("INVENTORY_URL", "http://localhost:8081");
    @Test void healthyServicesPublishOpenApi() {
        for (String service : List.of(orders, inventory)) {
            given().get(service + "/actuator/health").then().statusCode(200).body("status", equalTo("UP"));
            given().get(service + "/v3/api-docs").then().statusCode(200).body("openapi", startsWith("3."));
        }
    }
    @Test void placesOrderAndDeductsStock() {
        seed("E2E-OK", 10);
        String id = given().contentType(ContentType.JSON).body(order("E2E-OK", 3)).post(orders + "/api/orders")
                .then().statusCode(201).body("status", equalTo("PLACED")).body("total", equalTo(37.5f)).extract().path("id");
        given().get(orders + "/api/orders/" + id).then().statusCode(200).body("lines[0].sku", equalTo("E2E-OK"));
        given().get(inventory + "/api/stock/E2E-OK").then().statusCode(200).body("available", equalTo(7));
    }
    @Test void rejectsOrderAndLeavesStockUnchanged() {
        seed("E2E-LOW", 1);
        String id = given().contentType(ContentType.JSON).body(order("E2E-LOW", 2)).post(orders + "/api/orders")
                .then().statusCode(409).body("status", equalTo("REJECTED")).body("code", equalTo("INSUFFICIENT_STOCK")).extract().path("id");
        given().get(orders + "/api/orders/" + id).then().statusCode(200).body("status", equalTo("REJECTED"));
        given().get(inventory + "/api/stock/E2E-LOW").then().body("available", equalTo(1));
    }
    @Test void internalReservationReplayDeductsOnce() {
        seed("E2E-REPLAY", 10);
        Map<String, Object> body = Map.of("reservationId", UUID.randomUUID().toString(), "lines", List.of(Map.of("sku", "E2E-REPLAY", "quantity", 2)));
        for (int i = 0; i < 2; i++) given().contentType(ContentType.JSON).body(body).post(inventory + "/api/reservations").then().statusCode(201);
        given().get(inventory + "/api/stock/E2E-REPLAY").then().body("available", equalTo(8));
    }
    @Test void multilineRejectionDoesNotPartiallyReserve() {
        seed("E2E-A", 10); seed("E2E-B", 0);
        var lines = List.of(line("E2E-A", 2), line("E2E-B", 2));
        given().contentType(ContentType.JSON).body(Map.of("lines", lines)).post(orders + "/api/orders").then().statusCode(409);
        given().get(inventory + "/api/stock/E2E-A").then().body("available", equalTo(10));
    }
    private void seed(String sku, int count) { given().contentType(ContentType.JSON).body(Map.of("available", count)).put(inventory + "/api/stock/" + sku).then().statusCode(200); }
    private Map<String, Object> order(String sku, int count) { return Map.of("lines", List.of(line(sku, count))); }
    private Map<String, Object> line(String sku, int count) { return Map.of("sku", sku, "quantity", count, "unitPrice", 12.50, "currency", "EUR"); }
}
