package lab.inventory.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lab.inventory.application.InventoryService;
import lab.inventory.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class InventoryController {
    private final InventoryService service;
    public InventoryController(InventoryService service) { this.service = service; }
    public record StockRequest(@Min(0) int available) { }
    public record StockView(String sku, int available) { }
    public record Line(@NotBlank String sku, @Positive int quantity) { }
    public record ReservationRequest(@NotNull UUID reservationId, @NotEmpty @Size(max = 100) List<@NotNull @Valid Line> lines) { }
    public record ReservationView(UUID reservationId, String status) { }
    @Operation(summary = "Set available stock", description = "Demo administration endpoint. Replaces the current available quantity.")
    @PutMapping("/stock/{sku}")
    public StockView set(@PathVariable String sku, @Valid @RequestBody StockRequest request) {
        StockItem item = service.setStock(new Sku(sku), request.available());
        return new StockView(item.sku().value(), item.available());
    }
    @Operation(summary = "Read available stock", description = "Unknown products have zero available stock.")
    @GetMapping("/stock/{sku}")
    public StockView get(@PathVariable String sku) {
        StockItem item = service.stock(new Sku(sku)); return new StockView(item.sku().value(), item.available());
    }
    @Operation(summary = "Reserve all lines atomically", description = "201 on success or exact replay. 409 if stock is insufficient or a reservation ID is reused for different lines. 400 for invalid input.")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "Reservation confirmed, including an exact replay"),
            @ApiResponse(responseCode = "400", description = "Invalid reservation"),
            @ApiResponse(responseCode = "409", description = "Insufficient stock or conflicting reservation ID")})
    @PostMapping("/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationView reserve(@Valid @RequestBody ReservationRequest request) {
        Reservation result = service.reserve(request.reservationId(), request.lines().stream()
                .map(line -> new StockLine(new Sku(line.sku()), new Quantity(line.quantity()))).toList());
        return new ReservationView(result.id(), "RESERVED");
    }
}
