package lab.order.domain;

import java.time.Instant;
import java.util.*;

public final class Order {
    public enum Status { PENDING, PLACED, REJECTED }
    private final UUID id;
    private final List<OrderLine> lines;
    private final Money total;
    private Status status;
    private String reason;
    private final List<OrderEvent> events = new ArrayList<>();
    public Order(UUID id, List<OrderLine> lines) {
        this.id = Objects.requireNonNull(id);
        if (lines == null || lines.isEmpty() || lines.size() > 100) throw new IllegalArgumentException("Order needs 1 to 100 lines");
        this.lines = List.copyOf(lines);
        if (lines.stream().map(OrderLine::sku).distinct().count() != lines.size()) throw new IllegalArgumentException("Duplicate SKU");
        total = lines.stream().map(OrderLine::total).reduce(Money::add).orElseThrow();
        status = Status.PENDING;
    }
    public static Order restore(UUID id, List<OrderLine> lines, Status status, String reason) {
        Order order = new Order(id, lines); order.status = status; order.reason = reason; return order;
    }
    public void place(Instant now) {
        requirePending(); status = Status.PLACED; events.add(new OrderPlaced(id, now));
    }
    public void reject(String rejectionReason, Instant now) {
        requirePending(); reason = Objects.requireNonNull(rejectionReason); status = Status.REJECTED;
        events.add(new OrderRejected(id, reason, now));
    }
    private void requirePending() { if (status != Status.PENDING) throw new IllegalStateException("Order is already final"); }
    public UUID id() { return id; }
    public List<OrderLine> lines() { return lines; }
    public Money total() { return total; }
    public Status status() { return status; }
    public String reason() { return reason; }
    public List<OrderEvent> events() { return List.copyOf(events); }
}
