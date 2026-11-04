package lab.order.api;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiErrors {
    public record Error(String code, String message) { }
    @ExceptionHandler({IllegalArgumentException.class, ArithmeticException.class, MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<Error> invalid(Exception e) { return ResponseEntity.badRequest().body(new Error("INVALID_REQUEST", "Check SKU, quantity, money, currency and unique order lines")); }
}
