package lab.order.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

public record Money(BigDecimal amount, String currency) {
    public Money {
        Objects.requireNonNull(amount, "Amount is required");
        Currency.getInstance(currency);
        if (amount.signum() < 0) throw new IllegalArgumentException("Price cannot be negative");
        amount = amount.setScale(2, RoundingMode.UNNECESSARY);
    }
    public Money multiply(Quantity quantity) { return new Money(amount.multiply(BigDecimal.valueOf(quantity.value())), currency); }
    public Money add(Money other) {
        if (!currency.equals(other.currency)) throw new IllegalArgumentException("Currencies must match");
        return new Money(amount.add(other.amount), currency);
    }
}
