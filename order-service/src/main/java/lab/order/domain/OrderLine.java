package lab.order.domain;

import java.util.Objects;

public record OrderLine(Sku sku, Quantity quantity, Money unitPrice) {
    public OrderLine { Objects.requireNonNull(sku); Objects.requireNonNull(quantity); Objects.requireNonNull(unitPrice); }
    public Money total() { return unitPrice.multiply(quantity); }
}
