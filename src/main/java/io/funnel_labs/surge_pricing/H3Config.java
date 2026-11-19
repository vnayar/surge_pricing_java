package io.funnel_labs.surge_pricing;

import com.uber.h3core.H3Core;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class H3Config {
  @Bean
  public H3Core initH3Core() throws IOException {
    return H3Core.newInstance();
  }
}
