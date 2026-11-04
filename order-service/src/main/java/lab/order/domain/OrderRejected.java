package lab.order.domain;

import java.time.Instant;
import java.util.UUID;

public record OrderRejected(UUID orderId, String reason, Instant occurredAt) implements OrderEvent { }
