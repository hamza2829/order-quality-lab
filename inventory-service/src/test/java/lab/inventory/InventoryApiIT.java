package lab.inventory;

import lab.inventory.application.InventoryService;
import lab.inventory.domain.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class InventoryApiIT {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired InventoryService service;
    @BeforeEach void clean() {
        jdbc.update("DELETE FROM reservation_lines"); jdbc.update("DELETE FROM reservations"); jdbc.update("DELETE FROM stock");
    }
    @Test void stockRoundTrip() throws Exception {
        stock("BOOK", 10); mvc.perform(get("/api/stock/BOOK")).andExpect(status().isOk()).andExpect(jsonPath("$.available").value(10));
    }
    @Test void unknownStockIsZero() throws Exception { mvc.perform(get("/api/stock/UNKNOWN")).andExpect(status().isOk()).andExpect(jsonPath("$.available").value(0)); }
    @Test void providerContractReservesAndReplays() throws Exception {
        stock("BOOK", 10); String body = reservation(UUID.randomUUID(), "BOOK", 3);
        for (int i = 0; i < 2; i++) mvc.perform(post("/api/reservations").contentType("application/json").content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("RESERVED")).andExpect(jsonPath("$.reservationId").exists());
        mvc.perform(get("/api/stock/BOOK")).andExpect(jsonPath("$.available").value(7));
    }
    @Test void rejectsInsufficientStock() throws Exception {
        stock("BOOK", 1); mvc.perform(post("/api/reservations").contentType("application/json").content(reservation(UUID.randomUUID(), "BOOK", 2)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
        mvc.perform(get("/api/stock/BOOK")).andExpect(jsonPath("$.available").value(1));
    }
    @Test void failedMultilineReservationChangesNothing() throws Exception {
        stock("A", 10); stock("B", 1);
        String body = "{\"reservationId\":\"" + UUID.randomUUID() + "\",\"lines\":[{\"sku\":\"A\",\"quantity\":2},{\"sku\":\"B\",\"quantity\":2}]}";
        mvc.perform(post("/api/reservations").contentType("application/json").content(body)).andExpect(status().isConflict());
        assertEquals(10, service.stock(new Sku("A")).available()); assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM reservations", Integer.class));
    }
    @Test void changedReplayConflicts() throws Exception {
        stock("BOOK", 10); UUID id = UUID.randomUUID();
        mvc.perform(post("/api/reservations").contentType("application/json").content(reservation(id, "BOOK", 2))).andExpect(status().isCreated());
        mvc.perform(post("/api/reservations").contentType("application/json").content(reservation(id, "BOOK", 3)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESERVATION_CONFLICT"));
        assertEquals(8, service.stock(new Sku("BOOK")).available());
    }
    @Test void invalidInputIs400() throws Exception {
        mvc.perform(put("/api/stock/BOOK").contentType("application/json").content("{\"available\":-1}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/reservations").contentType("application/json").content(reservation(UUID.randomUUID(), "BOOK", 0)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/api/stock/lower")).andExpect(status().isBadRequest());
    }
    @Test void concurrentReservationsNeverOversell() throws Exception {
        stock("BOOK", 10); ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<Boolean>> attempts = new ArrayList<>();
            for (int i = 0; i < 20; i++) attempts.add(pool.submit(() -> {
                try { service.reserve(UUID.randomUUID(), List.of(new StockLine(new Sku("BOOK"), new Quantity(1)))); return true; }
                catch (InsufficientStock e) { return false; }
            }));
            int succeeded = 0; for (Future<Boolean> attempt : attempts) if (attempt.get(10, TimeUnit.SECONDS)) succeeded++;
            assertEquals(10, succeeded); assertEquals(0, service.stock(new Sku("BOOK")).available());
        } finally { pool.shutdownNow(); }
    }
    @Test void concurrentIdenticalReservationsDeductOnce() throws Exception {
        stock("BOOK", 10); UUID id = UUID.randomUUID(); ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Reservation>> calls = Collections.nCopies(8, () -> service.reserve(id, List.of(new StockLine(new Sku("BOOK"), new Quantity(2)))));
            for (Future<Reservation> future : pool.invokeAll(calls)) assertEquals(id, future.get().id());
            assertEquals(8, service.stock(new Sku("BOOK")).available());
        } finally { pool.shutdownNow(); }
    }
    @Test void exposesHealthAndOpenApi() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andExpect(jsonPath("$.paths['/api/reservations'].post").exists());
    }
    private void stock(String sku, int count) throws Exception { mvc.perform(put("/api/stock/" + sku).contentType("application/json").content("{\"available\":" + count + "}")).andExpect(status().isOk()); }
    private String reservation(UUID id, String sku, int count) { return "{\"reservationId\":\"" + id + "\",\"lines\":[{\"sku\":\"" + sku + "\",\"quantity\":" + count + "}]}"; }
}
