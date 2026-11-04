package lab.order.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import lab.order.application.*;
import lab.order.domain.OrderLine;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

public final class HttpInventoryGateway implements InventoryGateway {
    private final HttpClient client;
    private final ObjectMapper mapper;
    private final URI endpoint;
    private final Duration timeout;
    private final long retryDelayMs;
    public HttpInventoryGateway(String baseUrl, ObjectMapper mapper, Duration connectTimeout, Duration timeout, long retryDelayMs) {
        this.client = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        this.mapper = mapper; this.endpoint = URI.create(baseUrl + "/api/reservations");
        this.timeout = timeout; this.retryDelayMs = retryDelayMs;
    }
    @Override public void reserve(UUID orderId, List<OrderLine> lines) {
        try {
            String json = mapper.writeValueAsString(Map.of("reservationId", orderId, "lines", lines.stream()
                    .map(line -> Map.of("sku", line.sku().value(), "quantity", line.quantity().value())).toList()));
            HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(timeout).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json)).build();
            for (int attempt = 0; attempt < 2; attempt++) {
                try {
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() == 409) throw new StockUnavailable("Inventory rejected the reservation");
                    if (response.statusCode() >= 500) throw new IOException("Inventory HTTP " + response.statusCode());
                    if (response.statusCode() != 201 && response.statusCode() != 200)
                        throw new InventoryUnavailable(new IOException("Unexpected inventory HTTP " + response.statusCode()));
                    var result = mapper.readTree(response.body());
                    if (!"RESERVED".equals(result.path("status").asText()) || !orderId.toString().equals(result.path("reservationId").asText()))
                        throw new IOException("Invalid inventory confirmation");
                    return;
                } catch (IOException e) {
                    if (attempt == 1) throw new InventoryUnavailable(e);
                    Thread.sleep(retryDelayMs);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new InventoryUnavailable(e);
        } catch (IOException e) {
            throw new InventoryUnavailable(e);
        }
    }
}
