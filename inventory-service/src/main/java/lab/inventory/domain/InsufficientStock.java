package lab.inventory.domain;

public class InsufficientStock extends RuntimeException {
    public InsufficientStock(String message) { super(message); }
}
