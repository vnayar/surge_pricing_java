package io.funnel_labs.surge_pricing;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;

// A full integration test that loads all SpringBoot and Application beans.
@SpringBootTest
@EmbeddedKafka(
    controlledShutdown=true,
    brokerProperties={"log.dir=target/embedded-kafka-logs/${random.uuid}"})
@DirtiesContext // Wait for a clean shutdown of Embedded-Kafka.
class SurgePricingApplicationTests {

	@Test
	void contextLoads() {
	}

}
