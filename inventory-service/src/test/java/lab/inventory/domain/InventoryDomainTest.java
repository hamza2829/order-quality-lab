package lab.inventory.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class InventoryDomainTest {
    @ParameterizedTest @ValueSource(strings = {"A", "BOOK-1", "ABC_123"})
    void acceptsSku(String sku) { assertEquals(sku, new Sku(sku).value()); }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"lower", "HAS SPACE", "?", "ABCDEFGHIJKLMNOPQRSTUVWXYZ12345678901234567890"})
    void rejectsSku(String sku) { assertThrows(IllegalArgumentException.class, () -> new Sku(sku)); }
    @ParameterizedTest @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void rejectsQuantity(int quantity) { assertThrows(IllegalArgumentException.class, () -> new Quantity(quantity)); }
    @Test void reservesStock() {
        StockItem stock = new StockItem(new Sku("A"), 5);
        stock.reserve(new Quantity(3)); assertEquals(2, stock.available()); assertEquals(new Sku("A"), stock.sku());
    }
    @Test void cannotOversell() {
        StockItem stock = new StockItem(new Sku("A"), 1);
        assertThrows(InsufficientStock.class, () -> stock.reserve(new Quantity(2))); assertEquals(1, stock.available());
    }
    @Test void rejectsNegativeStock() { assertThrows(IllegalArgumentException.class, () -> new StockItem(new Sku("A"), -1)); }
    @Test void normalizesReservationLineOrder() {
        UUID id = UUID.randomUUID(); var a = line("A"); var b = line("B");
        assertEquals(new Reservation(id, List.of(a, b)), new Reservation(id, List.of(b, a)));
    }
    @Test void rejectsDuplicateLines() { assertThrows(IllegalArgumentException.class, () -> new Reservation(UUID.randomUUID(), List.of(line("A"), line("A")))); }
    @Test void rejectsEmptyReservation() { assertThrows(IllegalArgumentException.class, () -> new Reservation(UUID.randomUUID(), List.of())); }
    @Test void rejectsNullReservation() { assertThrows(IllegalArgumentException.class, () -> new Reservation(UUID.randomUUID(), null)); }
    @Test void rejectsTooManyLines() { assertThrows(IllegalArgumentException.class, () -> new Reservation(UUID.randomUUID(), Collections.nCopies(101, line("A")))); }
    private StockLine line(String sku) { return new StockLine(new Sku(sku), new Quantity(1)); }
}
