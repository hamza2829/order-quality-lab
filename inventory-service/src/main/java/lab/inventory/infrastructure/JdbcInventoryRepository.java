package lab.inventory.infrastructure;

import lab.inventory.application.UnitOfWork;
import lab.inventory.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Repository
public class JdbcInventoryRepository implements InventoryRepository, UnitOfWork {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    public JdbcInventoryRepository(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc; this.transaction = new TransactionTemplate(manager);
    }
    // One process owns this in-memory database. Serialize reservations and stock updates together.
    @Override public synchronized <T> T execute(Supplier<T> action) { return transaction.execute(status -> action.get()); }
    @Override public Optional<StockItem> find(Sku sku) {
        return jdbc.query("SELECT available FROM stock WHERE sku = ?",
                (rs, row) -> new StockItem(sku, rs.getInt(1)), sku.value()).stream().findFirst();
    }
    @Override public void save(StockItem item) {
        jdbc.update("MERGE INTO stock (sku, available) KEY(sku) VALUES (?, ?)", item.sku().value(), item.available());
    }
    @Override public Optional<Reservation> findReservation(UUID id) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM reservations WHERE id = ?", Integer.class, id) == 0) return Optional.empty();
        return Optional.of(new Reservation(id, jdbc.query("SELECT sku, quantity FROM reservation_lines WHERE reservation_id = ?",
                (rs, row) -> new StockLine(new Sku(rs.getString(1)), new Quantity(rs.getInt(2))), id)));
    }
    @Override public void saveReservation(Reservation reservation) {
        jdbc.update("INSERT INTO reservations (id) VALUES (?)", reservation.id());
        reservation.lines().forEach(line -> jdbc.update("INSERT INTO reservation_lines VALUES (?, ?, ?)",
                reservation.id(), line.sku().value(), line.quantity().value()));
    }
}
