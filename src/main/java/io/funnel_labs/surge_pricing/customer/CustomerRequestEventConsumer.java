package io.funnel_labs.surge_pricing.customer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@KafkaListener(
  groupId = "SurgePricingService",
  topics = "CustomerRequestEvents"
)
@RequiredArgsConstructor
public class CustomerRequestEventConsumer {
  private final CustomerRequestAggregator customerRequestAggregator;

  @KafkaHandler
  public void consumeCustomerRequestEvent(CustomerRequestEvent event) {
    customerRequestAggregator.updateRequests(event);
  }
}
