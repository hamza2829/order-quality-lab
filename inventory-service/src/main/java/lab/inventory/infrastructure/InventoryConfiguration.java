package lab.inventory.infrastructure;

import lab.inventory.application.InventoryService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;

@Configuration
public class InventoryConfiguration {
    @Bean InventoryService inventoryService(JdbcInventoryRepository repository, ApplicationEventPublisher publisher) {
        return new InventoryService(repository, repository, publisher::publishEvent, Clock.systemUTC());
    }
}
