package lab.order.application;

import lab.order.domain.*;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public final class PlaceOrderService {
    private final OrderRepository repository;
    private final InventoryGateway inventory;
    private final Consumer<OrderEvent> events;
    private final Clock clock;
    public PlaceOrderService(OrderRepository repository, InventoryGateway inventory, Consumer<OrderEvent> events, Clock clock) {
        this.repository = repository; this.inventory = inventory; this.events = events; this.clock = clock;
    }
    public Order place(List<OrderLine> lines) {
        Order order = new Order(UUID.randomUUID(), lines);
        try {
            inventory.reserve(order.id(), order.lines());
            order.place(Instant.now(clock));
        } catch (StockUnavailable e) {
            order.reject("INSUFFICIENT_STOCK", Instant.now(clock));
        } catch (InventoryUnavailable e) {
            order.reject("INVENTORY_UNAVAILABLE", Instant.now(clock));
        }
        repository.save(order);
        order.events().forEach(events);
        return order;
    }
    public Optional<Order> find(UUID id) { return repository.find(id); }
}
