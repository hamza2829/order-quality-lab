package lab.inventory.application;

import lab.inventory.domain.*;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public final class InventoryService {
    private final InventoryRepository repository;
    private final UnitOfWork unitOfWork;
    private final Consumer<StockReserved> events;
    private final Clock clock;
    public InventoryService(InventoryRepository repository, UnitOfWork unitOfWork, Consumer<StockReserved> events, Clock clock) {
        this.repository = repository; this.unitOfWork = unitOfWork; this.events = events; this.clock = clock;
    }
    public StockItem setStock(Sku sku, int available) {
        StockItem item = new StockItem(sku, available);
        return unitOfWork.execute(() -> { repository.save(item); return item; });
    }
    public StockItem stock(Sku sku) {
        return unitOfWork.execute(() -> repository.find(sku).orElse(new StockItem(sku, 0)));
    }
    public Reservation reserve(UUID id, List<StockLine> lines) {
        Reservation request = new Reservation(id, lines);
        return unitOfWork.execute(() -> {
            var existing = repository.findReservation(id);
            if (existing.isPresent()) {
                if (!existing.get().equals(request)) throw new ReservationConflict();
                return existing.get();
            }
            // Validate all lines before saving any stock. The unit of work also rolls back failures.
            List<StockItem> items = request.lines().stream().map(line -> {
                StockItem item = repository.find(line.sku()).orElse(new StockItem(line.sku(), 0));
                item.reserve(line.quantity());
                return item;
            }).toList();
            items.forEach(repository::save);
            repository.saveReservation(request);
            events.accept(new StockReserved(id, Instant.now(clock)));
            return request;
        });
    }
}
