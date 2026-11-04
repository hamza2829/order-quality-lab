package lab.order.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import lab.order.application.*;
import lab.order.domain.OrderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.*;
import java.time.*;

@Configuration
public class OrderConfiguration {
    @Bean InventoryGateway inventoryGateway(@Value("${inventory.base-url}") String baseUrl, ObjectMapper mapper,
            @Value("${inventory.connect-timeout-ms}") long connectMs, @Value("${inventory.request-timeout-ms}") long requestMs,
            @Value("${inventory.retry-delay-ms}") long retryMs) {
        return new HttpInventoryGateway(baseUrl, mapper, Duration.ofMillis(connectMs), Duration.ofMillis(requestMs), retryMs);
    }
    @Bean PlaceOrderService placeOrderService(OrderRepository repository, InventoryGateway inventory, ApplicationEventPublisher publisher) {
        return new PlaceOrderService(repository, inventory, publisher::publishEvent, Clock.systemUTC());
    }
}
