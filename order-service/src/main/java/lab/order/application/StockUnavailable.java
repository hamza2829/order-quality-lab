package lab.order.application;

public class StockUnavailable extends RuntimeException {
    public StockUnavailable(String message) { super(message); }
}
