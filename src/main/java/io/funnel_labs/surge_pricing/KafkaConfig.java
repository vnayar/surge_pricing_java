package io.funnel_labs.surge_pricing;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaConfig {
  @Bean
	public NewTopic driverStatusUpdateEventTopic() {
		return new NewTopic("DriverStatusUpdateEvents", 1, (short) 1);
	}
}
