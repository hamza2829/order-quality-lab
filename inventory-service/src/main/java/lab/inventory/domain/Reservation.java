package lab.inventory.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record Reservation(UUID id, List<StockLine> lines) {
    public Reservation {
        Objects.requireNonNull(id);
        if (lines == null || lines.isEmpty() || lines.size() > 100) throw new IllegalArgumentException("Reservation needs 1 to 100 lines");
        lines = lines.stream().sorted(Comparator.comparing(line -> line.sku().value())).toList();
        if (lines.stream().map(StockLine::sku).distinct().count() != lines.size()) throw new IllegalArgumentException("Duplicate SKU");
    }
}
