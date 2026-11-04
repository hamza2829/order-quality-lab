package lab.inventory.domain;

import java.util.Optional;
import java.util.UUID;

public interface InventoryRepository {
    Optional<StockItem> find(Sku sku);
    void save(StockItem item);
    Optional<Reservation> findReservation(UUID id);
    void saveReservation(Reservation reservation);
}
