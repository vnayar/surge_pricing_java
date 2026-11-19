package io.funnel_labs.surge_pricing;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan  // Allow auto-loading of @ConfigurationProperties classes.
@EnableScheduling  // Allow execution of @Schedued functions in beans.
public class SurgePricingApplication {

  public static void main(String[] args) {
    SpringApplication.run(SurgePricingApplication.class, args);
  }

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }

}
