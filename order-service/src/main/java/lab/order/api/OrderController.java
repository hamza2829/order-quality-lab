package lab.order.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lab.order.application.PlaceOrderService;
import lab.order.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.net.URI;
import java.util.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final PlaceOrderService service;
    public OrderController(PlaceOrderService service) { this.service = service; }
    public record Line(@NotBlank String sku, @Positive int quantity,
                       @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal unitPrice,
                       @NotBlank String currency) { }
    public record Request(@NotEmpty @Size(max = 100) List<@NotNull @Valid Line> lines) { }
    public record View(UUID id, String status, List<Line> lines, BigDecimal total, String currency, String code, String message) {
        static View of(Order order) {
            String message = switch (Objects.toString(order.reason(), "")) {
                case "INSUFFICIENT_STOCK" -> "One or more products do not have enough stock";
                case "INVENTORY_UNAVAILABLE" -> "Inventory did not confirm after two attempts. Reservation outcome may be unknown";
                default -> null;
            };
            return new View(order.id(), order.status().name(), order.lines().stream().map(line ->
                    new Line(line.sku().value(), line.quantity().value(), line.unitPrice().amount(), line.unitPrice().currency())).toList(),
                    order.total().amount(), order.total().currency(), order.reason(), message);
        }
    }
    @Operation(summary = "Place an order", description = "201 when stock is reserved. 409 for insufficient stock. 503 after inventory timeout or server failure with one retry. Rejected orders are saved and include an ID and error code. POST itself is not idempotent.")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "Order placed and stock reserved"),
            @ApiResponse(responseCode = "400", description = "Invalid order"),
            @ApiResponse(responseCode = "409", description = "Order rejected because stock is insufficient"),
            @ApiResponse(responseCode = "503", description = "Reservation was not confirmed after two attempts")})
    @PostMapping
    public ResponseEntity<View> place(@Valid @RequestBody Request request) {
        Order order = service.place(request.lines().stream().map(line -> new OrderLine(new Sku(line.sku()),
                new Quantity(line.quantity()), new Money(line.unitPrice(), line.currency()))).toList());
        return ResponseEntity.status(httpStatus(order)).location(URI.create("/api/orders/" + order.id())).body(View.of(order));
    }
    private static int httpStatus(Order order) {
        if (order.status() == Order.Status.PLACED) return 201;
        if ("INSUFFICIENT_STOCK".equals(order.reason())) return 409;
        return 503;
    }
    @Operation(summary = "Read a placed or rejected order", description = "404 if the order does not exist.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Saved order"), @ApiResponse(responseCode = "404", description = "Unknown order")})
    @GetMapping("/{id}")
    public ResponseEntity<View> get(@PathVariable UUID id) {
        return service.find(id).map(View::of).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
}
