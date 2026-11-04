package lab.order.application;

import lab.order.domain.OrderLine;
import java.util.List;
import java.util.UUID;

public interface InventoryGateway { void reserve(UUID orderId, List<OrderLine> lines); }
