package lab.order.domain;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository { void save(Order order); Optional<Order> find(UUID id); }
