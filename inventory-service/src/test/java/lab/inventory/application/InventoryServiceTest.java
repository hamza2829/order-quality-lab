package lab.inventory.application;

import lab.inventory.domain.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.*;
import java.util.*;
import java.util.function.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {
    @Mock InventoryRepository repository;
    @Mock Consumer<StockReserved> events;
    InventoryService service;
    final Sku sku = new Sku("BOOK");
    final UUID id = UUID.randomUUID();
    final Instant now = Instant.parse("2026-01-01T00:00:00Z");
    @BeforeEach void setup() {
        UnitOfWork unit = new UnitOfWork() { public <T> T execute(Supplier<T> action) { return action.get(); } };
        service = new InventoryService(repository, unit, events, Clock.fixed(now, ZoneOffset.UTC));
    }
    @Test void setsStock() { assertEquals(7, service.setStock(sku, 7).available()); verify(repository).save(any(StockItem.class)); }
    @Test void readsExistingStock() { when(repository.find(sku)).thenReturn(Optional.of(new StockItem(sku, 3))); assertEquals(3, service.stock(sku).available()); }
    @Test void missingStockIsZero() { assertEquals(0, service.stock(sku).available()); }
    @Test void reservesAndPublishes() {
        when(repository.find(sku)).thenReturn(Optional.of(new StockItem(sku, 5)));
        assertEquals(id, service.reserve(id, lines()).id());
        ArgumentCaptor<StockItem> stock = ArgumentCaptor.forClass(StockItem.class);
        verify(repository).save(stock.capture()); assertEquals(3, stock.getValue().available());
        verify(repository).saveReservation(new Reservation(id, lines())); verify(events).accept(new StockReserved(id, now));
    }
    @Test void exactReplayDoesNotSaveOrPublish() {
        Reservation existing = new Reservation(id, lines()); when(repository.findReservation(id)).thenReturn(Optional.of(existing));
        assertEquals(existing, service.reserve(id, lines())); verify(repository, never()).save(any()); verifyNoInteractions(events);
    }
    @Test void changedReplayConflicts() {
        when(repository.findReservation(id)).thenReturn(Optional.of(new Reservation(id, lines())));
        assertThrows(ReservationConflict.class, () -> service.reserve(id, List.of(new StockLine(sku, new Quantity(3)))));
        verify(repository, never()).save(any()); verifyNoInteractions(events);
    }
    @Test void insufficientStockDoesNotSave() {
        when(repository.find(sku)).thenReturn(Optional.of(new StockItem(sku, 1)));
        assertThrows(InsufficientStock.class, () -> service.reserve(id, lines())); verify(repository, never()).save(any()); verifyNoInteractions(events);
    }
    @Test void unknownSkuDoesNotSave() {
        assertThrows(InsufficientStock.class, () -> service.reserve(id, lines())); verify(repository, never()).saveReservation(any());
    }
    private List<StockLine> lines() { return List.of(new StockLine(sku, new Quantity(2))); }
}
