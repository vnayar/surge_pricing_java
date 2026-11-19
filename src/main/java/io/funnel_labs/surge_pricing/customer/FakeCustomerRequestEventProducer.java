package io.funnel_labs.surge_pricing.customer;

import io.funnel_labs.surge_pricing.Location;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.Random;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/// Publishes CustomerRequestEvent messages to Kafka as test data.
@Slf4j
@Service
@RequiredArgsConstructor
public class FakeCustomerRequestEventProducer {
  private static final double MIN_LONGITUDE = 13.276805;
  private static final double MIN_LATITUDE = 52.448121;
  private static final double MAX_LONGITUDE = 13.499795;
  private static final double MAX_LATITUDE = 52.553088;

  private final KafkaTemplate<Long, CustomerRequestEvent> kafkaTemplate;

  public Random random;

  @PostConstruct
  public void init() {
    this.random = new Random();
  }

  @Scheduled(fixedRate = 5_000)
  public void produceFakeCustomerRequests() {
    IntStream.range(0, 300).forEach((int i) -> {
          if (random.nextDouble() < 0.35) {
            CustomerRequestEvent event = CustomerRequestEvent.builder()
                .customerId(i)
                .timestampInMs(Instant.now().toEpochMilli())
                .location(Location.builder()
                    .latitude((float)(MIN_LATITUDE + (MAX_LATITUDE - MIN_LATITUDE) * random.nextDouble()))
                    .longitude((float)(MIN_LONGITUDE + (MAX_LONGITUDE - MIN_LONGITUDE) * random.nextDouble()))
                    .build())
                .build();

            kafkaTemplate.send("CustomerRequestEvents", event.getCustomerId(), event);
          }
        });
  }
}
