package lab.inventory.api;

import lab.inventory.domain.InsufficientStock;
import lab.inventory.domain.ReservationConflict;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiErrors {
    public record Error(String code, String message) { }
    @ExceptionHandler(InsufficientStock.class)
    ResponseEntity<Error> stock(InsufficientStock e) { return ResponseEntity.status(409).body(new Error("INSUFFICIENT_STOCK", e.getMessage())); }
    @ExceptionHandler(ReservationConflict.class)
    ResponseEntity<Error> conflict(ReservationConflict e) { return ResponseEntity.status(409).body(new Error("RESERVATION_CONFLICT", e.getMessage())); }
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<Error> invalid(Exception e) { return ResponseEntity.badRequest().body(new Error("INVALID_REQUEST", "Check SKU, quantity and request fields")); }
}
