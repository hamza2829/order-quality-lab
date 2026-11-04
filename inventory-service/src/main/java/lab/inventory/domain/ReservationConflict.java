package lab.inventory.domain;

public class ReservationConflict extends RuntimeException {
    public ReservationConflict() { super("Reservation ID already belongs to different lines"); }
}
