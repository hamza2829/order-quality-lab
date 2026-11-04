package lab.order.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OrderDomainTest {
    @ParameterizedTest @ValueSource(strings = {"A", "BOOK-1", "ABC_123"})
    void acceptsSku(String sku) { assertEquals(sku, new Sku(sku).value()); }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"lower", "HAS SPACE", "?"})
    void rejectsSku(String sku) { assertThrows(IllegalArgumentException.class, () -> new Sku(sku)); }
    @ParameterizedTest @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void rejectsQuantity(int quantity) { assertThrows(IllegalArgumentException.class, () -> new Quantity(quantity)); }
    @Test void totalsLinesExactly() {
        Order order = new Order(UUID.randomUUID(), List.of(line("A", 3, "0.10"), line("B", 2, "2.25")));
        assertEquals(new Money(new BigDecimal("4.80"), "EUR"), order.total()); assertEquals(Order.Status.PENDING, order.status()); assertNull(order.reason());
    }
    @Test void moneyRejectsMixedCurrencies() { assertThrows(IllegalArgumentException.class, () -> money("1").add(new Money(BigDecimal.ONE, "USD"))); }
    @Test void moneyRejectsNegativePrice() { assertThrows(IllegalArgumentException.class, () -> money("-1")); }
    @Test void moneyRejectsFractionalCent() { assertThrows(ArithmeticException.class, () -> money("1.001")); }
    @Test void moneyRejectsUnknownCurrency() { assertThrows(IllegalArgumentException.class, () -> new Money(BigDecimal.ONE, "ZZZ")); }
    @Test void moneyRejectsNullAmount() { assertThrows(NullPointerException.class, () -> new Money(null, "EUR")); }
    @Test void orderRejectsMixedCurrencies() {
        assertThrows(IllegalArgumentException.class, () -> new Order(UUID.randomUUID(), List.of(line("A", 1, "1"),
                new OrderLine(new Sku("B"), new Quantity(1), new Money(BigDecimal.ONE, "USD")))));
    }
    @Test void emptyOrderIsInvalid() { assertThrows(IllegalArgumentException.class, () -> new Order(UUID.randomUUID(), List.of())); }
    @Test void nullOrderLinesAreInvalid() { assertThrows(IllegalArgumentException.class, () -> new Order(UUID.randomUUID(), null)); }
    @Test void tooManyLinesAreInvalid() { assertThrows(IllegalArgumentException.class, () -> new Order(UUID.randomUUID(), Collections.nCopies(101, line("A", 1, "1")))); }
    @Test void duplicateSkuIsInvalid() { assertThrows(IllegalArgumentException.class, () -> new Order(UUID.randomUUID(), List.of(line("A", 1, "1"), line("A", 2, "1")))); }
    @Test void placedOrderEmitsEventAndIsFinal() {
        Order order = order(); Instant now = Instant.now(); order.place(now);
        assertEquals(Order.Status.PLACED, order.status()); assertEquals(List.of(new OrderPlaced(order.id(), now)), order.events());
        assertThrows(IllegalStateException.class, () -> order.place(now)); assertThrows(IllegalStateException.class, () -> order.reject("NO_STOCK", now));
    }
    @Test void rejectedOrderEmitsEventAndIsFinal() {
        Order order = order(); Instant now = Instant.now(); order.reject("NO_STOCK", now);
        assertEquals(Order.Status.REJECTED, order.status()); assertEquals("NO_STOCK", order.reason());
        assertEquals(List.of(new OrderRejected(order.id(), "NO_STOCK", now)), order.events());
        assertThrows(IllegalStateException.class, () -> order.place(now));
    }
    @Test void linesAndEventsCannotBeMutated() {
        Order order = order(); assertThrows(UnsupportedOperationException.class, () -> order.lines().clear());
        assertThrows(UnsupportedOperationException.class, () -> order.events().clear());
    }
    @Test void restoresWithoutReplayingEvents() {
        Order original = order(); Order restored = Order.restore(original.id(), original.lines(), Order.Status.REJECTED, "NO_STOCK");
        assertEquals(original.id(), restored.id()); assertEquals(Order.Status.REJECTED, restored.status());
        assertEquals("NO_STOCK", restored.reason()); assertTrue(restored.events().isEmpty());
    }
    private Money money(String amount) { return new Money(new BigDecimal(amount), "EUR"); }
    private OrderLine line(String sku, int count, String amount) { return new OrderLine(new Sku(sku), new Quantity(count), money(amount)); }
    private Order order() { return new Order(UUID.randomUUID(), List.of(line("A", 1, "2.50"))); }
}
