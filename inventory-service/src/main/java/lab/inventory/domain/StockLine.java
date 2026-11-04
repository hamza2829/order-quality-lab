package lab.inventory.domain;

import java.util.Objects;

public record StockLine(Sku sku, Quantity quantity) {
    public StockLine { Objects.requireNonNull(sku); Objects.requireNonNull(quantity); }
}
