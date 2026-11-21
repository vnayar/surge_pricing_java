package io.funnel_labs.surge_pricing;

import static org.assertj.core.api.Assertions.*;

import io.funnel_labs.surge_pricing.customer.CustomerRequestEvent;
import io.funnel_labs.surge_pricing.driver.DriverStatusUpdateEvent;
import io.funnel_labs.surge_pricing.surge.SurgePriceComputer;
import io.funnel_labs.surge_pricing.surge.SurgePriceMultipliersRequest;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;
import org.springframework.test.web.servlet.client.assertj.RestTestClientResponse;

// A full integration test that loads all SpringBoot and Application beans.
@SpringBootTest
@EmbeddedKafka(
    controlledShutdown=true,
    //brokerProperties={"log.dir=target/embedded-kafka-logs/${random.uuid}"},
    topics={ "DriverStatusUpdateEvents", "CustomerRequestEvents" })
@DirtiesContext // Wait for a clean shutdown of Embedded-Kafka.
@AutoConfigureRestTestClient
public class SurgePricingApplicationTests {

  @Autowired
  private KafkaTemplate<Long, CustomerRequestEvent> customerKafkaTemplate;

  @Autowired
  private KafkaTemplate<Long, DriverStatusUpdateEvent> driverKafkaTemplate;

  @Autowired
  private SurgePriceComputer surgePriceComputer;

  @Autowired
  private KafkaListenerEndpointRegistry registry;

  @Autowired
  private EmbeddedKafkaBroker embeddedKafkaBroker;

  @BeforeEach
  void setUp() {
    for (MessageListenerContainer container : registry.getListenerContainers()) {
      ContainerTestUtils.waitForAssignment(container, embeddedKafkaBroker.getPartitionsPerTopic());
    }
  }

	@Test
	public void contextLoads() {
	}

  // A course end-to-end test to make sure the application is working.
  // See: https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html#testing.spring-boot-applications.with-mock-environment
  @Test
  public void driverAndCustomerEventsChangeMultipliers(@Autowired RestTestClient restClient)
      throws InterruptedException {

    // Setup //

    // First create some drivers and customer requests via Kafka.
    IntStream.range(0, 3).forEach((int i) -> {
      CustomerRequestEvent event = CustomerRequestEvent.builder()
          .customerId(10 + i)
          .timestampInMs(Instant.now().toEpochMilli())
          .location(Location.builder()
              // Match cell "881f1d4993fffff"
              .latitude(52.4853900f)
              .longitude(13.3537659f)
              .build())
          .build();
      customerKafkaTemplate.send("CustomerRequestEvents", event.getCustomerId(), event)
          .join();
    });

    DriverStatusUpdateEvent driverStatusUpdateEvent = DriverStatusUpdateEvent.builder()
        .driverId(100)
        .timestampInMs(Instant.now().toEpochMilli())
        .location(Location.builder()
            // Match cell "881f1d4993fffff"
            .latitude(52.4853900f)
            .longitude(13.3537659f)
            .speed(44.7f) // 100 mi/hr = 44.7 m/s
            .bearing(180)
            .build())
        .available(true)
        .build();
    driverKafkaTemplate.send(
        "DriverStatusUpdateEvents", driverStatusUpdateEvent.getDriverId(), driverStatusUpdateEvent)
        .join();

    // Sleep for the maximum allowed time in ms for aggregators to process new updates.
    Thread.sleep(1000);

    // Triger an update immediately rather than waiting 30 seconds.
    System.out.println("BEFORE");
    surgePriceComputer.computeSurgePriceMultipliers();
    System.out.println("AFTER");

    // Test //

		ResponseSpec spec = restClient.post()
        .uri("/api/surge-price-multipliers")
        .header(HttpHeaders.CONTENT_TYPE, "application/json")
        .body(SurgePriceMultipliersRequest.builder()
            .h3Cells(List.of("881f1d4993fffff"))
            .build())
        .exchange();

    // Validate //

		RestTestClientResponse response = RestTestClientResponse.from(spec);
		assertThat(response).hasStatusOk()
        .bodyJson()
        .extractingPath("$.881f1d4993fffff").asNumber()
        .satisfies(num -> assertThat(num.doubleValue()).isGreaterThan(1.0));
	}
}
