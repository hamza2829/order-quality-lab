package lab.order.application;

public class InventoryUnavailable extends RuntimeException {
    public InventoryUnavailable(Throwable cause) { super("Inventory did not confirm the reservation", cause); }
}
