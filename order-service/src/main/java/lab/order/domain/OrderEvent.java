package lab.order.domain;

import java.time.Instant;
import java.util.UUID;

public sealed interface OrderEvent permits OrderPlaced, OrderRejected {
    UUID orderId();
    Instant occurredAt();
}
