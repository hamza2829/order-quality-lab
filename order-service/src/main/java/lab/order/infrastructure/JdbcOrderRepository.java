package lab.order.infrastructure;

import lab.order.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcOrderRepository implements OrderRepository {
    private final JdbcTemplate jdbc;
    public JdbcOrderRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override @Transactional public void save(Order order) {
        jdbc.update("INSERT INTO orders VALUES (?, ?, ?)", order.id(), order.status().name(), order.reason());
        for (int i = 0; i < order.lines().size(); i++) {
            OrderLine line = order.lines().get(i);
            jdbc.update("INSERT INTO order_lines VALUES (?, ?, ?, ?, ?, ?)", order.id(), i, line.sku().value(),
                    line.quantity().value(), line.unitPrice().amount(), line.unitPrice().currency());
        }
    }
    @Override @Transactional(readOnly = true) public Optional<Order> find(UUID id) {
        return jdbc.query("SELECT status, reason FROM orders WHERE id = ?", (rs, row) ->
                Order.restore(id, jdbc.query("SELECT * FROM order_lines WHERE order_id = ? ORDER BY position",
                        (line, index) -> new OrderLine(new Sku(line.getString("sku")), new Quantity(line.getInt("quantity")),
                                new Money(line.getBigDecimal("amount"), line.getString("currency"))), id),
                        Order.Status.valueOf(rs.getString("status")), rs.getString("reason")), id).stream().findFirst();
    }
}
