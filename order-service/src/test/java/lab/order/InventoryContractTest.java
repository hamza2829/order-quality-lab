package lab.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import lab.order.application.*;
import lab.order.domain.*;
import lab.order.infrastructure.HttpInventoryGateway;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;

class InventoryContractTest {
    WireMockServer server;
    UUID id;
    HttpInventoryGateway client;
    final List<OrderLine> lines = List.of(new OrderLine(new Sku("BOOK"), new Quantity(2), new Money(new BigDecimal("12.50"), "EUR")));
    @BeforeEach void setup() {
        server = new WireMockServer(wireMockConfig().dynamicPort()); server.start(); id = UUID.randomUUID(); client = client(1000);
    }
    @AfterEach void stop() { server.stop(); }
    @Test void sendsProviderCompatibleRequestAndAccepts201() {
        server.stubFor(post("/api/reservations").withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(equalToJson(request())).willReturn(okJson(confirmation()).withStatus(201)));
        client.reserve(id, lines); server.verify(1, postRequestedFor(urlEqualTo("/api/reservations")).withRequestBody(equalToJson(request())));
    }
    @Test void businessRejectionNeverRetries() {
        server.stubFor(post("/api/reservations").willReturn(aResponse().withStatus(409).withBody("{\"code\":\"INSUFFICIENT_STOCK\"}")));
        assertThrows(StockUnavailable.class, () -> client.reserve(id, lines)); server.verify(1, postRequestedFor(urlEqualTo("/api/reservations")));
    }
    @Test void retriesServerFailureWithIdenticalReservationId() {
        server.stubFor(post("/api/reservations").inScenario("retry").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503)).willSetStateTo("recovered"));
        server.stubFor(post("/api/reservations").inScenario("retry").whenScenarioStateIs("recovered").willReturn(okJson(confirmation()).withStatus(201)));
        client.reserve(id, lines); server.verify(2, postRequestedFor(urlEqualTo("/api/reservations")).withRequestBody(equalToJson(request())));
    }
    @Test void exhaustsTwoServerFailureAttempts() {
        server.stubFor(post("/api/reservations").willReturn(aResponse().withStatus(503)));
        assertThrows(InventoryUnavailable.class, () -> client.reserve(id, lines)); server.verify(2, postRequestedFor(urlEqualTo("/api/reservations")));
    }
    @Test void boundsTimeoutAndRetriesOnce() {
        server.stubFor(post("/api/reservations").willReturn(okJson(confirmation()).withStatus(201).withFixedDelay(800)));
        long started = System.nanoTime();
        assertThrows(InventoryUnavailable.class, () -> client(150).reserve(id, lines));
        assertTrue(Duration.ofNanos(System.nanoTime() - started).toMillis() < 2500, "Two 150 ms requests must be bounded");
        server.verify(2, postRequestedFor(urlEqualTo("/api/reservations")));
    }
    @Test void malformedConfirmationCannotPlaceOrder() {
        server.stubFor(post("/api/reservations").willReturn(okJson("{\"status\":\"RESERVED\",\"reservationId\":\"wrong\"}").withStatus(201)));
        assertThrows(InventoryUnavailable.class, () -> client.reserve(id, lines)); server.verify(2, postRequestedFor(urlEqualTo("/api/reservations")));
    }
    @Test void unexpectedClientErrorDoesNotRetry() {
        server.stubFor(post("/api/reservations").willReturn(aResponse().withStatus(400)));
        assertThrows(InventoryUnavailable.class, () -> client.reserve(id, lines)); server.verify(1, postRequestedFor(urlEqualTo("/api/reservations")));
    }
    private HttpInventoryGateway client(long timeoutMs) { return new HttpInventoryGateway(server.baseUrl(), new ObjectMapper(), Duration.ofMillis(300), Duration.ofMillis(timeoutMs), 20); }
    private String confirmation() { return "{\"reservationId\":\"" + id + "\",\"status\":\"RESERVED\"}"; }
    private String request() { return "{\"reservationId\":\"" + id + "\",\"lines\":[{\"sku\":\"BOOK\",\"quantity\":2}]}"; }
}
