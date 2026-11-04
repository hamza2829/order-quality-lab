package lab.inventory.domain;

import java.util.Objects;

public final class StockItem {
    private final Sku sku;
    private int available;
    public StockItem(Sku sku, int available) {
        this.sku = Objects.requireNonNull(sku);
        if (available < 0) throw new IllegalArgumentException("Stock cannot be negative");
        this.available = available;
    }
    public void reserve(Quantity quantity) {
        if (available < quantity.value()) throw new InsufficientStock("Insufficient stock for " + sku.value());
        available -= quantity.value();
    }
    public Sku sku() { return sku; }
    public int available() { return available; }
}
