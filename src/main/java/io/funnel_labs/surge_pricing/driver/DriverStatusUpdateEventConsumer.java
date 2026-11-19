package io.funnel_labs.surge_pricing.driver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@KafkaListener(
    groupId = "SurgePricingService",
    topics = "DriverStatusUpdateEvents"
)
@RequiredArgsConstructor
public class DriverStatusUpdateEventConsumer {
  private final DriverStatusAggregator driverStatusAggregator;

  @KafkaHandler
  public void consumeDriverStatusUpdateEvent(DriverStatusUpdateEvent event) {
    driverStatusAggregator.updateDriver(event);
  }
}
