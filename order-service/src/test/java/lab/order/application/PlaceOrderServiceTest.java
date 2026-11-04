package lab.order.application;

import lab.order.domain.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlaceOrderServiceTest {
    @Mock OrderRepository repository;
    @Mock InventoryGateway inventory;
    @Mock Consumer<OrderEvent> events;
    PlaceOrderService service;
    final Instant now = Instant.parse("2026-01-01T00:00:00Z");
    @BeforeEach void setup() { service = new PlaceOrderService(repository, inventory, events, Clock.fixed(now, ZoneOffset.UTC)); }
    @Test void placesAndSavesOrder() {
        Order order = service.place(lines()); assertEquals(Order.Status.PLACED, order.status());
        verify(inventory).reserve(order.id(), order.lines()); verify(repository).save(order); verify(events).accept(new OrderPlaced(order.id(), now));
    }
    @Test void rejectsAndSavesInsufficientStock() {
        doThrow(new StockUnavailable("No stock")).when(inventory).reserve(any(), any());
        Order order = service.place(lines()); assertEquals(Order.Status.REJECTED, order.status()); assertEquals("INSUFFICIENT_STOCK", order.reason());
        verify(repository).save(order); verify(events).accept(new OrderRejected(order.id(), "INSUFFICIENT_STOCK", now));
    }
    @Test void rejectsAndSavesUnavailableInventory() {
        doThrow(new InventoryUnavailable(new RuntimeException("Timeout"))).when(inventory).reserve(any(), any());
        Order order = service.place(lines()); assertEquals(Order.Status.REJECTED, order.status()); assertEquals("INVENTORY_UNAVAILABLE", order.reason());
        verify(repository).save(order); verify(events).accept(new OrderRejected(order.id(), "INVENTORY_UNAVAILABLE", now));
    }
    @Test void invalidOrderNeverCallsInventory() {
        assertThrows(IllegalArgumentException.class, () -> service.place(List.of())); verifyNoInteractions(inventory, repository, events);
    }
    @Test void readsOrder() {
        Order order = new Order(UUID.randomUUID(), lines()); when(repository.find(order.id())).thenReturn(Optional.of(order));
        assertEquals(Optional.of(order), service.find(order.id()));
    }
    @Test void missingOrderIsEmpty() { assertTrue(service.find(UUID.randomUUID()).isEmpty()); }
    @Test void failedPersistenceDoesNotPublish() {
        doThrow(new IllegalStateException("Database failed")).when(repository).save(any());
        assertThrows(IllegalStateException.class, () -> service.place(lines())); verifyNoInteractions(events);
    }
    private List<OrderLine> lines() { return List.of(new OrderLine(new Sku("BOOK"), new Quantity(2), new Money(new BigDecimal("12.50"), "EUR"))); }
}
