package lab.inventory.domain;

import java.time.Instant;
import java.util.UUID;

public record StockReserved(UUID reservationId, Instant occurredAt) { }
